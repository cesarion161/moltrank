use anchor_lang::prelude::*;

/// Global pool managing the ranking system parameters
/// PDA seeds: [b"global_pool"]
#[account]
pub struct GlobalPool {
    /// Total balance in the global pool
    pub balance: u64,

    /// Alpha parameter for ELO calculations (stored as basis points, e.g., 30 = 0.30)
    pub alpha: u16,

    /// Current round ID for settlement tracking
    pub round_id: u64,

    /// Hash of the last settlement for verification
    pub settlement_hash: [u8; 32],

    /// Bump seed for PDA derivation
    pub bump: u8,
}

impl GlobalPool {
    pub const LEN: usize = 8 + // discriminator
        8 +  // balance
        2 +  // alpha
        8 +  // round_id
        32 + // settlement_hash
        1;   // bump

    /// Default alpha value: 0.30 (represented as 30 basis points)
    pub const DEFAULT_ALPHA: u16 = 30;
}

/// Market for content ranking
/// PDA seeds: [b"market", market_id]
#[account]
pub struct Market {
    /// Market identifier (32-byte unique ID)
    pub market_id: [u8; 32],

    /// Human-readable market name (max 50 bytes)
    pub name: String,

    /// Submolt ID this market belongs to
    pub submolt_id: u32,

    /// Total subscription revenue accumulated
    pub subscription_revenue: u64,

    /// Number of subscribers
    pub subscribers: u32,

    /// Creation bond (refundable after subscriber threshold)
    pub creation_bond: u64,

    /// Maximum pairs for ranking (0 until first subscriber)
    pub max_pairs: u32,

    /// Bump seed for PDA derivation
    pub bump: u8,
}

impl Market {
    pub const LEN: usize = 8 +   // discriminator
        32 +  // market_id
        4 + 50 + // name (String: 4 bytes length prefix + 50 bytes max)
        4 +   // submolt_id
        8 +   // subscription_revenue
        4 +   // subscribers
        8 +   // creation_bond
        4 +   // max_pairs
        1;    // bump

    /// Maximum length for market name in bytes
    pub const MAX_NAME_LEN: usize = 50;
}

/// Identity registry linking wallet to X account
/// PDA seeds: [b"identity", wallet_pubkey]
#[account]
pub struct Identity {
    /// Wallet public key
    pub wallet: Pubkey,

    /// Identity ID (hash of X account)
    pub identity_id: [u8; 32],

    /// Whether the identity is verified
    pub verified: bool,

    /// Creation timestamp
    pub created_at: i64,

    /// Bump seed for PDA derivation
    pub bump: u8,
}

impl Identity {
    pub const LEN: usize = 8 +  // discriminator
        32 + // wallet
        32 + // identity_id
        1 +  // verified
        8 +  // created_at
        1;   // bump
}

/// Subscription record for market access
/// PDA seeds: [b"subscription", reader_wallet, market_id]
#[account]
pub struct Subscription {
    /// Reader's wallet public key
    pub reader_wallet: Pubkey,

    /// Market ID this subscription is for
    pub market_id: [u8; 32],

    /// Subscription amount in tokens
    pub amount: u64,

    /// Type of subscription (realtime or free with delay)
    pub subscription_type: SubscriptionType,

    /// Bump seed for PDA derivation
    pub bump: u8,
}

impl Subscription {
    pub const LEN: usize = 8 +  // discriminator
        32 + // reader_wallet
        32 + // market_id
        8 +  // amount
        1 +  // subscription_type (enum is 1 byte)
        1;   // bump
}

/// Subscription type enum
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq, Debug)]
pub enum SubscriptionType {
    /// Real-time access with token payment
    Realtime,
    /// Free access with delay
    FreeDelay,
}
