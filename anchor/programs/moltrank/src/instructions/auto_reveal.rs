use crate::error::MoltRankError;
use crate::instructions::commitment_codec::{compute_commitment_hash, parse_reveal_payload};
use crate::state::{Commitment, Round};
use anchor_lang::prelude::*;

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

pub fn handler(ctx: Context<AutoReveal>, _pair_id: u32, decrypted_payload: Vec<u8>) -> Result<()> {
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
    require!(!commitment.revealed, MoltRankError::AlreadyRevealed);

    let parsed_payload = parse_reveal_payload(&decrypted_payload)?;
    let computed_hash = compute_commitment_hash(commitment, &parsed_payload);
    require!(
        computed_hash == commitment.commitment_hash,
        MoltRankError::HashMismatch
    );

    // Mark as revealed and store vote
    commitment.revealed = true;
    commitment.vote = Some(parsed_payload.vote);

    msg!(
        "Auto-reveal successful: curator={}, pair={}, vote={}",
        commitment.curator_wallet,
        commitment.pair_id,
        parsed_payload.vote
    );

    Ok(())
}
