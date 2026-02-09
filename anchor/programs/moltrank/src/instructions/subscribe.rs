use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount, Transfer as SplTransfer};
use crate::state::{GlobalPool, Market, Subscription, SubscriptionType};

#[derive(Accounts)]
#[instruction(market_id: [u8; 32])]
pub struct Subscribe<'info> {
    #[account(
        init,
        payer = reader,
        space = Subscription::LEN,
        seeds = [b"subscription", reader.key().as_ref(), market_id.as_ref()],
        bump
    )]
    pub subscription: Account<'info, Subscription>,

    #[account(
        mut,
        seeds = [b"market", market_id.as_ref()],
        bump = market.bump
    )]
    pub market: Account<'info, Market>,

    #[account(
        mut,
        seeds = [b"global_pool"],
        bump = global_pool.bump
    )]
    pub global_pool: Account<'info, GlobalPool>,

    /// Reader's token account (source of tokens)
    #[account(mut)]
    pub reader_token_account: Account<'info, TokenAccount>,

    /// Global pool's token account (destination of tokens)
    #[account(mut)]
    pub global_pool_token_account: Account<'info, TokenAccount>,

    #[account(mut)]
    pub reader: Signer<'info>,

    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
}

pub fn handler(
    ctx: Context<Subscribe>,
    market_id: [u8; 32],
    amount: u64,
    subscription_type: SubscriptionType,
) -> Result<()> {
    // Transfer tokens from reader to global pool (only for realtime subscriptions)
    if subscription_type == SubscriptionType::Realtime {
        let cpi_accounts = SplTransfer {
            from: ctx.accounts.reader_token_account.to_account_info(),
            to: ctx.accounts.global_pool_token_account.to_account_info(),
            authority: ctx.accounts.reader.to_account_info(),
        };
        let cpi_context = CpiContext::new(
            ctx.accounts.token_program.to_account_info(),
            cpi_accounts,
        );
        token::transfer(cpi_context, amount)?;
    }

    // Initialize subscription account
    let subscription = &mut ctx.accounts.subscription;
    subscription.reader_wallet = ctx.accounts.reader.key();
    subscription.market_id = market_id;
    subscription.amount = amount;
    subscription.subscription_type = subscription_type;
    subscription.bump = ctx.bumps.subscription;

    // Update market statistics
    let market = &mut ctx.accounts.market;

    // Only update revenue for realtime subscriptions
    if subscription_type == SubscriptionType::Realtime {
        market.subscription_revenue = market.subscription_revenue.checked_add(amount)
            .ok_or(ProgramError::ArithmeticOverflow)?;
    }

    market.subscribers = market.subscribers.checked_add(1)
        .ok_or(ProgramError::ArithmeticOverflow)?;

    // Activate market on first subscriber (set max_pairs if it's 0)
    if market.max_pairs == 0 {
        // TODO: Set appropriate max_pairs value based on subscription tier
        // For now, setting a default value
        market.max_pairs = 100;
        msg!("Market activated with max_pairs: {}", market.max_pairs);
    }

    msg!(
        "Subscription created: reader={}, market_id={:?}, type={:?}, amount={}",
        subscription.reader_wallet,
        market_id,
        subscription_type,
        amount
    );

    Ok(())
}
