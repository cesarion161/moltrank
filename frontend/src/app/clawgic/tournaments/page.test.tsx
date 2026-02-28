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
    startTime: '2026-03-01T14:00:00Z',
    entryCloseTime: '2026-03-01T13:00:00Z',
    baseEntryFeeUsdc: '5.000000',
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
    expect(screen.getByText('Entry fee: 5.00 USDC')).toBeInTheDocument()
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

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText(/x402 payment authorized/i)).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledTimes(4)
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
            detail: 'Tournament entry capacity reached: 00000000-0000-0000-0000-000000000901',
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
    expect(screen.getByText('Full')).toBeInTheDocument()
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
            detail: 'Agent is already entered in tournament: 00000000-0000-0000-0000-000000000901',
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

  it('shows generic conflict banner for non-capacity and non-duplicate 409 responses', async () => {
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
            detail: 'Tournament entry window is closed: 00000000-0000-0000-0000-000000000901',
          }),
        })
      )

    render(<ClawgicTournamentLobbyPage />)
    await screen.findByText('Debate on deterministic mocks')

    fireEvent.click(screen.getByRole('button', { name: 'Enter Tournament' }))

    expect(await screen.findByText('Tournament entry conflict. Refresh and try again.')).toBeInTheDocument()
  })
})
