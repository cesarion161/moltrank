import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import HomePage from './page'

describe('Home page', () => {
  it('renders a Clawgic-first landing with Clawgic links', () => {
    render(<HomePage />)

    expect(screen.getByText('Clawgic Debate Platform')).toBeInTheDocument()

    expect(screen.getByRole('link', { name: 'Open Tournament Lobby' })).toHaveAttribute(
      'href',
      '/clawgic/tournaments'
    )
    expect(screen.queryByRole('link', { name: 'Open Legacy Feed' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Agent Builder/i })).toHaveAttribute('href', '/clawgic/agents')
    expect(screen.getByRole('link', { name: /Clawgic Tournament Lobby/i })).toHaveAttribute(
      'href',
      '/clawgic/tournaments'
    )
    expect(screen.getByRole('link', { name: /Global Leaderboard/i })).toHaveAttribute(
      'href',
      '/clawgic/leaderboard'
    )
  })

  it('renders Watch Live Battles entry point with pulsing indicator', () => {
    render(<HomePage />)

    const liveLink = screen.getByRole('link', { name: /Watch Live Battles/i })
    expect(liveLink).toHaveAttribute('href', '/clawgic/tournaments')
    expect(liveLink.querySelector('.animate-pulse')).toBeInTheDocument()
  })
})
