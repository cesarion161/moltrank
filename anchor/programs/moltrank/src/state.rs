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

/// Round for pairwise curation voting
/// PDA seeds: [b"round", round_id (u64 as bytes)]
#[account]
pub struct Round {
    /// Unique round identifier
    pub round_id: u64,

    /// Merkle root of content in this round
    pub content_merkle_root: [u8; 32],

    /// Base pool amount for this round
    pub base_pool_amount: u64,

    /// Premium pool amount for this round
    pub premium_pool_amount: u64,

    /// Round start timestamp
    pub start_time: i64,

    /// Commit phase deadline
    pub commit_deadline: i64,

    /// Reveal phase deadline
    pub reveal_deadline: i64,

    /// Bump seed for PDA derivation
    pub bump: u8,
}

impl Round {
    pub const LEN: usize = 8 +  // discriminator
        8 +  // round_id
        32 + // content_merkle_root
        8 +  // base_pool_amount
        8 +  // premium_pool_amount
        8 +  // start_time
        8 +  // commit_deadline
        8 +  // reveal_deadline
        1;   // bump
}

/// Pair for curation voting
/// PDA seeds: [b"pair", round_id (u64 as bytes), pair_id (u32 as bytes)]
#[account]
pub struct Pair {
    /// Unique pair identifier within the round
    pub pair_id: u32,

    /// Round this pair belongs to
    pub round_id: u64,

    /// Whether this is a golden pair (quality control)
    pub is_golden: bool,

    /// Whether this is an audit pair
    pub is_audit: bool,

    /// Escrow balance for this pair
    pub escrow_balance: u64,

    /// Total number of votes
    pub votes_count: u32,

    /// Bump seed for PDA derivation
    pub bump: u8,
}

impl Pair {
    pub const LEN: usize = 8 +  // discriminator
        4 +  // pair_id
        8 +  // round_id
        1 +  // is_golden
        1 +  // is_audit
        8 +  // escrow_balance
        4 +  // votes_count
        1;   // bump
}

/// Commitment for commit-reveal voting
/// PDA seeds: [b"commitment", pair_id (u32 as bytes), curator_wallet]
#[account]
pub struct Commitment {
    /// Hash of the commitment (keccak256)
    pub commitment_hash: [u8; 32],

    /// Encrypted reveal payload
    pub encrypted_reveal: Vec<u8>,

    /// Curator's wallet
    pub curator_wallet: Pubkey,

    /// Pair ID this commitment is for
    pub pair_id: u32,

    /// Round ID
    pub round_id: u64,

    /// Stake amount locked in this commitment
    pub stake_amount: u64,

    /// Timestamp of commitment
    pub timestamp: i64,

    /// Whether commitment has been revealed
    pub revealed: bool,

    /// Bump seed for PDA derivation
    pub bump: u8,
}

impl Commitment {
    /// Maximum encrypted payload size (1KB)
    pub const MAX_ENCRYPTED_SIZE: usize = 1024;

    /// Calculate space needed for a commitment with encrypted data
    pub fn space_for(encrypted_len: usize) -> usize {
        8 +   // discriminator
        32 +  // commitment_hash
        4 + encrypted_len + // encrypted_reveal (Vec: 4 bytes length + data)
        32 +  // curator_wallet
        4 +   // pair_id
        8 +   // round_id
        8 +   // stake_amount
        8 +   // timestamp
        1 +   // revealed
        1     // bump
    }

    /// Maximum stake per identity per pair (500 tokens with 9 decimals)
    pub const MAX_STAKE_PER_PAIR: u64 = 500_000_000_000;
}
