use anchor_lang::prelude::*;
use crate::state::Identity;

#[derive(Accounts)]
pub struct RegisterIdentity<'info> {
    #[account(
        init,
        payer = authority,
        space = Identity::LEN,
        seeds = [b"identity", authority.key().as_ref()],
        bump
    )]
    pub identity: Account<'info, Identity>,

    #[account(mut)]
    pub authority: Signer<'info>,

    pub system_program: Program<'info, System>,
}

pub fn handler(ctx: Context<RegisterIdentity>, identity_id: [u8; 32]) -> Result<()> {
    let identity = &mut ctx.accounts.identity;
    let clock = Clock::get()?;

    identity.wallet = ctx.accounts.authority.key();
    identity.identity_id = identity_id;
    identity.verified = false; // Initially unverified, verification happens separately
    identity.created_at = clock.unix_timestamp;
    identity.alignment_score = 0;
    identity.bump = ctx.bumps.identity;

    msg!("Identity registered for wallet: {}", identity.wallet);
    msg!("Identity ID: {:?}", identity.identity_id);

    Ok(())
}
