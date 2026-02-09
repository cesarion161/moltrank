use anchor_lang::prelude::*;
use crate::state::GlobalPool;

#[derive(Accounts)]
pub struct InitGlobalPool<'info> {
    #[account(
        init,
        payer = authority,
        space = GlobalPool::LEN,
        seeds = [b"global_pool"],
        bump
    )]
    pub global_pool: Account<'info, GlobalPool>,

    #[account(mut)]
    pub authority: Signer<'info>,

    pub system_program: Program<'info, System>,
}

pub fn handler(ctx: Context<InitGlobalPool>) -> Result<()> {
    let global_pool = &mut ctx.accounts.global_pool;

    global_pool.balance = 0;
    global_pool.alpha = GlobalPool::DEFAULT_ALPHA;
    global_pool.round_id = 0;
    global_pool.settlement_hash = [0u8; 32];
    global_pool.bump = ctx.bumps.global_pool;

    msg!("Global pool initialized with alpha: {}", global_pool.alpha);

    Ok(())
}
