use anchor_lang::prelude::*;
use anchor_lang::solana_program::keccak;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};
use crate::state::{GlobalPool, Round, Pair, Commitment, Identity};
use crate::error::MoltRankError;

/// Event emitted when a pair is settled
#[event]
pub struct SettlementEvent {
    pub pair_id: u32,
    pub round_id: u64,
    pub majority_side: u8,
    pub total_majority_stake: u64,
    pub total_minority_stake: u64,
    pub total_forfeited: u64,
    pub total_rewards_distributed: u64,
    pub settlement_hash: [u8; 32],
}

#[derive(Accounts)]
#[instruction(pair_id: u32, round_id: u64)]
pub struct SettlePair<'info> {
    #[account(
        mut,
        seeds = [b"global_pool"],
        bump = global_pool.bump
    )]
    pub global_pool: Account<'info, GlobalPool>,

    #[account(
        seeds = [b"round", round_id.to_le_bytes().as_ref()],
        bump = round.bump
    )]
    pub round: Account<'info, Round>,

    #[account(
        mut,
        seeds = [b"pair", round_id.to_le_bytes().as_ref(), pair_id.to_le_bytes().as_ref()],
        bump = pair.bump,
        constraint = !pair.settled @ MoltRankError::PairAlreadySettled
    )]
    pub pair: Account<'info, Pair>,

    /// Pair escrow token account (holds staked tokens)
    #[account(
        mut,
        seeds = [b"pair_escrow", pair_id.to_le_bytes().as_ref()],
        bump,
        token::mint = token_mint,
        token::authority = pair_escrow
    )]
    pub pair_escrow: Account<'info, TokenAccount>,

    /// Round escrow token account (holds reward pool tokens)
    #[account(
        mut,
        seeds = [b"round_escrow", round_id.to_le_bytes().as_ref()],
        bump,
        token::mint = token_mint,
        token::authority = round_escrow
    )]
    pub round_escrow: Account<'info, TokenAccount>,

    /// Global pool token account (receives forfeited stakes)
    #[account(
        mut,
        token::mint = token_mint
    )]
    pub global_pool_token_account: Account<'info, TokenAccount>,

    /// Token mint (SURGE token)
    pub token_mint: Account<'info, token::Mint>,

    #[account(mut)]
    pub authority: Signer<'info>,

    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

/// Parsed curator data from commitment accounts
struct CuratorInfo {
    curator_wallet: Pubkey,
    stake: u64,
    vote: Option<u8>,
    revealed: bool,
}

