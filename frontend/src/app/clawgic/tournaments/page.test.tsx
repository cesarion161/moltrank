import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClawgicTournamentLobbyPage from './page'

const mockFetch = vi.fn()

type MockResponseInit = {
  ok: boolean
  status: number
  statusText: string
  jsonBody?: unknown
  textBody?: string
}

const tournamentsFixture = [
  {
    tournamentId: '00000000-0000-0000-0000-000000000901',
    topic: 'Debate on deterministic mocks',
    status: 'SCHEDULED',
    bracketSize: 4,
    maxEntries: 4,
    currentEntries: 1,
    startTime: '2026-03-01T14:00:00Z',
    entryCloseTime: '2026-03-01T13:00:00Z',
    baseEntryFeeUsdc: '5.000000',
    canEnter: true,
    entryState: 'OPEN',
    entryStateReason: null,
  },
]

const agentsFixture = [
  {
    agentId: '00000000-0000-0000-0000-000000000911',
    walletAddress: '0x1111111111111111111111111111111111111111',
    name: 'Logic Falcon',
    providerType: 'OPENAI',
  },
  {
    agentId: '00000000-0000-0000-0000-000000000912',
    walletAddress: '0x2222222222222222222222222222222222222222',
    name: 'Counter Fox',
    providerType: 'ANTHROPIC',
  },
]

function x402ChallengeFixture() {
  return {
    scheme: 'x402',
    network: 'base-sepolia',
    chainId: 84532,
    tokenAddress: '0x0000000000000000000000000000000000000a11',
    priceUsdc: '5.000000',
    recipient: '0x0000000000000000000000000000000000000b22',
    paymentHeader: 'X-PAYMENT',
    nonce: 'challenge-nonce-001',
    challengeExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
  }
}

function mockResponse(init: MockResponseInit) {
  return {
    ok: init.ok,
    status: init.status,
    statusText: init.statusText,
    json: () => Promise.resolve(init.jsonBody),
    text: () => Promise.resolve(init.textBody || ''),
  }
}

