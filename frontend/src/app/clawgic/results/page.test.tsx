import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClawgicResultsPage from './page'

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

describe('ClawgicResultsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    globalThis.fetch = mockFetch
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders tournament results with transcript, judge output, and elo deltas', async () => {
    mockFetch
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: [
            {
              tournamentId: '00000000-0000-0000-0000-000000000901',
              topic: 'Deterministic finals',
              status: 'COMPLETED',
              bracketSize: 4,
              maxEntries: 4,
              startTime: '2026-03-01T14:00:00Z',
              entryCloseTime: '2026-03-01T13:00:00Z',
              baseEntryFeeUsdc: '5.000000',
              winnerAgentId: '00000000-0000-0000-0000-000000000911',
              matchesCompleted: 3,
              matchesForfeited: 0,
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: [
            {
              agentId: '00000000-0000-0000-0000-000000000911',
              walletAddress: '0x1111111111111111111111111111111111111111',
              name: 'Logic Falcon',
            },
            {
              agentId: '00000000-0000-0000-0000-000000000912',
              walletAddress: '0x2222222222222222222222222222222222222222',
              name: 'Counter Fox',
            },
          ],
        })
      )
      .mockResolvedValueOnce(
        mockResponse({
          ok: true,
          status: 200,
          statusText: 'OK',
          jsonBody: {
            tournament: {
              tournamentId: '00000000-0000-0000-0000-000000000901',
              topic: 'Deterministic finals',
              status: 'COMPLETED',
              bracketSize: 4,
              maxEntries: 4,
              startTime: '2026-03-01T14:00:00Z',
              entryCloseTime: '2026-03-01T13:00:00Z',
              baseEntryFeeUsdc: '5.000000',
              winnerAgentId: '00000000-0000-0000-0000-000000000911',
              matchesCompleted: 3,
              matchesForfeited: 0,
              startedAt: '2026-03-01T14:05:00Z',
              completedAt: '2026-03-01T14:20:00Z',
            },
            entries: [
              {
                entryId: '00000000-0000-0000-0000-000000000921',
                tournamentId: '00000000-0000-0000-0000-000000000901',
                agentId: '00000000-0000-0000-0000-000000000911',
                walletAddress: '0x1111111111111111111111111111111111111111',
                status: 'CONFIRMED',
                seedPosition: 1,
                seedSnapshotElo: 1000,
              },
              {
                entryId: '00000000-0000-0000-0000-000000000922',
                tournamentId: '00000000-0000-0000-0000-000000000901',
                agentId: '00000000-0000-0000-0000-000000000912',
                walletAddress: '0x2222222222222222222222222222222222222222',
                status: 'CONFIRMED',
                seedPosition: 2,
                seedSnapshotElo: 1000,
              },
            ],
            matches: [
              {
                matchId: '00000000-0000-0000-0000-000000000931',
                tournamentId: '00000000-0000-0000-0000-000000000901',
                agent1Id: '00000000-0000-0000-0000-000000000911',
                agent2Id: '00000000-0000-0000-0000-000000000912',
                bracketRound: 2,
                bracketPosition: 1,
                nextMatchId: null,
                nextMatchAgentSlot: null,
                status: 'COMPLETED',
                phase: 'CONCLUSION',
                transcriptJson: [
                  {
                    role: 'agent_1',
                    phase: 'ARGUMENTATION',
                    content: 'Agent one opens with benchmark-backed claims.',
                  },
                  {
                    role: 'agent_2',
                    phase: 'COUNTER_ARGUMENTATION',
                    content: 'Agent two disputes benchmark quality and sample size.',
                  },
                ],
                judgeResultJson: {
                  winner_id: '00000000-0000-0000-0000-000000000911',
                },
                winnerAgentId: '00000000-0000-0000-0000-000000000911',
                agent1EloBefore: 1000,
                agent1EloAfter: 1016,
                agent2EloBefore: 1000,
                agent2EloAfter: 984,
                forfeitReason: null,
                judgeRetryCount: 0,
                judgements: [
                  {
                    judgementId: '00000000-0000-0000-0000-000000000941',
                    matchId: '00000000-0000-0000-0000-000000000931',
                    judgeKey: 'mock-judge-primary',
                    judgeModel: 'mock-gpt4o',
                    status: 'ACCEPTED',
                    attempt: 1,
                    resultJson: {
                      winner_id: '00000000-0000-0000-0000-000000000911',
                    },
                    winnerAgentId: '00000000-0000-0000-0000-000000000911',
                    agent1LogicScore: 9,
                    agent1PersonaAdherenceScore: 8,
                    agent1RebuttalStrengthScore: 9,
                    agent2LogicScore: 8,
                    agent2PersonaAdherenceScore: 7,
                    agent2RebuttalStrengthScore: 8,
                    reasoning: 'Agent one preserved stronger logical continuity.',
                    judgedAt: '2026-03-01T14:18:00Z',
                  },
                ],
              },
            ],
          },
        })
      )

    render(<ClawgicResultsPage />)

    expect(await screen.findByRole('option', { name: /Deterministic finals/i })).toBeInTheDocument()
    expect(await screen.findByText('Transcript Viewer')).toBeInTheDocument()
    expect(screen.getByText('Agent one opens with benchmark-backed claims.')).toBeInTheDocument()
    expect(screen.getByText('Agent two disputes benchmark quality and sample size.')).toBeInTheDocument()
    expect(screen.getByText('Judge Output')).toBeInTheDocument()
    expect(screen.getByText('Accepted verdict')).toBeInTheDocument()
    expect(screen.getByText('Raw Judge JSON')).toBeInTheDocument()
    expect(screen.getByText('Elo Before/After')).toBeInTheDocument()
    expect(screen.getByText('16')).toBeInTheDocument()
    expect(screen.getByText('-16')).toBeInTheDocument()
  })

  it('renders index error state when results index fetch fails', async () => {
    mockFetch.mockRejectedValueOnce(new TypeError('fetch failed'))

    render(<ClawgicResultsPage />)

    expect(await screen.findByText('Failed to load tournament results index.')).toBeInTheDocument()
    expect(screen.getByText('Network error: fetch failed')).toBeInTheDocument()
  })
})
