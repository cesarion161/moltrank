use anchor_lang::prelude::*;

#[error_code]
pub enum MoltRankError {
    #[msg("Market name exceeds maximum length of 50 bytes")]
    NameTooLong,

    #[msg("Creation bond amount must be greater than zero")]
    InvalidCreationBond,

    #[msg("Alpha parameter must be between 1 and 100 (0.01 to 1.00)")]
    InvalidAlpha,

    #[msg("Global pool already initialized")]
    GlobalPoolAlreadyInitialized,

    #[msg("Market ID must be unique")]
    MarketIdNotUnique,

    #[msg("Identity ID cannot be empty")]
    InvalidIdentityId,

    #[msg("Subscription amount must be greater than zero for realtime subscriptions")]
    InvalidSubscriptionAmount,

    #[msg("Identity already registered for this wallet")]
    IdentityAlreadyRegistered,

    #[msg("Subscription already exists for this market")]
    SubscriptionAlreadyExists,

    #[msg("Round already exists")]
    RoundAlreadyExists,

    #[msg("Pair already exists")]
    PairAlreadyExists,

    #[msg("Stake amount exceeds maximum allowed per pair (500 tokens)")]
    StakeExceedsMaximum,

    #[msg("Encrypted reveal payload exceeds maximum size")]
    EncryptedPayloadTooLarge,

    #[msg("Commitment already exists for this pair and curator")]
    CommitmentAlreadyExists,

    #[msg("Commitment not found or invalid")]
    InvalidCommitment,

    #[msg("Reveal hash does not match commitment")]
    HashMismatch,

    #[msg("Outside reveal window")]
    OutsideRevealWindow,

    #[msg("Commitment already revealed")]
    AlreadyRevealed,

    #[msg("Manual reveal grace period has expired")]
    GracePeriodExpired,

    #[msg("Cannot reveal during commit phase")]
    RevealNotYetAllowed,
}
