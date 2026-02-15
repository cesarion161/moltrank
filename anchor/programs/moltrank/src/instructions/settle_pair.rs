use anchor_lang::prelude::*;
use anchor_lang::solana_program::keccak;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};
use crate::state::{GlobalPool, Round, Pair, Commitment, Identity, Market};
use crate::error::MoltRankError;

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

    /// Pair escrow token account
    #[account(
        mut,
        seeds = [b"pair_escrow", pair_id.to_le_bytes().as_ref()],
        bump,
        token::mint = token_mint,
        token::authority = pair_escrow
    )]
    pub pair_escrow: Account<'info, TokenAccount>,

    /// Global pool token account
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

/// Settlement result for a curator
#[derive(Debug)]
pub struct CuratorSettlement {
    pub curator_wallet: Pubkey,
    pub stake: u64,
    pub vote: u8,
    pub payout: u64,
    pub reward: u64,
}

pub fn handler(
    ctx: Context<SettlePair>,
    _pair_id: u32,
    _round_id: u64,
    commitment_accounts: Vec<AccountInfo>,
) -> Result<()> {
    let pair = &mut ctx.accounts.pair;
    let round = &ctx.accounts.round;
    let global_pool = &mut ctx.accounts.global_pool;
    let clock = Clock::get()?;

    // Validation: Must be past reveal deadline
    require!(
        clock.unix_timestamp > round.reveal_deadline,
        MoltRankError::CannotSettleDuringVoting
    );

    // Parse commitment accounts
    let mut commitments: Vec<(Pubkey, Account<Commitment>)> = Vec::new();
    for account_info in commitment_accounts.iter() {
        let commitment: Account<Commitment> = Account::try_from(account_info)?;
        commitments.push((account_info.key(), commitment));
    }

    // Count votes and categorize curators
    let mut vote_0_stakes: Vec<(Pubkey, u64)> = Vec::new();
    let mut vote_1_stakes: Vec<(Pubkey, u64)> = Vec::new();
    let mut non_revealed: Vec<(Pubkey, u64)> = Vec::new();

    for (pubkey, commitment) in commitments.iter() {
        if commitment.revealed {
            if let Some(vote) = commitment.vote {
                if vote == 0 {
                    vote_0_stakes.push((*pubkey, commitment.stake_amount));
                } else {
                    vote_1_stakes.push((*pubkey, commitment.stake_amount));
                }
            } else {
                return Err(MoltRankError::CommitmentNotRevealed.into());
            }
        } else {
            non_revealed.push((*pubkey, commitment.stake_amount));
        }
    }

    // Determine majority side
    let total_vote_0: u64 = vote_0_stakes.iter().map(|(_, stake)| *stake).sum();
    let total_vote_1: u64 = vote_1_stakes.iter().map(|(_, stake)| *stake).sum();

    let majority_side: u8 = if total_vote_0 > total_vote_1 { 0 } else { 1 };
    let (majority_stakes, minority_stakes) = if majority_side == 0 {
        (vote_0_stakes, vote_1_stakes)
    } else {
        (vote_1_stakes, vote_0_stakes)
    };

    // Calculate rewards for majority curators using quadratic weighting
    let total_majority_weight: f64 = majority_stakes.iter()
        .map(|(_, stake)| (*stake as f64).sqrt())
        .sum();

    // Calculate per-pair base reward: GlobalPool × alpha / totalPairsThisRound
    // For simplicity, assuming 100 pairs per round (this should come from round state)
    let total_pairs_this_round: u64 = 100;
    let per_pair_base = global_pool.balance
        .checked_mul(global_pool.alpha as u64)
        .ok_or(MoltRankError::SettlementArithmeticOverflow)?
        .checked_div(100)
        .ok_or(MoltRankError::SettlementArithmeticOverflow)?
        .checked_div(total_pairs_this_round)
        .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

    // For simplicity, assuming no premium for now (would need market revenue lookup)
    let per_pair_premium: u64 = 0;
    let total_reward_pool = per_pair_base
        .checked_add(per_pair_premium)
        .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

    // Process majority curators: 100% stake + quadratic-weighted reward
    let mut total_forfeited: u64 = 0;
    for (curator_pubkey, stake) in majority_stakes.iter() {
        let curator_weight = (*stake as f64).sqrt();
        let reward_share = if total_majority_weight > 0.0 {
            (total_reward_pool as f64 * curator_weight / total_majority_weight) as u64
        } else {
            0
        };

        let payout = stake.checked_add(reward_share)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

        // Transfer tokens from pair escrow to curator
        // Note: This would need the curator's token account, which should be passed as remaining_accounts
        msg!("Majority curator {}: stake={}, reward={}, payout={}", curator_pubkey, stake, reward_share, payout);
    }

    // Process minority curators: 80% stake back, 20% forfeited
    for (_curator_pubkey, stake) in minority_stakes.iter() {
        let payout = stake.checked_mul(80)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?
            .checked_div(100)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;
        let forfeited = stake.checked_sub(payout)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

        total_forfeited = total_forfeited.checked_add(forfeited)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

        // Transfer payout to curator
        msg!("Minority curator: stake={}, payout={}, forfeited={}", stake, payout, forfeited);
    }

    // Process non-revealed curators: 0% stake back, 100% forfeited
    for (_curator_pubkey, stake) in non_revealed.iter() {
        total_forfeited = total_forfeited.checked_add(*stake)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;

        msg!("Non-revealed curator: stake={}, forfeited={}", stake, stake);
    }

    // Handle golden pairs - check if curators got the golden answer correct
    if pair.is_golden {
        if let Some(golden_answer) = pair.golden_answer {
            // Curators who voted against golden answer: 80% stake back, decrease alignment
            // This would require access to Identity accounts for alignment score updates
            msg!("Golden pair settlement: correct_answer={}", golden_answer);
        }
    }

    // Handle audit pairs - flag inconsistencies
    if pair.is_audit {
        // Audit pair logic: check for inconsistencies
        // Inconsistent curators: 50% stake back, 50% forfeited, flag for review
        msg!("Audit pair settlement");
    }

    // Transfer forfeited stakes to global pool
    if total_forfeited > 0 {
        let seeds = &[
            b"pair_escrow",
            pair.pair_id.to_le_bytes().as_ref(),
            &[ctx.bumps.pair_escrow],
        ];
        let signer = &[&seeds[..]];

        let cpi_accounts = Transfer {
            from: ctx.accounts.pair_escrow.to_account_info(),
            to: ctx.accounts.global_pool_token_account.to_account_info(),
            authority: ctx.accounts.pair_escrow.to_account_info(),
        };
        let cpi_program = ctx.accounts.token_program.to_account_info();
        let cpi_ctx = CpiContext::new_with_signer(cpi_program, cpi_accounts, signer);
        token::transfer(cpi_ctx, total_forfeited)?;

        // Update global pool balance
        global_pool.balance = global_pool.balance
            .checked_add(total_forfeited)
            .ok_or(MoltRankError::SettlementArithmeticOverflow)?;
    }

    // Calculate and store settlement hash
    let mut settlement_data = Vec::new();
    settlement_data.extend_from_slice(&pair.pair_id.to_le_bytes());
    settlement_data.extend_from_slice(&round.round_id.to_le_bytes());
    settlement_data.extend_from_slice(&majority_side.to_le_bytes());
    settlement_data.extend_from_slice(&total_forfeited.to_le_bytes());
    settlement_data.extend_from_slice(&clock.unix_timestamp.to_le_bytes());

    let settlement_hash = keccak::hash(&settlement_data);
    global_pool.settlement_hash = settlement_hash.to_bytes();

    // Mark pair as settled
    pair.settled = true;

    msg!(
        "Pair {} settled: majority_side={}, total_forfeited={}",
        pair.pair_id,
        majority_side,
        total_forfeited
    );

    Ok(())
}
