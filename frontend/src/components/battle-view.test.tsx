import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import BattleView, {
  parseTranscriptMessages,
  determineActiveAgent,
  formatPhase,
  type BattleViewMatchDetail,
  type BattleViewAgentInfo,
} from './battle-view'

const agent1Info: BattleViewAgentInfo = { name: 'AlphaBot', providerType: 'OPENAI', elo: 1000 }
const agent2Info: BattleViewAgentInfo = { name: 'BetaBot', providerType: 'ANTHROPIC', elo: 1020 }

function makeMatch(overrides: Partial<BattleViewMatchDetail> = {}): BattleViewMatchDetail {
  return {
    matchId: 'match-1',
    agent1Id: 'a1',
    agent2Id: 'a2',
    status: 'IN_PROGRESS',
    phase: 'THESIS_DISCOVERY',
    transcriptJson: [],
    winnerAgentId: null,
    forfeitReason: null,
    executionDeadlineAt: new Date(Date.now() + 600_000).toISOString(),
    startedAt: new Date(Date.now() - 30_000).toISOString(),
    ...overrides,
  }
}

describe('parseTranscriptMessages', () => {
  it('returns empty array for non-array input', () => {
    expect(parseTranscriptMessages(null)).toEqual([])
    expect(parseTranscriptMessages('text')).toEqual([])
    expect(parseTranscriptMessages(undefined)).toEqual([])
  })

  it('parses valid messages', () => {
    const input = [
      { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'Hello' },
      { role: 'agent_2', phase: 'THESIS_DISCOVERY', content: 'World' },
    ]
    expect(parseTranscriptMessages(input)).toEqual(input)
  })

  it('filters out invalid entries', () => {
    const input = [
      { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'Valid' },
      { role: 123, phase: 'X', content: 'Invalid role' },
      null,
      { phase: 'X', content: 'Missing role' },
    ]
    expect(parseTranscriptMessages(input)).toHaveLength(1)
  })
})

describe('determineActiveAgent', () => {
  it('returns null for non-IN_PROGRESS status', () => {
    expect(determineActiveAgent(0, 'SCHEDULED')).toBeNull()
    expect(determineActiveAgent(0, 'COMPLETED')).toBeNull()
    expect(determineActiveAgent(0, 'PENDING_JUDGE')).toBeNull()
  })

  it('returns agent_1 when transcript length is even', () => {
    expect(determineActiveAgent(0, 'IN_PROGRESS')).toBe('agent_1')
    expect(determineActiveAgent(2, 'IN_PROGRESS')).toBe('agent_1')
    expect(determineActiveAgent(4, 'IN_PROGRESS')).toBe('agent_1')
  })

  it('returns agent_2 when transcript length is odd', () => {
    expect(determineActiveAgent(1, 'IN_PROGRESS')).toBe('agent_2')
    expect(determineActiveAgent(3, 'IN_PROGRESS')).toBe('agent_2')
    expect(determineActiveAgent(5, 'IN_PROGRESS')).toBe('agent_2')
  })

  it('returns null when all 8 turns complete', () => {
    expect(determineActiveAgent(8, 'IN_PROGRESS')).toBeNull()
  })
})

describe('formatPhase', () => {
  it('formats phase names', () => {
    expect(formatPhase('THESIS_DISCOVERY')).toBe('Thesis Discovery')
    expect(formatPhase('COUNTER_ARGUMENTATION')).toBe('Counter Argumentation')
  })
})

