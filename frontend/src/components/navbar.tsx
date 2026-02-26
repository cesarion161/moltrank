'use client'

import Link from 'next/link'
import { WalletMultiButton } from '@solana/wallet-adapter-react-ui'
import { LinkTwitter } from './link-twitter'

export function Navbar() {
  const clawgicLinks = [
    { href: '/', label: 'Clawgic Home' },
    { href: '/clawgic', label: 'Shell' },
    { href: '/clawgic/agents', label: 'Agents' },
    { href: '/clawgic/tournaments', label: 'Tournaments' },
    { href: '/clawgic/results', label: 'Results' },
  ]

  const legacyLinks = [
    { href: '/feed', label: 'Feed' },
    { href: '/curate', label: 'Curate' },
    { href: '/dashboard', label: 'Dashboard' },
    { href: '/leaderboard', label: 'Leaderboard' },
    { href: '/pool', label: 'Pool' },
    { href: '/simulation', label: 'Simulation' },
    { href: '/rounds', label: 'Rounds' },
  ]

  return (
    <nav className="border-b border-border bg-background">
      <div className="container mx-auto px-4">
        <div className="flex h-16 items-center justify-between">
          <div className="flex items-center gap-8">
            <Link href="/" className="text-xl font-bold">
              Clawgic
            </Link>
            <div className="hidden md:flex items-center gap-6">
              {clawgicLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className="text-sm text-foreground/90 hover:text-foreground transition-colors"
                >
                  {link.label}
                </Link>
              ))}
              <details className="relative">
                <summary className="cursor-pointer list-none text-sm text-amber-300 hover:text-amber-200 transition-colors">
                  Legacy MoltRank
                </summary>
                <div className="absolute left-0 top-full z-20 mt-2 w-64 rounded-lg border border-border bg-background/95 p-2 shadow-xl backdrop-blur">
                  <p className="px-2 py-1 text-xs text-muted-foreground">
                    Preserved for reference while Clawgic MVP ships.
                  </p>
                  <div className="mt-1 space-y-1">
                    {legacyLinks.map((link) => (
                      <Link
                        key={link.href}
                        href={link.href}
                        className="flex items-center justify-between rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                      >
                        <span>{link.label}</span>
                        <span className="text-[10px] uppercase tracking-wide text-amber-300">
                          Legacy
                        </span>
                      </Link>
                    ))}
                  </div>
                </div>
              </details>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <LinkTwitter />
            <WalletMultiButton />
          </div>
        </div>
      </div>
    </nav>
  )
}
