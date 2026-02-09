use anchor_lang::prelude::*;
use anchor_lang::system_program::{transfer, Transfer};
use crate::error::MoltRankError;
use crate::state::{GlobalPool, Market};

#[derive(Accounts)]
#[instruction(market_id: [u8; 32])]
pub struct CreateMarket<'info> {
    #[account(
        init,
        payer = creator,
        space = Market::LEN,
        seeds = [b"market", market_id.as_ref()],
        bump
    )]
    pub market: Account<'info, Market>,

    #[account(
        mut,
        seeds = [b"global_pool"],
        bump = global_pool.bump
    )]
    pub global_pool: Account<'info, GlobalPool>,

    #[account(mut)]
    pub creator: Signer<'info>,

    pub system_program: Program<'info, System>,
}

pub fn handler(
    ctx: Context<CreateMarket>,
    market_id: [u8; 32],
    name: String,
    submolt_id: u32,
    creation_bond: u64,
) -> Result<()> {
    // Validate inputs
    require!(
        name.len() <= Market::MAX_NAME_LEN,
        MoltRankError::NameTooLong
    );
    require!(
        creation_bond > 0,
        MoltRankError::InvalidCreationBond
    );

    // Transfer creation bond from creator to global pool for safekeeping
    let cpi_context = CpiContext::new(
        ctx.accounts.system_program.to_account_info(),
        Transfer {
            from: ctx.accounts.creator.to_account_info(),
            to: ctx.accounts.global_pool.to_account_info(),
        },
    );
    transfer(cpi_context, creation_bond)?;

    // Initialize market account
    let market = &mut ctx.accounts.market;
    market.market_id = market_id;
    market.name = name;
    market.submolt_id = submolt_id;
    market.subscription_revenue = 0;
    market.subscribers = 0;
    market.creation_bond = creation_bond;
    market.max_pairs = 0; // Activates on first subscriber
    market.bump = ctx.bumps.market;

    msg!(
        "Market created: {} (submolt_id: {}, bond: {})",
        market.name,
        market.submolt_id,
        market.creation_bond
    );

    Ok(())
}
