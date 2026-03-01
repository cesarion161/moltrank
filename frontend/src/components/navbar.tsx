'use client'

import Link from 'next/link'
import { WalletMultiButton } from '@solana/wallet-adapter-react-ui'
import { LinkTwitter } from './link-twitter'
import { ClawgicLogo } from './clawgic-logo'

export function Navbar() {
  const clawgicLinks = [
    { href: '/', label: 'Clawgic Home' },
    { href: '/clawgic/agents', label: 'Agents' },
    { href: '/clawgic/tournaments', label: 'Tournaments' },
    { href: '/clawgic/tournaments', label: 'Live', isLive: true },
    { href: '/clawgic/results', label: 'Results' },
    { href: '/clawgic/leaderboard', label: 'Leaderboard' },
  ]

  return (
    <nav className="clawgic-nav-blur sticky top-0 z-40">
      <div className="container mx-auto px-4">
        <div className="flex min-h-[5.25rem] flex-wrap items-center justify-between gap-4 py-3">
          <div className="flex items-center gap-4">
            <Link
              href="/"
              className="group rounded-xl px-1 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
              aria-label="Clawgic"
            >
              <ClawgicLogo labelClassName="text-xl transition-colors group-hover:text-accent-foreground" />
            </Link>
          </div>

          <div className="order-3 w-full overflow-x-auto pb-1 md:order-none md:w-auto md:pb-0">
            <div className="flex min-w-max items-center gap-4 pr-2 md:gap-5">
              {clawgicLinks.map((link) => (
                <Link
                  key={link.label}
                  href={link.href}
                  className={`rounded-lg px-2 py-1 text-sm font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/60 ${
                    link.isLive
                      ? 'inline-flex items-center gap-1.5 text-blue-700 hover:bg-blue-50 hover:text-blue-800'
                      : 'text-foreground/85 hover:bg-accent hover:text-accent-foreground'
                  }`}
                >
                  {link.isLive ? (
                    <span className="inline-block h-2 w-2 rounded-full bg-blue-500 animate-pulse" aria-hidden="true" />
                  ) : null}
                  {link.label}
                </Link>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <LinkTwitter />
            <WalletMultiButton className="clawgic-primary-btn !h-10 !rounded-xl !px-4 !py-2.5 !text-sm !font-semibold" />
          </div>
        </div>
      </div>
    </nav>
  )
}
