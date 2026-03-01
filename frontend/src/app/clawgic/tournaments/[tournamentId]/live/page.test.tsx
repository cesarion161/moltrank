import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import LiveBattleArenaPage from './page'

const mockRouterPush = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockRouterPush,
    replace: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    refresh: vi.fn(),
  }),
}))

function mockResponse(init: {
  ok: boolean
  status: number
  statusText: string
  jsonBody?: unknown
  textBody?: string
}) {
  return {
    ok: init.ok,
    status: init.status,
    statusText: init.statusText,
    json: () => Promise.resolve(init.jsonBody),
    text: () => Promise.resolve(init.textBody || ''),
  }
}

const TOURNAMENT_ID = '11111111-1111-1111-1111-111111111111'

const sampleAgents = [
  { agentId: 'aaaa-1111', walletAddress: '0x1111', name: 'AlphaBot', providerType: 'OPENAI' },
  { agentId: 'aaaa-2222', walletAddress: '0x2222', name: 'BetaBot', providerType: 'ANTHROPIC' },
  { agentId: 'aaaa-3333', walletAddress: '0x3333', name: 'GammaBot', providerType: 'OPENAI' },
  { agentId: 'aaaa-4444', walletAddress: '0x4444', name: 'DeltaBot', providerType: 'MOCK' },
]

const sampleLiveStatus = {
  tournamentId: TOURNAMENT_ID,
  topic: 'AI Ethics Debate',
  status: 'IN_PROGRESS',
  startTime: new Date(Date.now() - 60_000).toISOString(),
  entryCloseTime: new Date(Date.now() - 120_000).toISOString(),
  serverTime: new Date().toISOString(),
  activeMatchId: 'match-sf1',
  tournamentWinnerAgentId: null,
  matchesCompleted: 0,
  matchesForfeited: 0,
  bracket: [
    {
      matchId: 'match-sf1',
      status: 'IN_PROGRESS',
      phase: 'ARGUMENTATION',
      agent1Id: 'aaaa-1111',
      agent2Id: 'aaaa-4444',
      winnerAgentId: null,
      bracketRound: 1,
      bracketPosition: 1,
    },
    {
      matchId: 'match-sf2',
      status: 'SCHEDULED',
      phase: null,
      agent1Id: 'aaaa-2222',
      agent2Id: 'aaaa-3333',
      winnerAgentId: null,
      bracketRound: 1,
      bracketPosition: 2,
    },
    {
      matchId: 'match-final',
      status: 'SCHEDULED',
      phase: null,
      agent1Id: null,
      agent2Id: null,
      winnerAgentId: null,
      bracketRound: 2,
      bracketPosition: 1,
    },
  ],
}

const sampleMatchDetail = {
  matchId: 'match-sf1',
  tournamentId: TOURNAMENT_ID,
  agent1Id: 'aaaa-1111',
  agent2Id: 'aaaa-4444',
  bracketRound: 1,
  bracketPosition: 1,
  status: 'IN_PROGRESS',
  phase: 'ARGUMENTATION',
  transcriptJson: [
    { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'I argue that AI safety is paramount.' },
    { role: 'agent_2', phase: 'THESIS_DISCOVERY', content: 'I counter that AI autonomy drives innovation.' },
    { role: 'agent_1', phase: 'ARGUMENTATION', content: 'Without safety guardrails, innovation is reckless.' },
  ],
  judgeResultJson: null,
  winnerAgentId: null,
  agent1EloBefore: null,
  agent1EloAfter: null,
  agent2EloBefore: null,
  agent2EloAfter: null,
  forfeitReason: null,
  judgements: [],
  executionDeadlineAt: new Date(Date.now() + 300_000).toISOString(),
  startedAt: new Date(Date.now() - 30_000).toISOString(),
  judgeRequestedAt: null,
  judgedAt: null,
  forfeitedAt: null,
  completedAt: null,
}

const paramsPromise = Promise.resolve({ tournamentId: TOURNAMENT_ID })

let mockFetch: ReturnType<typeof vi.fn>

