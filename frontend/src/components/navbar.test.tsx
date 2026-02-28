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
  it('shows Clawgic-first navigation', () => {
    const { container } = render(<Navbar />)

    expect(screen.getByRole('link', { name: 'Clawgic' })).toHaveAttribute('href', '/')
    expect(screen.getByTestId('clawgic-logo-wordmark')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Clawgic Home' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Shell' })).toHaveAttribute('href', '/clawgic')
    expect(screen.getByRole('link', { name: 'Leaderboard' })).toHaveAttribute('href', '/clawgic/leaderboard')
    expect(screen.queryByText('Legacy', { selector: 'summary' })).not.toBeInTheDocument()
    expect(container.querySelector('nav')).toHaveClass('clawgic-nav-blur')
    expect(screen.getByRole('button', { name: 'Wallet' })).toBeInTheDocument()
  })
})
