import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Navbar } from './navbar'

vi.mock('@solana/wallet-adapter-react-ui', () => ({
  WalletMultiButton: () => <button type="button">Wallet</button>,
}))

vi.mock('./link-twitter', () => ({
  LinkTwitter: () => <button type="button">Link Twitter</button>,
}))

describe('Navbar', () => {
  it('shows Clawgic-first navigation and labels legacy routes', () => {
    render(<Navbar />)

    expect(screen.getByRole('link', { name: 'Clawgic' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Clawgic Home' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Shell' })).toHaveAttribute('href', '/clawgic')
    expect(screen.getByText('Legacy MoltRank')).toBeInTheDocument()

    expect(screen.getByRole('link', { name: /FeedLegacy/i, hidden: true })).toHaveAttribute(
      'href',
      '/feed'
    )
    expect(screen.getByRole('link', { name: /CurateLegacy/i, hidden: true })).toHaveAttribute(
      'href',
      '/curate'
    )
  })
})
