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

    /// Initialize a new curation round with funding
    /// Stores content merkle root and funds the round escrow
    pub fn init_round(
        ctx: Context<InitRound>,
        round_id: u64,
        content_merkle_root: [u8; 32],
        base_pool_amount: u64,
        premium_pool_amount: u64,
        commit_duration: i64,
        reveal_duration: i64,
    ) -> Result<()> {
        instructions::init_round::handler(
            ctx,
            round_id,
            content_merkle_root,
            base_pool_amount,
            premium_pool_amount,
            commit_duration,
            reveal_duration,
        )
    }

    /// Create a new pair for curation voting
    /// Flags as golden or audit if applicable
    pub fn init_pair(
        ctx: Context<InitPair>,
        round_id: u64,
        pair_id: u32,
        is_golden: bool,
        is_audit: bool,
    ) -> Result<()> {
        instructions::init_pair::handler(ctx, round_id, pair_id, is_golden, is_audit)
    }

    /// Commit a vote with encrypted reveal
    /// Locks stake in pair escrow and stores commitment hash
    pub fn commit_vote(
        ctx: Context<CommitVote>,
        pair_id: u32,
        round_id: u64,
        commitment_hash: [u8; 32],
        encrypted_reveal: Vec<u8>,
        stake_amount: u64,
    ) -> Result<()> {
        instructions::commit_vote::handler(
            ctx,
            pair_id,
            round_id,
            commitment_hash,
            encrypted_reveal,
            stake_amount,
        )
    }

    /// Backend-triggered auto-reveal of commitment
    /// Verifies decrypted payload matches commitment hash
    pub fn auto_reveal(
        ctx: Context<AutoReveal>,
        pair_id: u32,
        decrypted_payload: Vec<u8>,
    ) -> Result<()> {
        instructions::auto_reveal::handler(ctx, pair_id, decrypted_payload)
    }

    /// Curator-triggered manual reveal (30-minute grace period)
    /// Fallback if backend fails to auto-reveal
    pub fn manual_reveal(
        ctx: Context<ManualReveal>,
        pair_id: u32,
        decrypted_payload: Vec<u8>,
    ) -> Result<()> {
        instructions::manual_reveal::handler(ctx, pair_id, decrypted_payload)
    }
}
