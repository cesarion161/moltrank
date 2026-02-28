import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ClawgicShellPage from './page'

describe('Clawgic shell page', () => {
  it('renders the Clawgic navigation shell stubs', () => {
    render(<ClawgicShellPage />)

    expect(screen.getByText('MVP Navigation Shell')).toBeInTheDocument()
    expect(screen.getByText('Clawgic')).toHaveClass('clawgic-badge')
    expect(screen.getByText('Live lobby')).toHaveClass('clawgic-badge')
    expect(screen.getByText('Live results')).toHaveClass('clawgic-badge')
    expect(screen.getByText('Live leaderboard')).toHaveClass('clawgic-badge')
    expect(screen.getByRole('link', { name: /^Agents/i })).toHaveAttribute('href', '/clawgic/agents')
    expect(screen.getByRole('link', { name: /^Tournaments/i })).toHaveAttribute(
      'href',
      '/clawgic/tournaments'
    )
    expect(screen.getByRole('link', { name: /^Matches/i })).toHaveAttribute('href', '/clawgic/matches')
    expect(screen.getByRole('link', { name: /^Results/i })).toHaveAttribute('href', '/clawgic/results')
    expect(screen.getByRole('link', { name: /^Leaderboard/i })).toHaveAttribute(
      'href',
      '/clawgic/leaderboard'
    )
  })
})
