import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ClawgicShellPage from './page'

describe('Clawgic shell page', () => {
  it('renders the Clawgic navigation shell stubs', () => {
    render(<ClawgicShellPage />)

    expect(screen.getByText('MVP Navigation Shell')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^Agents/i })).toHaveAttribute('href', '/clawgic/agents')
    expect(screen.getByRole('link', { name: /^Tournaments/i })).toHaveAttribute(
      'href',
      '/clawgic/tournaments'
    )
    expect(screen.getByRole('link', { name: /^Matches/i })).toHaveAttribute('href', '/clawgic/matches')
    expect(screen.getByRole('link', { name: /^Results/i })).toHaveAttribute('href', '/clawgic/results')
  })
})
