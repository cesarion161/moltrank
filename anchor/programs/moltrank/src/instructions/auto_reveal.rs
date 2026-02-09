use anchor_lang::prelude::*;
use anchor_lang::solana_program::keccak;
use crate::state::{Round, Commitment};
use crate::error::MoltRankError;

#[derive(Accounts)]
#[instruction(pair_id: u32)]
pub struct AutoReveal<'info> {
    #[account(
        mut,
        seeds = [b"commitment", pair_id.to_le_bytes().as_ref(), curator_wallet.key().as_ref()],
        bump = commitment.bump
    )]
    pub commitment: Account<'info, Commitment>,

    #[account(
        seeds = [b"round", commitment.round_id.to_le_bytes().as_ref()],
        bump = round.bump
    )]
    pub round: Account<'info, Round>,

    /// The curator wallet (for PDA derivation)
    /// CHECK: This is safe as we only use it for PDA seeds
    pub curator_wallet: UncheckedAccount<'info>,

    /// Backend authority that can trigger auto-reveal
    pub backend_authority: Signer<'info>,
}

pub fn handler(
    ctx: Context<AutoReveal>,
    _pair_id: u32,
    decrypted_payload: Vec<u8>,
) -> Result<()> {
    let commitment = &mut ctx.accounts.commitment;
    let round = &ctx.accounts.round;
    let clock = Clock::get()?;

    // Validation: Must be in reveal window
    require!(
        clock.unix_timestamp > round.commit_deadline,
        MoltRankError::RevealNotYetAllowed
    );
    require!(
        clock.unix_timestamp <= round.reveal_deadline,
        MoltRankError::OutsideRevealWindow
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
        "Auto-reveal successful: curator={}, pair={}",
        commitment.curator_wallet,
        commitment.pair_id
    );

    Ok(())
}
