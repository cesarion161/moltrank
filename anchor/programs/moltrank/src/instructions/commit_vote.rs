use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};
use crate::state::{Round, Pair, Commitment, Identity};
use crate::error::MoltRankError;

#[derive(Accounts)]
#[instruction(pair_id: u32, round_id: u64)]
pub struct CommitVote<'info> {
    #[account(
        init,
        payer = curator,
        space = Commitment::space_for(Commitment::MAX_ENCRYPTED_SIZE),
        seeds = [b"commitment", pair_id.to_le_bytes().as_ref(), curator.key().as_ref()],
        bump
    )]
    pub commitment: Account<'info, Commitment>,

    #[account(
        mut,
        seeds = [b"pair", round_id.to_le_bytes().as_ref(), pair_id.to_le_bytes().as_ref()],
        bump = pair.bump
    )]
    pub pair: Account<'info, Pair>,

    #[account(
        seeds = [b"round", round_id.to_le_bytes().as_ref()],
        bump = round.bump
    )]
    pub round: Account<'info, Round>,

    #[account(
        seeds = [b"identity", curator.key().as_ref()],
        bump = identity.bump
    )]
    pub identity: Account<'info, Identity>,

    /// Pair escrow token account (must be initialized beforehand)
    #[account(
        mut,
        seeds = [b"pair_escrow", pair_id.to_le_bytes().as_ref()],
        bump,
        token::mint = token_mint,
        token::authority = pair_escrow
    )]
    pub pair_escrow: Account<'info, TokenAccount>,

    /// Curator's token account
    #[account(mut)]
    pub curator_token_account: Account<'info, TokenAccount>,

    /// Token mint (SURGE token)
    pub token_mint: Account<'info, token::Mint>,

    #[account(mut)]
    pub curator: Signer<'info>,

    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
    pub rent: Sysvar<'info, Rent>,
}

pub fn handler(
    ctx: Context<CommitVote>,
    pair_id: u32,
    round_id: u64,
    commitment_hash: [u8; 32],
    encrypted_reveal: Vec<u8>,
    stake_amount: u64,
) -> Result<()> {
    let commitment = &mut ctx.accounts.commitment;
    let pair = &mut ctx.accounts.pair;
    let round = &ctx.accounts.round;
    let clock = Clock::get()?;

    // Validation: Check we're in commit phase
    require!(
        clock.unix_timestamp <= round.commit_deadline,
        MoltRankError::RevealNotYetAllowed
    );

    // Validation: Check stake doesn't exceed maximum
    require!(
        stake_amount <= Commitment::MAX_STAKE_PER_PAIR,
        MoltRankError::StakeExceedsMaximum
    );

    // Validation: Check encrypted payload size
    require!(
        encrypted_reveal.len() <= Commitment::MAX_ENCRYPTED_SIZE,
        MoltRankError::EncryptedPayloadTooLarge
    );

    // Store commitment
    commitment.commitment_hash = commitment_hash;
    commitment.encrypted_reveal = encrypted_reveal;
    commitment.curator_wallet = ctx.accounts.curator.key();
    commitment.pair_id = pair_id;
    commitment.round_id = round_id;
    commitment.stake_amount = stake_amount;
    commitment.timestamp = clock.unix_timestamp;
    commitment.revealed = false;
    commitment.bump = ctx.bumps.commitment;

    // Transfer stake to pair escrow
    let cpi_accounts = Transfer {
        from: ctx.accounts.curator_token_account.to_account_info(),
        to: ctx.accounts.pair_escrow.to_account_info(),
        authority: ctx.accounts.curator.to_account_info(),
    };
    let cpi_program = ctx.accounts.token_program.to_account_info();
    let cpi_ctx = CpiContext::new(cpi_program, cpi_accounts);
    token::transfer(cpi_ctx, stake_amount)?;

    // Update pair state
    pair.escrow_balance = pair
        .escrow_balance
        .checked_add(stake_amount)
        .ok_or(ProgramError::ArithmeticOverflow)?;
    pair.votes_count = pair
        .votes_count
        .checked_add(1)
        .ok_or(ProgramError::ArithmeticOverflow)?;

    msg!(
        "Vote committed: pair={}, curator={}, stake={}",
        pair_id,
        ctx.accounts.curator.key(),
        stake_amount
    );

    Ok(())
}