describe('ClawgicTournamentLobbyPage', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    globalThis.fetch = mockFetch
    vi.spyOn(console, 'error').mockImplementation(() => {})
    ;(window as Window & { ethereum?: unknown }).ethereum = undefined
  })

  afterEach(() => {
    vi.restoreAllMocks()
    ;(window as Window & { ethereum?: unknown }).ethereum = undefined
  })

  it('renders loading then tournament details on successful data fetch', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )

    render(<ClawgicTournamentLobbyPage />)

    expect(screen.getByText('Loading tournament lobby...')).toBeInTheDocument()
    expect(await screen.findByText('Debate on deterministic mocks')).toBeInTheDocument()
    expect(screen.getByText('Status: SCHEDULED')).toBeInTheDocument()
    expect(screen.getByText('Entries: 1/4')).toBeInTheDocument()
    expect(screen.getByText('Entry fee: 5.00 USDC')).toBeInTheDocument()
    expect(screen.getByText('Open')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enter Tournament' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Enter Tournament' })).toHaveClass('clawgic-primary-btn')
    expect(
      screen.getByRole('combobox', { name: /Select agent for Debate on deterministic mocks/i })
    ).toHaveClass('clawgic-select')
    expect(screen.getByText('Clawgic')).toHaveClass('clawgic-badge')
  })

  it('renders error state when lobby fetch fails', async () => {
    mockFetch
      .mockRejectedValueOnce(new TypeError('fetch failed'))
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )

    render(<ClawgicTournamentLobbyPage />)

    expect(await screen.findByText('Failed to load tournament lobby.')).toBeInTheDocument()
    expect(screen.getByText('Network error: fetch failed')).toBeInTheDocument()
  })

  it('submits tournament entry and shows success banner', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 201,
          statusText: 'Created',
          jsonBody: {
            entryId: '00000000-0000-0000-0000-000000000920',
            tournamentId: tournamentsFixture[0].tournamentId,
            agentId: agentsFixture[0].agentId,
            walletAddress: agentsFixture[0].walletAddress,
            status: 'CONFIRMED',
            seedSnapshotElo: 1000,
          },
        })
      )
      // post-entry refresh (tournaments + agents)
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText(/entered successfully/i)).toBeInTheDocument()
    expect(mockFetch).toHaveBeenNthCalledWith(
      3,
      'http://localhost:8080/api/clawgic/tournaments/00000000-0000-0000-0000-000000000901/enter',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ agentId: '00000000-0000-0000-0000-000000000911' }),
      })
    )
  })

  it('handles 402 challenge and retries entry with signed X-PAYMENT header', async () => {
    const ethereumRequest = vi.fn(async (args: { method: string; params?: unknown[] | object }) => {
      if (args.method === 'eth_chainId') {
        return '0x14a34'
      }
      if (args.method === 'eth_requestAccounts') {
        return [agentsFixture[0].walletAddress]
      }
      if (args.method === 'eth_signTypedData_v4') {
        return `0x${'a'.repeat(130)}`
      }
      return null
    })
    ;(window as Window & { ethereum?: { request: typeof ethereumRequest } }).ethereum = {
      request: ethereumRequest,
    }

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 402,
          statusText: 'Payment Required',
          textBody: JSON.stringify(x402ChallengeFixture()),
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 201,
          statusText: 'Created',
          jsonBody: {
            entryId: '00000000-0000-0000-0000-000000000921',
            tournamentId: tournamentsFixture[0].tournamentId,
            agentId: agentsFixture[0].agentId,
            walletAddress: agentsFixture[0].walletAddress,
            status: 'CONFIRMED',
            seedSnapshotElo: 1000,
          },
        })
      )
      // post-entry refresh (tournaments + agents)
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText(/x402 payment authorized/i)).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledTimes(6)
    expect(mockFetch).toHaveBeenNthCalledWith(
      4,
      'http://localhost:8080/api/clawgic/tournaments/00000000-0000-0000-0000-000000000901/enter',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          'X-PAYMENT': expect.any(String),
        }),
      })
    )

    const retryCall = mockFetch.mock.calls[3]
    const requestInit = retryCall?.[1] as RequestInit
    const headers = requestInit.headers as Record<string, string>
    const paymentHeaderJson = JSON.parse(headers['X-PAYMENT']) as {
      requestNonce: string
      payload: {
        authorization: {
          signature: string
        }
      }
    }

    expect(paymentHeaderJson.requestNonce).toBe('challenge-nonce-001')
    expect(paymentHeaderJson.payload.authorization.signature).toBe(`0x${'a'.repeat(130)}`)
  })

  it('shows wallet mismatch message when 402 retry signer does not match selected agent wallet', async () => {
    const ethereumRequest = vi.fn(async (args: { method: string }) => {
      if (args.method === 'eth_chainId') {
        return '0x14a34'
      }
      if (args.method === 'eth_requestAccounts') {
        return ['0x9999999999999999999999999999999999999999']
      }
      if (args.method === 'eth_signTypedData_v4') {
        return `0x${'b'.repeat(130)}`
      }
      return null
    })
    ;(window as Window & { ethereum?: { request: typeof ethereumRequest } }).ethereum = {
      request: ethereumRequest,
    }

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 402,
          statusText: 'Payment Required',
          textBody: JSON.stringify(x402ChallengeFixture()),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText(/does not match selected agent wallet/i)).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledTimes(3)
  })

  it('shows full-state messaging when entry API returns capacity conflict', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 409,
          statusText: 'Conflict',
          textBody: JSON.stringify({
            code: 'capacity_reached',
            message: 'Tournament entry capacity reached: 00000000-0000-0000-0000-000000000901',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(
      await screen.findByText('Tournament is full. Choose another tournament or wait for a new round.')
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Enter Tournament' })).toBeDisabled()
    )
    expect(screen.getByRole('button', { name: 'Enter Tournament' })).toHaveClass('clawgic-primary-btn')
  })

  it('shows duplicate-entry messaging when entry API returns duplicate conflict', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 409,
          statusText: 'Conflict',
          textBody: JSON.stringify({
            code: 'already_entered',
            message: 'Agent is already entered in tournament: 00000000-0000-0000-0000-000000000901',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(
      await screen.findByText('This agent is already entered in the selected tournament.')
    ).toBeInTheDocument()
  })

  it('shows entry-window-closed messaging when entry API returns entry_window_closed conflict code', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 409,
          statusText: 'Conflict',
          textBody: JSON.stringify({
            code: 'entry_window_closed',
            message: 'Tournament entry window is closed: 00000000-0000-0000-0000-000000000901',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText('Entry window has closed for this tournament.')).toBeInTheDocument()
  })

  it('shows generic conflict banner when 409 has no recognized conflict code', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: tournamentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: agentsFixture,
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 409,
          statusText: 'Conflict',
          textBody: JSON.stringify({
            detail: 'Some unknown conflict reason',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText('Tournament entry conflict. Refresh and try again.')).toBeInTheDocument()
  })

  it('renders disabled button and Closed badge when backend says entry window is closed', async () => {
    const closedTournament = [
      {
        ...tournamentsFixture[0],
        canEnter: false,
        entryState: 'ENTRY_WINDOW_CLOSED',
        entryStateReason: 'Entry window has closed.',
      },
    ]

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: closedTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    expect(screen.getByText('Closed')).toBeInTheDocument()
    expect(screen.getByText('Entry window has closed.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enter Tournament' })).toBeDisabled()
    expect(
      screen.getByRole('combobox', { name: /Select agent for Debate on deterministic mocks/i })
    ).toBeDisabled()
  })

  it('renders Starting Soon badge and Watch Live button when tournament is LOCKED', async () => {
    const lockedTournament = [
      {
        ...tournamentsFixture[0],
        status: 'LOCKED',
        canEnter: false,
        entryState: 'TOURNAMENT_NOT_OPEN',
        entryStateReason: 'Tournament is locked and no longer accepting entries.',
      },
    ]

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: lockedTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    expect(screen.getByText('Starting Soon')).toBeInTheDocument()
    expect(screen.getByText('Tournament is locked and no longer accepting entries.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Watch Live/i })).toHaveAttribute(
      'href',
      `/clawgic/tournaments/${tournamentsFixture[0].tournamentId}/live`
    )
    // Entry controls should be hidden for LOCKED tournaments
    expect(screen.queryByRole('button', { name: 'Enter Tournament' })).not.toBeInTheDocument()
  })

  it('renders disabled button and Full badge when backend says capacity is reached', async () => {
    const fullTournament = [
      {
        ...tournamentsFixture[0],
        currentEntries: 4,
        canEnter: false,
        entryState: 'CAPACITY_REACHED',
        entryStateReason: 'Tournament is at full capacity.',
      },
    ]

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: fullTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    expect(screen.getByText('Full')).toBeInTheDocument()
    expect(screen.getByText('Entries: 4/4')).toBeInTheDocument()
    expect(screen.getByText('Tournament is at full capacity.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enter Tournament' })).toBeDisabled()
  })

  it('does not send entry request when button is disabled for non-enterable tournament', async () => {
    const closedTournament = [
      {
        ...tournamentsFixture[0],
        canEnter: false,
        entryState: 'ENTRY_WINDOW_CLOSED',
        entryStateReason: 'Entry window has closed.',
      },
    ]

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: closedTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    const button = screen.getByRole('button', { name: 'Enter Tournament' })
    expect(button).toBeDisabled()

    fireEvent.click(button)

    // No additional fetch calls beyond the initial 2 (tournaments + agents)
    expect(mockFetch).toHaveBeenCalledTimes(2)
  })

  it('shows invalid_agent messaging when entry API returns 404 with invalid_agent code', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 404,
          statusText: 'Not Found',
          textBody: JSON.stringify({
            code: 'invalid_agent',
            message: 'Agent not found: 00000000-0000-0000-0000-000000000911',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(
      await screen.findByText('Agent not found. Refresh and try again.')
    ).toBeInTheDocument()
    // invalid_agent is recoverable, so refresh button should be shown
    expect(screen.getByRole('button', { name: 'Refresh lobby data' })).toBeInTheDocument()
  })

  it('shows tournament_not_open messaging when entry API returns tournament_not_open conflict code', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 409,
          statusText: 'Conflict',
          textBody: JSON.stringify({
            code: 'tournament_not_open',
            message: 'Tournament is not open for entries.',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(
      await screen.findByText('This tournament is not open for entries.')
    ).toBeInTheDocument()
  })

  it('refreshes lobby data after successful entry and updates entries count', async () => {
    const updatedTournament = [
      { ...tournamentsFixture[0], currentEntries: 2 },
    ]

    mockFetch
      // initial load
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )
      // entry success
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 201,
          statusText: 'Created',
          jsonBody: {
            entryId: '00000000-0000-0000-0000-000000000920',
            tournamentId: tournamentsFixture[0].tournamentId,
            agentId: agentsFixture[0].agentId,
            walletAddress: agentsFixture[0].walletAddress,
            status: 'CONFIRMED',
            seedSnapshotElo: 1000,
          },
        })
      )
      // post-entry refresh
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: updatedTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Entries: 1/4')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText(/entered successfully/i)).toBeInTheDocument()
    // After refresh, entries count is updated from backend
    await waitFor(() => expect(screen.getByText('Entries: 2/4')).toBeInTheDocument())
    // 5 total fetch calls: 2 initial + 1 entry + 2 refresh
    expect(mockFetch).toHaveBeenCalledTimes(5)
  })

  it('shows Refresh lobby data button on recoverable conflict and clears banners on refresh', async () => {
    const refreshedTournament = [
      { ...tournamentsFixture[0], currentEntries: 3 },
    ]

    mockFetch
      // initial load
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )
      // entry conflict
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 409,
          statusText: 'Conflict',
          textBody: JSON.stringify({
            code: 'capacity_reached',
            message: 'Tournament entry capacity reached.',
          }),
        })
      )
      // refresh after clicking recovery button
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: refreshedTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    // Recoverable conflict shows banner with refresh button
    expect(
      await screen.findByText('Tournament is full. Choose another tournament or wait for a new round.')
    ).toBeInTheDocument()
    const refreshBtn = screen.getByRole('button', { name: 'Refresh lobby data' })
    expect(refreshBtn).toBeInTheDocument()

    // Click refresh clears banners and fetches fresh data
    fireEvent.click(refreshBtn)

    await waitFor(() => expect(screen.getByText('Entries: 3/4')).toBeInTheDocument())
    // Banner should be cleared after refresh
    expect(
      screen.queryByText('Tournament is full. Choose another tournament or wait for a new round.')
    ).not.toBeInTheDocument()
  })

  it('shows top-level Refresh lobby button and refreshes data when clicked', async () => {
    const refreshedTournament = [
      { ...tournamentsFixture[0], currentEntries: 2 },
    ]

    mockFetch
      // initial load
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )
      // refresh
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: refreshedTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Entries: 1/4')

    const refreshBtn = screen.getByRole('button', { name: 'Refresh lobby' })
    expect(refreshBtn).toBeInTheDocument()

    fireEvent.click(refreshBtn)

    await waitFor(() => expect(screen.getByText('Entries: 2/4')).toBeInTheDocument())
    expect(mockFetch).toHaveBeenCalledTimes(4)
  })

  it('does not show Refresh lobby data button on non-recoverable conflict (already_entered)', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: false,
          status: 409,
          statusText: 'Conflict',
          textBody: JSON.stringify({
            code: 'already_entered',
            message: 'Agent is already entered in tournament.',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(
      await screen.findByText('This agent is already entered in the selected tournament.')
    ).toBeInTheDocument()
    // No refresh button for already_entered (not recoverable)
    expect(screen.queryByRole('button', { name: 'Refresh lobby data' })).not.toBeInTheDocument()
  })

  it('syncs fullTournamentIds from backend on refresh when tournament becomes full', async () => {
    const nowFullTournament = [
      {
        ...tournamentsFixture[0],
        currentEntries: 4,
        canEnter: false,
        entryState: 'CAPACITY_REACHED' as const,
        entryStateReason: 'Tournament is at full capacity.',
      },
    ]

    mockFetch
      // initial load - tournament is open
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: tournamentsFixture })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )
      // refresh returns full tournament
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: nowFullTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Entries: 1/4')
    expect(screen.getByRole('button', { name: 'Enter Tournament' })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: 'Refresh lobby' }))

    await waitFor(() => expect(screen.getByText('Full')).toBeInTheDocument())
    expect(screen.getByText('Entries: 4/4')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enter Tournament' })).toBeDisabled()
  })

  it('renders Live Now badge with pulsing indicator and Watch Live button for IN_PROGRESS tournament', async () => {
    const inProgressTournament = [
      {
        ...tournamentsFixture[0],
        status: 'IN_PROGRESS',
        canEnter: false,
        entryState: 'TOURNAMENT_NOT_OPEN',
        entryStateReason: 'Tournament is in progress.',
      },
    ]

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: inProgressTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    expect(screen.getByText('Live Now')).toBeInTheDocument()
    // Pulsing indicator inside the badge
    const badge = screen.getByText('Live Now').closest('span')
    expect(badge?.querySelector('.animate-pulse')).toBeInTheDocument()
    // Watch Live button present
    const watchLiveLink = screen.getByRole('link', { name: /Watch Live/i })
    expect(watchLiveLink).toHaveAttribute(
      'href',
      `/clawgic/tournaments/${tournamentsFixture[0].tournamentId}/live`
    )
    // Entry controls hidden for IN_PROGRESS
    expect(screen.queryByRole('button', { name: 'Enter Tournament' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('combobox', { name: /Select agent/i })
    ).not.toBeInTheDocument()
  })

  it('renders Completed badge and View Results link for COMPLETED tournament', async () => {
    const completedTournament = [
      {
        ...tournamentsFixture[0],
        status: 'COMPLETED',
        canEnter: false,
        entryState: 'TOURNAMENT_NOT_OPEN',
        entryStateReason: 'Tournament is completed.',
      },
    ]

    mockFetch
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: completedTournament })
      )
      .mockResolvedValueOnce(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    expect(screen.getByText('Completed')).toBeInTheDocument()
    // View Results link present
    expect(screen.getByRole('link', { name: 'View Results' })).toHaveAttribute(
      'href',
      '/clawgic/results'
    )
    // Entry controls hidden for COMPLETED
    expect(screen.queryByRole('button', { name: 'Enter Tournament' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('combobox', { name: /Select agent/i })
    ).not.toBeInTheDocument()
    // No Watch Live link
    expect(screen.queryByRole('link', { name: /Watch Live/i })).not.toBeInTheDocument()
  })
})
