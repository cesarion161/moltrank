use anchor_lang::prelude::*;

pub mod error;
pub mod instructions;
pub mod state;

use instructions::*;

declare_id!("Fg6PaFpoGXkYsidMpWTK6W2BeZ7FEfcYkg476zPFsLnS");

#[program]
pub mod moltrank {
    use super::*;

    /// Initialize the global pool with initial alpha parameter
    /// Alpha defaults to 0.30 for the ELO ranking system
    pub fn init_global_pool(ctx: Context<InitGlobalPool>) -> Result<()> {
        instructions::init_global_pool::handler(ctx)
    }

    /// Create a new market with creation bond requirement
    /// Markets activate (max_pairs > 0) after first subscriber
    pub fn create_market(
        ctx: Context<CreateMarket>,
        market_id: [u8; 32],
        name: String,
        submolt_id: u32,
        creation_bond: u64,
    ) -> Result<()> {
        instructions::create_market::handler(ctx, market_id, name, submolt_id, creation_bond)
    }
}