pub fn handler<'info>(
    ctx: Context<'_, '_, 'info, 'info, SettlePair<'info>>,
    _pair_id: u32,
    _round_id: u64,
    total_pairs: u32,
) -> Result<()> {
    let pair = &mut ctx.accounts.pair;
    let round = &ctx.accounts.round;
    let global_pool = &mut ctx.accounts.global_pool;
    let clock = Clock::get()?;

    // Must be past reveal deadline
    require!(
        clock.unix_timestamp > round.reveal_deadline,
        MoltRankError::CannotSettleDuringVoting
    );

    require!(total_pairs > 0, MoltRankError::SettlementArithmeticOverflow);

    // Parse remaining_accounts
    // Layout for non-golden pairs: [commitment_0, curator_token_0, commitment_1, curator_token_1, ...]
    // Layout for golden pairs: [commitment_0, curator_token_0, ..., identity_0, identity_1, ...]
    let remaining = ctx.remaining_accounts;
    let is_golden = pair.is_golden;

    let n_curators = if is_golden {
        require!(
            remaining.len() % 3 == 0,
            MoltRankError::InvalidRemainingAccounts
        );
        remaining.len() / 3
    } else {
        require!(
            remaining.len() % 2 == 0,
            MoltRankError::InvalidRemainingAccounts
        );
        remaining.len() / 2
    };

    // Deserialize commitment accounts to extract curator data
    let mut curators: Vec<CuratorInfo> = Vec::with_capacity(n_curators);
    for i in 0..n_curators {
        let commitment_info = &remaining[i * 2];
        let commitment: Account<'info, Commitment> = Account::try_from(commitment_info)?;

        curators.push(CuratorInfo {
            curator_wallet: commitment.curator_wallet,
            stake: commitment.stake_amount,
            vote: commitment.vote,
            revealed: commitment.revealed,
        });
    }

    // Count votes by side
    let mut total_vote_0: u64 = 0;
    let mut total_vote_1: u64 = 0;

    for curator in curators.iter() {
        if curator.revealed {
            match curator.vote {
                Some(0) => total_vote_0 = total_vote_0.checked_add(curator.stake)
                    .ok_or(MoltRankError::SettlementArithmeticOverflow)?,
                Some(1) => total_vote_1 = total_vote_1.checked_add(curator.stake)
                    .ok_or(MoltRankError::SettlementArithmeticOverflow)?,
                _ => {}
            }
        }
    }

    // Determine majority side (tie goes to side 1)
    let majority_side: u8 = if total_vote_0 > total_vote_1 { 0 } else { 1 };

    // Categorize each curator
    let mut majority_indices: Vec<usize> = Vec::new();
    let mut minority_indices: Vec<usize> = Vec::new();
    let mut non_revealed_indices: Vec<usize> = Vec::new();
    let mut golden_wrong_indices: Vec<usize> = Vec::new();

    for (i, curator) in curators.iter().enumerate() {
        if !curator.revealed {
            non_revealed_indices.push(i);
            continue;
        }

        if let Some(vote) = curator.vote {
            // Golden pair: wrong answer gets special treatment
            if is_golden {
                if let Some(golden_answer) = pair.golden_answer {
                    if vote != golden_answer {
                        golden_wrong_indices.push(i);
                        continue;
                    }
                }
            }

            if vote == majority_side {
                majority_indices.push(i);
            } else {
                minority_indices.push(i);
            }
        }
    }

    // Calculate per-pair reward pool from round funding
    // Per-pair base = round.base_pool_amount / total_pairs
    // Per-pair premium = round.premium_pool_amount / total_pairs
    let per_pair_base = round.base_pool_amount
        .checked_div(total_pairs as u64)
        .unwrap_or(0);
    let per_pair_premium = round.premium_pool_amount
        .checked_div(total_pairs as u64)
        .unwrap_or(0);
    let total_reward_pool = per_pair_base
        .checked_add(per_pair_premium)
        .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

    // Calculate quadratic weights for majority curators: weight = isqrt(stake)
    let total_majority_weight: u128 = majority_indices.iter()
        .map(|&i| isqrt(curators[i].stake as u128))
        .sum();

    // PDA signer seeds for pair escrow and round escrow
    let pair_id_bytes = pair.pair_id.to_le_bytes();
    let pair_escrow_bump = [ctx.bumps.pair_escrow];
    let pair_escrow_seeds: &[&[u8]] = &[
        b"pair_escrow",
        pair_id_bytes.as_ref(),
        &pair_escrow_bump,
    ];
    let pair_signer = [pair_escrow_seeds];

    let round_id_bytes = round.round_id.to_le_bytes();
    let round_escrow_bump = [ctx.bumps.round_escrow];
    let round_escrow_seeds: &[&[u8]] = &[
        b"round_escrow",
        round_id_bytes.as_ref(),
        &round_escrow_bump,
    ];
    let round_signer = [round_escrow_seeds];

    let mut total_forfeited: u64 = 0;
    let mut total_rewards_distributed: u64 = 0;

    // --- Majority curators: 100% stake back + quadratic-weighted reward ---
    for &idx in majority_indices.iter() {
        let curator = &curators[idx];
        let curator_token_info = &remaining[idx * 2 + 1];

        // Calculate quadratic-weighted reward share
        let curator_weight = isqrt(curator.stake as u128);
        let reward: u64 = if total_majority_weight > 0 {
            (total_reward_pool as u128)
                .checked_mul(curator_weight)
                .ok_or(MoltRankError::SettlementArithmeticOverflow)?
                .checked_div(total_majority_weight)
                .ok_or(MoltRankError::SettlementArithmeticOverflow)?
                as u64
        } else {
            0
        };

        // Transfer full stake back from pair escrow
        if curator.stake > 0 {
            let cpi_accounts = Transfer {
                from: ctx.accounts.pair_escrow.to_account_info(),
                to: curator_token_info.clone(),
                authority: ctx.accounts.pair_escrow.to_account_info(),
            };
            let cpi_ctx = CpiContext::new_with_signer(
                ctx.accounts.token_program.to_account_info(),
                cpi_accounts,
                &pair_signer,
            );
            token::transfer(cpi_ctx, curator.stake)?;
        }

        // Transfer reward from round escrow
        if reward > 0 {
            let cpi_accounts = Transfer {
                from: ctx.accounts.round_escrow.to_account_info(),
                to: curator_token_info.clone(),
                authority: ctx.accounts.round_escrow.to_account_info(),
            };
            let cpi_ctx = CpiContext::new_with_signer(
                ctx.accounts.token_program.to_account_info(),
                cpi_accounts,
                &round_signer,
            );
            token::transfer(cpi_ctx, reward)?;
            total_rewards_distributed = total_rewards_distributed
                .checked_add(reward)
                .ok_or(MoltRankError::SettlementArithmeticOverflow)?;
        }

        msg!("Majority curator {}: stake={}, reward={}", curator.curator_wallet, curator.stake, reward);
    }

    // --- Minority curators: 80% stake back, 20% forfeited to GlobalPool ---
    for &idx in minority_indices.iter() {
        let curator = &curators[idx];
        let curator_token_info = &remaining[idx * 2 + 1];

        let payout = curator.stake
            .checked_mul(80)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?
            .checked_div(100)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;
        let forfeited = curator.stake
            .checked_sub(payout)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

        if payout > 0 {
            let cpi_accounts = Transfer {
                from: ctx.accounts.pair_escrow.to_account_info(),
                to: curator_token_info.clone(),
                authority: ctx.accounts.pair_escrow.to_account_info(),
            };
            let cpi_ctx = CpiContext::new_with_signer(
                ctx.accounts.token_program.to_account_info(),
                cpi_accounts,
                &pair_signer,
            );
            token::transfer(cpi_ctx, payout)?;
        }

        total_forfeited = total_forfeited
            .checked_add(forfeited)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

        msg!("Minority curator {}: payout={}, forfeited={}", curator.curator_wallet, payout, forfeited);
    }

    // --- Non-revealed curators: 0% stake back, 100% forfeited ---
    for &idx in non_revealed_indices.iter() {
        let curator = &curators[idx];
        total_forfeited = total_forfeited
            .checked_add(curator.stake)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

        msg!("Non-revealed curator {}: forfeited={}", curator.curator_wallet, curator.stake);
    }

    // --- Golden pair wrong answers: 80% stake back, 20% forfeited, decrease alignment ---
    if is_golden {
        for &idx in golden_wrong_indices.iter() {
            let curator = &curators[idx];
            let curator_token_info = &remaining[idx * 2 + 1];

            let payout = curator.stake
                .checked_mul(80)
                .ok_or(MoltRankError::SettlementArithmeticOverflow)?
                .checked_div(100)
                .ok_or(MoltRankError::SettlementArithmeticOverflow)?;
            let forfeited = curator.stake
                .checked_sub(payout)
                .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

            if payout > 0 {
                let cpi_accounts = Transfer {
                    from: ctx.accounts.pair_escrow.to_account_info(),
                    to: curator_token_info.clone(),
                    authority: ctx.accounts.pair_escrow.to_account_info(),
                };
                let cpi_ctx = CpiContext::new_with_signer(
                    ctx.accounts.token_program.to_account_info(),
                    cpi_accounts,
                    &pair_signer,
                );
                token::transfer(cpi_ctx, payout)?;
            }

            total_forfeited = total_forfeited
                .checked_add(forfeited)
                .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

            // Decrease alignment score via Identity account
            let identity_info = &remaining[n_curators * 2 + idx];
            let mut identity: Account<'info, Identity> = Account::try_from(identity_info)?;
            identity.alignment_score = identity.alignment_score.saturating_sub(1);
            identity.exit(ctx.program_id)?;

            msg!(
                "Golden wrong curator {}: payout={}, forfeited={}, alignment_decreased",
                curator.curator_wallet, payout, forfeited
            );
        }
    }

    // --- Audit pair: flag for off-chain review ---
    // Audit pairs detect voting inconsistencies (e.g. transitivity violations)
    // which require cross-pair analysis done off-chain. On-chain, we flag the pair
    // and emit the settlement event for the backend to analyze.
    if pair.is_audit {
        msg!("Audit pair {} flagged for review", pair.pair_id);
    }

    // --- Transfer all forfeited stakes to global pool ---
    if total_forfeited > 0 {
        let cpi_accounts = Transfer {
            from: ctx.accounts.pair_escrow.to_account_info(),
            to: ctx.accounts.global_pool_token_account.to_account_info(),
            authority: ctx.accounts.pair_escrow.to_account_info(),
        };
        let cpi_ctx = CpiContext::new_with_signer(
            ctx.accounts.token_program.to_account_info(),
            cpi_accounts,
            &pair_signer,
        );
        token::transfer(cpi_ctx, total_forfeited)?;

        global_pool.balance = global_pool.balance
            .checked_add(total_forfeited)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;
    }

    // --- Compute settlement hash over all inputs + outputs ---
    let mut settlement_data = Vec::new();
    settlement_data.extend_from_slice(&pair.pair_id.to_le_bytes());
    settlement_data.extend_from_slice(&round.round_id.to_le_bytes());
    settlement_data.extend_from_slice(&majority_side.to_le_bytes());
    settlement_data.extend_from_slice(&total_forfeited.to_le_bytes());
    settlement_data.extend_from_slice(&total_rewards_distributed.to_le_bytes());
    settlement_data.extend_from_slice(&clock.unix_timestamp.to_le_bytes());
    for curator in curators.iter() {
        settlement_data.extend_from_slice(&curator.curator_wallet.to_bytes());
        settlement_data.extend_from_slice(&curator.stake.to_le_bytes());
        settlement_data.push(if curator.revealed {
            curator.vote.unwrap_or(255)
        } else {
            255
        });
    }
    let settlement_hash = keccak::hash(&settlement_data);
    global_pool.settlement_hash = settlement_hash.to_bytes();

    // Mark pair as settled
    pair.settled = true;

    // Emit settlement event
    emit!(SettlementEvent {
        pair_id: pair.pair_id,
        round_id: round.round_id,
        majority_side,
        total_majority_stake: if majority_side == 0 { total_vote_0 } else { total_vote_1 },
        total_minority_stake: if majority_side == 0 { total_vote_1 } else { total_vote_0 },
        total_forfeited,
        total_rewards_distributed,
        settlement_hash: settlement_hash.to_bytes(),
    });

    msg!(
        "Pair {} settled: majority_side={}, forfeited={}, rewards={}",
        pair.pair_id,
        majority_side,
        total_forfeited,
        total_rewards_distributed
    );

    Ok(())
}

/// Integer square root via Newton's method (for quadratic weighting)
fn isqrt(n: u128) -> u128 {
    if n == 0 {
        return 0;
    }
    let mut x = n;
    let mut y = (x + 1) / 2;
    while y < x {
        x = y;
        y = (x + n / x) / 2;
    }
    x
}
