'use client'

import Link from 'next/link'
import { WalletMultiButton } from '@solana/wallet-adapter-react-ui'
import { LinkTwitter } from './link-twitter'

export function Navbar() {
  const navLinks = [
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
              MoltRank
            </Link>
            <div className="hidden md:flex items-center gap-6">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className="text-sm text-muted-foreground hover:text-foreground transition-colors"
                >
                  {link.label}
                </Link>
              ))}
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