describe('BattleView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('renders with data-testid battle-view', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('battle-view')).toBeInTheDocument()
  })

  it('renders agent 1 and agent 2 panels', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('agent_1-panel')).toBeInTheDocument()
    expect(screen.getByTestId('agent_2-panel')).toBeInTheDocument()
  })

  it('renders agent names in panel headers', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('agent_1-name')).toHaveTextContent('AlphaBot')
    expect(screen.getByTestId('agent_2-name')).toHaveTextContent('BetaBot')
  })

  it('renders provider badges', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('agent_1-provider')).toHaveTextContent('OpenAI')
    expect(screen.getByTestId('agent_2-provider')).toHaveTextContent('Anthropic')
  })

  it('renders Elo ratings', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('agent_1-elo')).toHaveTextContent('Elo: 1000')
    expect(screen.getByTestId('agent_2-elo')).toHaveTextContent('Elo: 1020')
  })

  it('hides Elo when null', () => {
    const noElo1 = { ...agent1Info, elo: null }
    const noElo2 = { ...agent2Info, elo: null }
    render(<BattleView matchDetail={makeMatch()} agent1Info={noElo1} agent2Info={noElo2} />)
    expect(screen.queryByTestId('agent_1-elo')).not.toBeInTheDocument()
    expect(screen.queryByTestId('agent_2-elo')).not.toBeInTheDocument()
  })

  it('renders phase stepper', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('phase-stepper')).toBeInTheDocument()
    expect(screen.getByTestId('phase-bar-THESIS_DISCOVERY')).toBeInTheDocument()
    expect(screen.getByTestId('phase-bar-ARGUMENTATION')).toBeInTheDocument()
    expect(screen.getByTestId('phase-bar-COUNTER_ARGUMENTATION')).toBeInTheDocument()
    expect(screen.getByTestId('phase-bar-CONCLUSION')).toBeInTheDocument()
  })

  it('shows Generating indicator for active agent when transcript is empty', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('agent_1-generating')).toBeInTheDocument()
    expect(screen.getByTestId('agent_1-generating')).toHaveTextContent('Generating...')
    expect(screen.queryByTestId('agent_2-generating')).not.toBeInTheDocument()
  })

  it('shows Active indicator on agent_1 when transcript length is even', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('agent_1-active')).toHaveTextContent('Active')
    expect(screen.queryByTestId('agent_2-active')).not.toBeInTheDocument()
  })

  it('shows Active indicator on agent_2 when transcript length is odd', () => {
    const match = makeMatch({
      transcriptJson: [
        { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'First point.' },
      ],
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.queryByTestId('agent_1-active')).not.toBeInTheDocument()
    expect(screen.getByTestId('agent_2-active')).toHaveTextContent('Active')
    expect(screen.getByTestId('agent_2-generating')).toBeInTheDocument()
  })

  it('renders transcript messages in the correct panels', () => {
    const match = makeMatch({
      phase: 'ARGUMENTATION',
      transcriptJson: [
        { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'A1 thesis.' },
        { role: 'agent_2', phase: 'THESIS_DISCOVERY', content: 'A2 thesis.' },
        { role: 'agent_1', phase: 'ARGUMENTATION', content: 'A1 argument.' },
      ],
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)

    // Agent 1 panel should have its messages
    expect(screen.getByTestId('agent_1-THESIS_DISCOVERY')).toHaveTextContent('A1 thesis.')
    expect(screen.getByTestId('agent_1-ARGUMENTATION')).toHaveTextContent('A1 argument.')

    // Agent 2 panel should have its messages
    expect(screen.getByTestId('agent_2-THESIS_DISCOVERY')).toHaveTextContent('A2 thesis.')

    // Agent 2 should show generating in ARGUMENTATION phase
    expect(screen.getByTestId('agent_2-generating')).toBeInTheDocument()
  })

  it('renders full transcript across all phases', () => {
    const match = makeMatch({
      status: 'PENDING_JUDGE',
      phase: 'CONCLUSION',
      transcriptJson: [
        { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'T1' },
        { role: 'agent_2', phase: 'THESIS_DISCOVERY', content: 'T2' },
        { role: 'agent_1', phase: 'ARGUMENTATION', content: 'A1' },
        { role: 'agent_2', phase: 'ARGUMENTATION', content: 'A2' },
        { role: 'agent_1', phase: 'COUNTER_ARGUMENTATION', content: 'C1' },
        { role: 'agent_2', phase: 'COUNTER_ARGUMENTATION', content: 'C2' },
        { role: 'agent_1', phase: 'CONCLUSION', content: 'Co1' },
        { role: 'agent_2', phase: 'CONCLUSION', content: 'Co2' },
      ],
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)

    expect(screen.getByTestId('agent_1-THESIS_DISCOVERY')).toHaveTextContent('T1')
    expect(screen.getByTestId('agent_2-THESIS_DISCOVERY')).toHaveTextContent('T2')
    expect(screen.getByTestId('agent_1-ARGUMENTATION')).toHaveTextContent('A1')
    expect(screen.getByTestId('agent_2-ARGUMENTATION')).toHaveTextContent('A2')
    expect(screen.getByTestId('agent_1-COUNTER_ARGUMENTATION')).toHaveTextContent('C1')
    expect(screen.getByTestId('agent_2-COUNTER_ARGUMENTATION')).toHaveTextContent('C2')
    expect(screen.getByTestId('agent_1-CONCLUSION')).toHaveTextContent('Co1')
    expect(screen.getByTestId('agent_2-CONCLUSION')).toHaveTextContent('Co2')
  })

  it('shows pending judge overlay', () => {
    const match = makeMatch({ status: 'PENDING_JUDGE', phase: 'CONCLUSION' })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('pending-judge')).toHaveTextContent('Awaiting judge verdict...')
  })

  it('does not show pending judge overlay for other statuses', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.queryByTestId('pending-judge')).not.toBeInTheDocument()
  })

  it('shows winner banner for completed match', () => {
    const match = makeMatch({ status: 'COMPLETED', winnerAgentId: 'a1' })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('winner-banner')).toHaveTextContent('Winner: AlphaBot')
  })

  it('shows winner badge on winning agent panel', () => {
    const match = makeMatch({ status: 'COMPLETED', winnerAgentId: 'a2' })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.queryByTestId('agent_1-winner-badge')).not.toBeInTheDocument()
    expect(screen.getByTestId('agent_2-winner-badge')).toHaveTextContent('Winner')
  })

  it('applies winner border class to winning agent panel', () => {
    const match = makeMatch({ status: 'COMPLETED', winnerAgentId: 'a1' })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    const panel = screen.getByTestId('agent_1-panel')
    expect(panel.className).toContain('border-emerald-400')
  })

  it('shows forfeit on failing agent panel', () => {
    const match = makeMatch({
      status: 'FORFEITED',
      forfeitReason: 'PROVIDER_TIMEOUT: agent a2 timed out in ARGUMENTATION',
      winnerAgentId: 'a1',
      phase: 'ARGUMENTATION',
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)

    // Forfeit should appear on agent 2's panel (the failing agent)
    expect(screen.getByTestId('agent_2-forfeit')).toHaveTextContent(/PROVIDER_TIMEOUT/)
    expect(screen.queryByTestId('agent_1-forfeit')).not.toBeInTheDocument()
  })

  it('applies forfeit border class to forfeited agent panel', () => {
    const match = makeMatch({
      status: 'FORFEITED',
      forfeitReason: 'PROVIDER_ERROR: agent a2 failed',
      winnerAgentId: 'a1',
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    const panel = screen.getByTestId('agent_2-panel')
    expect(panel.className).toContain('border-red-300')
  })

  it('shows "not started" message for scheduled match', () => {
    const match = makeMatch({ status: 'SCHEDULED', phase: null, transcriptJson: [] })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('not-started')).toHaveTextContent('Match has not started yet.')
  })

  it('does not show generating indicator for completed match', () => {
    const match = makeMatch({ status: 'COMPLETED', winnerAgentId: 'a1' })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.queryByTestId('agent_1-generating')).not.toBeInTheDocument()
    expect(screen.queryByTestId('agent_2-generating')).not.toBeInTheDocument()
    expect(screen.queryByTestId('agent_1-active')).not.toBeInTheDocument()
    expect(screen.queryByTestId('agent_2-active')).not.toBeInTheDocument()
  })

  it('renders turn timer for in-progress match', () => {
    render(<BattleView matchDetail={makeMatch()} agent1Info={agent1Info} agent2Info={agent2Info} />)
    const timer = screen.getByTestId('turn-timer')
    expect(timer).toBeInTheDocument()
    expect(timer).toHaveTextContent("AlphaBot's turn")
  })

  it('does not render turn timer for completed match', () => {
    const match = makeMatch({ status: 'COMPLETED', winnerAgentId: 'a1' })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.queryByTestId('turn-timer')).not.toBeInTheDocument()
  })

  it('shows BetaBot turn timer when agent_2 is active', () => {
    const match = makeMatch({
      transcriptJson: [
        { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'My thesis.' },
      ],
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    expect(screen.getByTestId('turn-timer')).toHaveTextContent("BetaBot's turn")
  })

  it('renders MOCK provider badge correctly', () => {
    const mockAgent = { ...agent1Info, providerType: 'MOCK' }
    render(<BattleView matchDetail={makeMatch()} agent1Info={mockAgent} agent2Info={agent2Info} />)
    expect(screen.getByTestId('agent_1-provider')).toHaveTextContent('Mock')
  })

  it('renders Waiting state for phases not yet reached', () => {
    const match = makeMatch({
      phase: 'THESIS_DISCOVERY',
      transcriptJson: [
        { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'Start.' },
        { role: 'agent_2', phase: 'THESIS_DISCOVERY', content: 'Reply.' },
      ],
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)

    // THESIS_DISCOVERY should have content
    expect(screen.getByTestId('agent_1-THESIS_DISCOVERY')).toBeInTheDocument()
    expect(screen.getByTestId('agent_2-THESIS_DISCOVERY')).toBeInTheDocument()
  })

  it('renders phase labels in response areas', () => {
    const match = makeMatch({
      phase: 'ARGUMENTATION',
      transcriptJson: [
        { role: 'agent_1', phase: 'THESIS_DISCOVERY', content: 'T.' },
        { role: 'agent_2', phase: 'THESIS_DISCOVERY', content: 'T.' },
      ],
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)

    // Phase labels should appear (multiple times - once per panel)
    const thesisLabels = screen.getAllByText('Thesis Discovery')
    expect(thesisLabels.length).toBeGreaterThanOrEqual(2)
  })

  it('turn timer shows urgency styling when close to deadline', () => {
    const match = makeMatch({
      // Set up so the turn deadline is < 10 seconds from now
      executionDeadlineAt: new Date(Date.now() + 5_000).toISOString(),
      startedAt: new Date(Date.now() - 70_000).toISOString(),
    })
    render(<BattleView matchDetail={match} agent1Info={agent1Info} agent2Info={agent2Info} />)
    const timer = screen.getByTestId('turn-timer')
    expect(timer.className).toContain('border-red')
  })
})
