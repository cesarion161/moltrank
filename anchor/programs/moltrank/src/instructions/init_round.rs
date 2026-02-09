use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};
use crate::state::{GlobalPool, Round};

#[derive(Accounts)]
#[instruction(round_id: u64)]
pub struct InitRound<'info> {
    #[account(
        init,
        payer = authority,
        space = Round::LEN,
        seeds = [b"round", round_id.to_le_bytes().as_ref()],
        bump
    )]
    pub round: Account<'info, Round>,

    #[account(
        mut,
        seeds = [b"global_pool"],
        bump = global_pool.bump
    )]
    pub global_pool: Account<'info, GlobalPool>,

    /// Round escrow token account
    #[account(
        init,
        payer = authority,
        seeds = [b"round_escrow", round_id.to_le_bytes().as_ref()],
        bump,
        token::mint = token_mint,
        token::authority = round_escrow
    )]
    pub round_escrow: Account<'info, TokenAccount>,

    /// Source token account for funding
    #[account(mut)]
    pub source_token_account: Account<'info, TokenAccount>,

    /// Token mint (SURGE token)
    pub token_mint: Account<'info, token::Mint>,

    #[account(mut)]
    pub authority: Signer<'info>,

    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
    pub rent: Sysvar<'info, Rent>,
}

pub fn handler(
    ctx: Context<InitRound>,
    round_id: u64,
    content_merkle_root: [u8; 32],
    base_pool_amount: u64,
    premium_pool_amount: u64,
    commit_duration: i64,
    reveal_duration: i64,
) -> Result<()> {
    let round = &mut ctx.accounts.round;
    let clock = Clock::get()?;

    // Initialize round state
    round.round_id = round_id;
    round.content_merkle_root = content_merkle_root;
    round.base_pool_amount = base_pool_amount;
    round.premium_pool_amount = premium_pool_amount;
    round.start_time = clock.unix_timestamp;
    round.commit_deadline = clock.unix_timestamp + commit_duration;
    round.reveal_deadline = round.commit_deadline + reveal_duration;
    round.bump = ctx.bumps.round;

    // Transfer base + premium to round escrow
    let total_amount = base_pool_amount
        .checked_add(premium_pool_amount)
        .ok_or(ProgramError::ArithmeticOverflow)?;

    let cpi_accounts = Transfer {
        from: ctx.accounts.source_token_account.to_account_info(),
        to: ctx.accounts.round_escrow.to_account_info(),
        authority: ctx.accounts.authority.to_account_info(),
    };
    let cpi_program = ctx.accounts.token_program.to_account_info();
    let cpi_ctx = CpiContext::new(cpi_program, cpi_accounts);
    token::transfer(cpi_ctx, total_amount)?;

    msg!(
        "Round {} initialized: merkle_root={:?}, base={}, premium={}",
        round_id,
        content_merkle_root,
        base_pool_amount,
        premium_pool_amount
    );

    Ok(())
}
