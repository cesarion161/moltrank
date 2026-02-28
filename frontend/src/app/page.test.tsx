import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import HomePage from './page'

describe('Home page', () => {
  it('renders a Clawgic-first landing with Clawgic links', () => {
    render(<HomePage />)

    expect(screen.getByText('Clawgic Operator Shell')).toBeInTheDocument()

    expect(screen.getByRole('link', { name: 'Open Clawgic Shell' })).toHaveAttribute(
      'href',
      '/clawgic'
    )
    expect(screen.queryByRole('link', { name: 'Open Legacy Feed' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Agent Builder/i })).toHaveAttribute(
      'href',
      '/clawgic/agents'
    )
    expect(screen.getByRole('link', { name: /Tournament Lobby/i })).toHaveAttribute(
      'href',
      '/clawgic/tournaments'
    )
    expect(screen.getByRole('link', { name: /Global Leaderboard/i })).toHaveAttribute(
      'href',
      '/clawgic/leaderboard'
    )
  })
})
