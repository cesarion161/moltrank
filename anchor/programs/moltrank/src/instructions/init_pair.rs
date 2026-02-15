use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount};
use crate::state::{Round, Pair};

#[derive(Accounts)]
#[instruction(round_id: u64, pair_id: u32)]
pub struct InitPair<'info> {
    #[account(
        init,
        payer = authority,
        space = Pair::LEN,
        seeds = [b"pair", round_id.to_le_bytes().as_ref(), pair_id.to_le_bytes().as_ref()],
        bump
    )]
    pub pair: Account<'info, Pair>,

    #[account(
        seeds = [b"round", round_id.to_le_bytes().as_ref()],
        bump = round.bump
    )]
    pub round: Account<'info, Round>,

    /// Pair escrow token account
    #[account(
        init,
        payer = authority,
        seeds = [b"pair_escrow", pair_id.to_le_bytes().as_ref()],
        bump,
        token::mint = token_mint,
        token::authority = pair_escrow
    )]
    pub pair_escrow: Account<'info, TokenAccount>,

    /// Token mint (SURGE token)
    pub token_mint: Account<'info, token::Mint>,

    #[account(mut)]
    pub authority: Signer<'info>,

    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
    pub rent: Sysvar<'info, Rent>,
}

pub fn handler(
    ctx: Context<InitPair>,
    round_id: u64,
    pair_id: u32,
    is_golden: bool,
    is_audit: bool,
    golden_answer: Option<u8>,
) -> Result<()> {
    let pair = &mut ctx.accounts.pair;

    // Initialize pair state
    pair.pair_id = pair_id;
    pair.round_id = round_id;
    pair.is_golden = is_golden;
    pair.is_audit = is_audit;
    pair.escrow_balance = 0;
    pair.votes_count = 0;
    pair.golden_answer = golden_answer;
    pair.settled = false;
    pair.bump = ctx.bumps.pair;

    msg!(
        "Pair {} created for round {}: golden={}, audit={}",
        pair_id,
        round_id,
        is_golden,
        is_audit
    );

    Ok(())
}