function setupFetch(liveStatus = sampleLiveStatus, matchDetail = sampleMatchDetail) {
  mockFetch = vi.fn((url: string) => {
    if (typeof url === 'string' && url.includes(`/tournaments/${TOURNAMENT_ID}/live`)) {
      return Promise.resolve(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: liveStatus })
      )
    }
    if (typeof url === 'string' && url.includes('/clawgic/agents')) {
      return Promise.resolve(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: sampleAgents })
      )
    }
    if (typeof url === 'string' && url.includes('/clawgic/matches/')) {
      return Promise.resolve(
        mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: matchDetail })
      )
    }
    return Promise.resolve(
      mockResponse({ ok: false, status: 404, statusText: 'Not Found', textBody: 'Not found' })
    )
  })
  globalThis.fetch = mockFetch
}

describe('LiveBattleArenaPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    setupFetch()
    mockRouterPush.mockReset()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('renders loading state initially', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    expect(screen.getByText('Live Battle Arena')).toBeInTheDocument()
    expect(screen.getByText('Loading tournament...')).toBeInTheDocument()
  })

  it('renders tournament header with topic and Live badge', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('AI Ethics Debate')).toBeInTheDocument()
    })
    expect(screen.getByText('Live')).toBeInTheDocument()
    expect(screen.getByText('Live Arena')).toBeInTheDocument()
  })

  it('renders bracket with three match cards', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Bracket')).toBeInTheDocument()
    })
    // Semifinal 1/2 appear in both bracket cards and tournament progress indicator
    expect(screen.getAllByText('Semifinal 1').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Semifinal 2').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Final').length).toBeGreaterThanOrEqual(1)
  })

  it('renders agent names in bracket cards', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getAllByText('AlphaBot').length).toBeGreaterThan(0)
    })
    expect(screen.getAllByText('DeltaBot').length).toBeGreaterThan(0)
    expect(screen.getAllByText('BetaBot').length).toBeGreaterThan(0)
    expect(screen.getAllByText('GammaBot').length).toBeGreaterThan(0)
  })

  it('shows active match status badge as Battling', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getAllByText('Battling').length).toBeGreaterThan(0)
    })
  })

  it('shows scheduled match status badge as Waiting', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getAllByText('Waiting').length).toBeGreaterThan(0)
    })
  })

  it('auto-selects active match and shows side-by-side transcript', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByTestId('battle-view')).toBeInTheDocument()
    })
    expect(screen.getByText('I argue that AI safety is paramount.')).toBeInTheDocument()
    expect(screen.getByText('I counter that AI autonomy drives innovation.')).toBeInTheDocument()
    expect(screen.getByText('Without safety guardrails, innovation is reckless.')).toBeInTheDocument()
  })

  it('shows Generating indicator for in-progress match via BattleView', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Generating...')).toBeInTheDocument()
    })
  })

  it('shows phase progress bar via BattleView', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByTestId('phase-stepper')).toBeInTheDocument()
    })
  })

  it('renders polling indicator when active', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Polling')).toBeInTheDocument()
    })
  })

  it('shows error state on load failure', async () => {
    mockFetch = vi.fn(() =>
      Promise.resolve(
        mockResponse({ ok: false, status: 500, statusText: 'Internal Server Error', textBody: 'Server error' })
      )
    )
    globalThis.fetch = mockFetch

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText(/Failed to load|Server error|500/i)).toBeInTheDocument()
    })
    expect(screen.getByText('Back to Tournament Lobby')).toBeInTheDocument()
  })

  it('renders completed tournament with champion', async () => {
    const completedStatus = {
      ...sampleLiveStatus,
      status: 'COMPLETED',
      activeMatchId: null,
      tournamentWinnerAgentId: 'aaaa-1111',
      matchesCompleted: 3,
      matchesForfeited: 0,
      bracket: sampleLiveStatus.bracket.map((m) => ({
        ...m,
        status: 'COMPLETED',
        winnerAgentId: m.matchId === 'match-sf1' ? 'aaaa-1111' : m.matchId === 'match-sf2' ? 'aaaa-2222' : 'aaaa-1111',
      })),
    }

    const completedMatchDetail = {
      ...sampleMatchDetail,
      status: 'COMPLETED',
      winnerAgentId: 'aaaa-1111',
      agent1EloBefore: 1000,
      agent1EloAfter: 1016,
      agent2EloBefore: 1000,
      agent2EloAfter: 984,
      judgements: [
        {
          judgementId: 'j1',
          matchId: 'match-sf1',
          judgeKey: 'default',
          status: 'ACCEPTED',
          attempt: 1,
          winnerAgentId: 'aaaa-1111',
          agent1LogicScore: 8,
          agent1PersonaAdherenceScore: 7,
          agent1RebuttalStrengthScore: 9,
          agent2LogicScore: 6,
          agent2PersonaAdherenceScore: 7,
          agent2RebuttalStrengthScore: 5,
          reasoning: 'AlphaBot presented stronger arguments.',
        },
      ],
    }

    setupFetch(completedStatus, completedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Champion: AlphaBot')).toBeInTheDocument()
    })
    expect(screen.getByText('Completed')).toBeInTheDocument()
    expect(screen.getByText('View Results')).toBeInTheDocument()
    expect(screen.getByText('Polling stopped')).toBeInTheDocument()
  })

  it('shows winner announcement in match detail for completed match', async () => {
    const completedMatchDetail = {
      ...sampleMatchDetail,
      status: 'COMPLETED',
      winnerAgentId: 'aaaa-1111',
      judgements: [],
    }

    setupFetch(sampleLiveStatus, completedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Winner: AlphaBot')).toBeInTheDocument()
    })
  })

  it('shows judge scores when accepted judgement exists', async () => {
    const completedMatchDetail = {
      ...sampleMatchDetail,
      status: 'COMPLETED',
      winnerAgentId: 'aaaa-1111',
      judgements: [
        {
          judgementId: 'j1',
          matchId: 'match-sf1',
          judgeKey: 'default',
          status: 'ACCEPTED',
          attempt: 1,
          winnerAgentId: 'aaaa-1111',
          agent1LogicScore: 8,
          agent1PersonaAdherenceScore: 7,
          agent1RebuttalStrengthScore: 9,
          agent2LogicScore: 6,
          agent2PersonaAdherenceScore: 7,
          agent2RebuttalStrengthScore: 5,
          reasoning: 'AlphaBot was more convincing.',
        },
      ],
    }

    setupFetch(sampleLiveStatus, completedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Judge Scores')).toBeInTheDocument()
    })
    expect(screen.getByText('AlphaBot was more convincing.')).toBeInTheDocument()
  })

  it('shows Elo impact table for completed match', async () => {
    const completedMatchDetail = {
      ...sampleMatchDetail,
      status: 'COMPLETED',
      winnerAgentId: 'aaaa-1111',
      agent1EloBefore: 1000,
      agent1EloAfter: 1016,
      agent2EloBefore: 1000,
      agent2EloAfter: 984,
      judgements: [],
    }

    setupFetch(sampleLiveStatus, completedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Elo Impact')).toBeInTheDocument()
    })
    expect(screen.getByText('+16')).toBeInTheDocument()
    expect(screen.getByText('-16')).toBeInTheDocument()
  })

  it('shows forfeit reason for forfeited match via BattleView', async () => {
    const forfeitedMatchDetail = {
      ...sampleMatchDetail,
      status: 'FORFEITED',
      forfeitReason: 'PROVIDER_TIMEOUT: agent aaaa-4444 timed out in ARGUMENTATION',
      winnerAgentId: 'aaaa-1111',
      judgements: [],
    }

    setupFetch(sampleLiveStatus, forfeitedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText(/PROVIDER_TIMEOUT/)).toBeInTheDocument()
    })
  })

  it('shows awaiting judge message for pending_judge match via BattleView', async () => {
    const pendingJudgeMatchDetail = {
      ...sampleMatchDetail,
      status: 'PENDING_JUDGE',
      phase: 'CONCLUSION',
      judgements: [],
    }

    setupFetch(sampleLiveStatus, pendingJudgeMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Awaiting judge verdict...')).toBeInTheDocument()
    })
  })

  it('clicking a bracket card selects that match', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getAllByText('Semifinal 2').length).toBeGreaterThanOrEqual(1)
    })

    // Wait for initial match detail load to settle
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/clawgic/matches/match-sf1'),
        expect.anything()
      )
    })

    // Clear fetch spy to isolate the SF2 click's fetch calls
    mockFetch.mockClear()

    const sf2Button = screen.getByRole('button', { name: /Semifinal 2/i })
    await act(async () => {
      fireEvent.click(sf2Button)
    })

    // Should trigger a new match detail fetch for match-sf2
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/clawgic/matches/match-sf2'),
        expect.anything()
      )
    })
  })

  it('shows empty bracket message when no matches exist', async () => {
    const emptyBracketStatus = {
      ...sampleLiveStatus,
      status: 'SCHEDULED',
      activeMatchId: null,
      bracket: [],
    }

    setupFetch(emptyBracketStatus, sampleMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('No matches generated yet.')).toBeInTheDocument()
    })
  })

  it('shows Tournament Lobby link', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Tournament Lobby')).toBeInTheDocument()
    })
  })

  it('renders match not started message for scheduled match via BattleView', async () => {
    const scheduledMatchDetail = {
      ...sampleMatchDetail,
      status: 'SCHEDULED',
      phase: null,
      transcriptJson: [],
      judgements: [],
    }

    setupFetch(sampleLiveStatus, scheduledMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByText('Match has not started yet.')).toBeInTheDocument()
    })
  })

  it('renders winner highlight in bracket card', async () => {
    const completedBracketStatus = {
      ...sampleLiveStatus,
      bracket: [
        {
          ...sampleLiveStatus.bracket[0],
          status: 'COMPLETED',
          winnerAgentId: 'aaaa-1111',
        },
        sampleLiveStatus.bracket[1],
        sampleLiveStatus.bracket[2],
      ],
    }

    setupFetch(completedBracketStatus, sampleMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      // The bracket card for SF1 should show winner label
      const winnerTexts = screen.getAllByText('Winner: AlphaBot')
      expect(winnerTexts.length).toBeGreaterThan(0)
    })
  })

  it('renders MatchStatusBadge with pulsing indicator for in-progress match in bracket', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      const badges = screen.getAllByTestId('match-status-badge')
      const inProgressBadge = badges.find((b) => b.getAttribute('data-status') === 'IN_PROGRESS')
      expect(inProgressBadge).toBeInTheDocument()
      expect(inProgressBadge?.querySelector('.animate-pulse')).toBeInTheDocument()
    })
  })

  it('renders MatchStatusBadge with spinner for pending_judge match in bracket', async () => {
    const pendingBracketStatus = {
      ...sampleLiveStatus,
      bracket: [
        { ...sampleLiveStatus.bracket[0], status: 'PENDING_JUDGE', phase: 'CONCLUSION' },
        sampleLiveStatus.bracket[1],
        sampleLiveStatus.bracket[2],
      ],
    }
    setupFetch(pendingBracketStatus, sampleMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      const badges = screen.getAllByTestId('match-status-badge')
      const pendingBadge = badges.find((b) => b.getAttribute('data-status') === 'PENDING_JUDGE')
      expect(pendingBadge).toBeInTheDocument()
      expect(pendingBadge?.querySelector('.animate-spin')).toBeInTheDocument()
    })
  })

  it('renders MatchStatusBadge with winner name for completed match in detail', async () => {
    const completedMatchDetail = {
      ...sampleMatchDetail,
      status: 'COMPLETED',
      winnerAgentId: 'aaaa-1111',
      judgements: [],
    }

    setupFetch(sampleLiveStatus, completedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      const badges = screen.getAllByTestId('match-status-badge')
      const completedBadge = badges.find((b) => b.getAttribute('data-status') === 'COMPLETED')
      expect(completedBadge).toBeInTheDocument()
      expect(completedBadge).toHaveTextContent('AlphaBot')
    })
  })

  it('renders tournament progress indicator', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      expect(screen.getByTestId('tournament-progress')).toBeInTheDocument()
    })
  })

  it('renders tournament progress with active semifinal 1 stage', async () => {
    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      const stage0 = screen.getByTestId('stage-0')
      expect(stage0.querySelector('.bg-blue-500')).toBeInTheDocument()
    })
  })

  it('renders judge scores section with flash animation class', async () => {
    const completedMatchDetail = {
      ...sampleMatchDetail,
      status: 'COMPLETED',
      winnerAgentId: 'aaaa-1111',
      judgements: [
        {
          judgementId: 'j1',
          matchId: 'match-sf1',
          judgeKey: 'default',
          status: 'ACCEPTED',
          attempt: 1,
          winnerAgentId: 'aaaa-1111',
          agent1LogicScore: 8,
          agent1PersonaAdherenceScore: 7,
          agent1RebuttalStrengthScore: 9,
          agent2LogicScore: 6,
          agent2PersonaAdherenceScore: 7,
          agent2RebuttalStrengthScore: 5,
          reasoning: 'Strong logic.',
        },
      ],
    }
    setupFetch(sampleLiveStatus, completedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      const section = screen.getByTestId('judge-scores-section')
      expect(section).toBeInTheDocument()
      expect(section.className).toContain('judge-scores-flash')
    })
  })

  it('renders bracket advancement class on completed match card', async () => {
    const advancedBracketStatus = {
      ...sampleLiveStatus,
      activeMatchId: 'match-sf2',
      bracket: [
        { ...sampleLiveStatus.bracket[0], status: 'COMPLETED', winnerAgentId: 'aaaa-1111' },
        { ...sampleLiveStatus.bracket[1], status: 'IN_PROGRESS', phase: 'THESIS_DISCOVERY' },
        sampleLiveStatus.bracket[2],
      ],
    }
    setupFetch(advancedBracketStatus, sampleMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)
    await waitFor(() => {
      // The SF1 card (completed, not selected since active is SF2) should have bracket-advance class
      const sf1Button = screen.getByRole('button', { name: /Semifinal 1/i })
      expect(sf1Button.className).toContain('bracket-advance')
    })
  })

  it('shows redirect countdown and navigates to results after tournament completes', async () => {
    const completedLiveStatus = {
      ...sampleLiveStatus,
      status: 'COMPLETED',
      activeMatchId: null,
      tournamentWinnerAgentId: 'aaaa-1111',
      matchesCompleted: 3,
      matchesForfeited: 0,
      bracket: [
        { ...sampleLiveStatus.bracket[0], status: 'COMPLETED', winnerAgentId: 'aaaa-1111' },
        { ...sampleLiveStatus.bracket[1], status: 'COMPLETED', winnerAgentId: 'aaaa-2222' },
        { ...sampleLiveStatus.bracket[2], status: 'COMPLETED', agent1Id: 'aaaa-1111', agent2Id: 'aaaa-2222', winnerAgentId: 'aaaa-1111' },
      ],
    }
    const completedMatchDetail = {
      ...sampleMatchDetail,
      status: 'COMPLETED',
      winnerAgentId: 'aaaa-1111',
      judgements: [],
    }

    setupFetch(completedLiveStatus, completedMatchDetail)

    render(<LiveBattleArenaPage params={paramsPromise} />)

    // Wait for countdown to appear
    await waitFor(() => {
      expect(screen.getByTestId('redirect-countdown')).toBeInTheDocument()
    })
    expect(screen.getByTestId('redirect-countdown')).toHaveTextContent(/Redirecting in \d+s/)

    // Advance timers to trigger redirect
    await act(async () => {
      vi.advanceTimersByTime(6000)
    })

    expect(mockRouterPush).toHaveBeenCalledWith('/clawgic/results')
  })
})
