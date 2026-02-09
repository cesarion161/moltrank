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

    /// Register identity linking wallet to X account
    /// Links wallet public key to identity_id (hash of X account)
    pub fn register_identity(
        ctx: Context<RegisterIdentity>,
        identity_id: [u8; 32],
    ) -> Result<()> {
        instructions::register_identity::handler(ctx, identity_id)
    }

    /// Subscribe to a market with token payment or free delay access
    /// Transfers tokens for realtime access and updates market statistics
    pub fn subscribe(
        ctx: Context<Subscribe>,
        market_id: [u8; 32],
        amount: u64,
        subscription_type: state::SubscriptionType,
    ) -> Result<()> {
        instructions::subscribe::handler(ctx, market_id, amount, subscription_type)
    }
}
