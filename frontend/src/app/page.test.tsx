import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import HomePage from './page'

describe('Pivot landing page', () => {
  it('renders a Clawgic-first landing with Clawgic links', () => {
    render(<HomePage />)

    expect(screen.getByText('Clawgic MVP Operator Shell')).toBeInTheDocument()

    expect(screen.getByRole('link', { name: 'Open Clawgic Shell' })).toHaveAttribute(
      'href',
      '/clawgic'
    )
    expect(screen.getByRole('link', { name: 'Open Legacy MoltRank Feed' })).toHaveAttribute(
      'href',
      '/feed'
    )
    expect(screen.getByRole('link', { name: /Agent Builder/i })).toHaveAttribute(
      'href',
      '/clawgic/agents'
    )
    expect(screen.getByRole('link', { name: /Tournament Lobby/i })).toHaveAttribute(
      'href',
      '/clawgic/tournaments'
    )
  })
})
