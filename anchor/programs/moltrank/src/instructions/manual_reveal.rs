use anchor_lang::prelude::*;
use anchor_lang::solana_program::keccak;
use crate::state::{Round, Commitment};
use crate::error::MoltRankError;

#[derive(Accounts)]
#[instruction(pair_id: u32)]
pub struct ManualReveal<'info> {
    #[account(
        mut,
        seeds = [b"commitment", pair_id.to_le_bytes().as_ref(), curator.key().as_ref()],
        bump = commitment.bump,
        constraint = commitment.curator_wallet == curator.key() @ MoltRankError::InvalidCommitment
    )]
    pub commitment: Account<'info, Commitment>,

    #[account(
        seeds = [b"round", commitment.round_id.to_le_bytes().as_ref()],
        bump = round.bump
    )]
    pub round: Account<'info, Round>,

    /// Curator who created the commitment
    pub curator: Signer<'info>,
}

pub fn handler(
    ctx: Context<ManualReveal>,
    _pair_id: u32,
    decrypted_payload: Vec<u8>,
) -> Result<()> {
    let commitment = &mut ctx.accounts.commitment;
    let round = &ctx.accounts.round;
    let clock = Clock::get()?;

    // Grace period: 30 minutes after reveal deadline
    const GRACE_PERIOD: i64 = 30 * 60; // 30 minutes in seconds

    // Validation: Must be in reveal window OR grace period
    require!(
        clock.unix_timestamp > round.commit_deadline,
        MoltRankError::RevealNotYetAllowed
    );
    require!(
        clock.unix_timestamp <= round.reveal_deadline + GRACE_PERIOD,
        MoltRankError::GracePeriodExpired
    );

    // Validation: Not already revealed
    require!(
        !commitment.revealed,
        MoltRankError::AlreadyRevealed
    );

    // Verify hash matches
    let computed_hash = keccak::hash(&decrypted_payload);
    require!(
        computed_hash.to_bytes() == commitment.commitment_hash,
        MoltRankError::HashMismatch
    );

    // Mark as revealed
    commitment.revealed = true;

    msg!(
        "Manual reveal successful: curator={}, pair={}",
        commitment.curator_wallet,
        commitment.pair_id
    );

    Ok(())
}
