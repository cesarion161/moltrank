import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClawgicLeaderboardPage from './page'

const mockFetch = vi.fn()

type MockResponseInit = {
  ok: boolean
  status: number
  statusText: string
  jsonBody?: unknown
  textBody?: string
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

describe('ClawgicLeaderboardPage', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    globalThis.fetch = mockFetch
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders leaderboard entries from API response', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: {
          entries: [
            {
              rank: 1,
              previousRank: null,
              rankDelta: null,
              agentId: '00000000-0000-0000-0000-000000000701',
              walletAddress: '0x1111111111111111111111111111111111111111',
              name: 'Logic Falcon',
              avatarUrl: null,
              currentElo: 1216,
              matchesPlayed: 14,
              matchesWon: 10,
              matchesForfeited: 1,
              lastUpdated: '2026-03-01T12:00:00Z',
            },
            {
              rank: 2,
              previousRank: 4,
              rankDelta: 2,
              agentId: '00000000-0000-0000-0000-000000000702',
              walletAddress: '0x2222222222222222222222222222222222222222',
              name: 'Counter Fox',
              avatarUrl: null,
              currentElo: 1204,
              matchesPlayed: 15,
              matchesWon: 9,
              matchesForfeited: 2,
              lastUpdated: '2026-03-01T12:00:00Z',
            },
          ],
          offset: 0,
          limit: 20,
          total: 8,
          hasMore: true,
        },
      })
    )

    render(<ClawgicLeaderboardPage />)

    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Global Elo leaderboard table' })).toBeInTheDocument()
    expect(screen.getByText('Counter Fox')).toBeInTheDocument()
    expect(screen.getByText('1216')).toBeInTheDocument()
    expect(screen.getByText('Up 2')).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/clawgic/agents/leaderboard?offset=0&limit=20',
      expect.objectContaining({ method: 'GET' })
    )
  })

  it('renders empty state when leaderboard has no entries', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: {
          entries: [],
          offset: 0,
          limit: 20,
          total: 0,
          hasMore: false,
        },
      })
    )

    render(<ClawgicLeaderboardPage />)

    expect(await screen.findByText(/No ranked agents yet\./)).toBeInTheDocument()
  })

  it('renders error state when leaderboard fetch fails', async () => {
    mockFetch.mockRejectedValueOnce(new TypeError('fetch failed'))

    render(<ClawgicLeaderboardPage />)

    expect(await screen.findByText('Failed to load leaderboard.')).toBeInTheDocument()
    expect(screen.getByText('Network error: fetch failed')).toBeInTheDocument()
  })

  it('requests the next page when next button is clicked', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: {
            entries: [
              {
                rank: 1,
                previousRank: null,
                rankDelta: null,
                agentId: '00000000-0000-0000-0000-000000000703',
                walletAddress: '0x3333333333333333333333333333333333333333',
                name: 'First Page Agent',
                avatarUrl: null,
                currentElo: 1300,
                matchesPlayed: 10,
                matchesWon: 7,
                matchesForfeited: 1,
                lastUpdated: '2026-03-01T12:00:00Z',
              },
            ],
            offset: 0,
            limit: 20,
            total: 21,
            hasMore: true,
          },
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: {
            entries: [
              {
                rank: 21,
                previousRank: null,
                rankDelta: null,
                agentId: '00000000-0000-0000-0000-000000000704',
                walletAddress: '0x4444444444444444444444444444444444444444',
                name: 'Second Page Agent',
                avatarUrl: null,
                currentElo: 1022,
                matchesPlayed: 6,
                matchesWon: 3,
                matchesForfeited: 0,
                lastUpdated: '2026-03-01T12:05:00Z',
              },
            ],
            offset: 20,
            limit: 20,
            total: 21,
            hasMore: false,
          },
        })
      )

    render(<ClawgicLeaderboardPage />)
    await screen.findByText('First Page Agent')

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))

    await screen.findByText('Second Page Agent')

    await waitFor(() => {
      expect(mockFetch).toHaveBeenNthCalledWith(
        2,
        'http://localhost:8080/api/clawgic/agents/leaderboard?offset=20&limit=20',
        expect.objectContaining({ method: 'GET' })
      )
    })
  })
})
