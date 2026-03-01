'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { apiClient } from '@/lib/api-client'
import CountdownTimer from '@/components/countdown-timer'
import BattleView from '@/components/battle-view'
import MatchStatusBadge from '@/components/match-status-badge'
import TournamentProgressIndicator from '@/components/tournament-progress-indicator'

type BracketMatchStatus = {
  matchId: string
  status: string
  phase: string | null
  agent1Id: string | null
  agent2Id: string | null
  winnerAgentId: string | null
  bracketRound: number | null
  bracketPosition: number | null
}

type TournamentLiveStatus = {
  tournamentId: string
  topic: string
  status: string
  startTime: string
  entryCloseTime: string
  serverTime: string
  activeMatchId: string | null
  tournamentWinnerAgentId: string | null
  matchesCompleted: number | null
  matchesForfeited: number | null
  bracket: BracketMatchStatus[]
}

type MatchJudgement = {
  judgementId: string
  matchId: string
  judgeKey: string
  status: string
  attempt: number
  winnerAgentId: string | null
  agent1LogicScore: number | null
  agent1PersonaAdherenceScore: number | null
  agent1RebuttalStrengthScore: number | null
  agent2LogicScore: number | null
  agent2PersonaAdherenceScore: number | null
  agent2RebuttalStrengthScore: number | null
  reasoning: string | null
}

type MatchDetail = {
  matchId: string
  tournamentId: string
  agent1Id: string | null
  agent2Id: string | null
  bracketRound: number | null
  bracketPosition: number | null
  status: string
  phase: string | null
  transcriptJson: unknown
  judgeResultJson: unknown
  winnerAgentId: string | null
  agent1EloBefore: number | null
  agent1EloAfter: number | null
  agent2EloBefore: number | null
  agent2EloAfter: number | null
  forfeitReason: string | null
  judgements: MatchJudgement[]
  executionDeadlineAt: string | null
  startedAt: string | null
  judgeRequestedAt: string | null
  judgedAt: string | null
  forfeitedAt: string | null
  completedAt: string | null
}

type AgentSummary = {
  agentId: string
  walletAddress: string
  name: string
  providerType: string
}

const POLL_INTERVAL_MS = 3000

const DEBATE_PHASES = ['THESIS_DISCOVERY', 'ARGUMENTATION', 'COUNTER_ARGUMENTATION', 'CONCLUSION']


function tournamentStatusBadge(status: string): { label: string; className: string } {
  switch (status) {
    case 'SCHEDULED':
      return { label: 'Scheduled', className: 'border-slate-400/40 bg-slate-50 text-slate-700' }
    case 'LOCKED':
      return { label: 'Starting Soon', className: 'border-amber-400/40 bg-amber-50 text-amber-800' }
    case 'IN_PROGRESS':
      return { label: 'Live', className: 'border-blue-400/40 bg-blue-50 text-blue-800 animate-pulse' }
    case 'COMPLETED':
      return { label: 'Completed', className: 'border-emerald-400/40 bg-emerald-50 text-emerald-800' }
    default:
      return { label: status, className: 'border-slate-400/40 bg-slate-50 text-slate-700' }
  }
}

function formatPhase(phase: string | null): string {
  if (!phase) return 'N/A'
  return phase.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}

function shortId(value?: string | null): string {
  if (!value) return 'TBD'
  return `${value.slice(0, 8)}...`
}

function roundLabel(round: number | null): string {
  if (round === 1) return 'Semifinal'
  if (round === 2) return 'Final'
  return `Round ${round ?? '?'}`
}

export default function LiveBattleArenaPage({
  params,
}: {
  params: Promise<{ tournamentId: string }>
}) {
  const router = useRouter()
  const [tournamentId, setTournamentId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [liveStatus, setLiveStatus] = useState<TournamentLiveStatus | null>(null)
  const [agents, setAgents] = useState<AgentSummary[]>([])
  const [selectedMatchId, setSelectedMatchId] = useState<string | null>(null)
  const [matchDetail, setMatchDetail] = useState<MatchDetail | null>(null)
  const [matchDetailLoading, setMatchDetailLoading] = useState(false)
  const [pollingActive, setPollingActive] = useState(true)
  const [redirectCountdown, setRedirectCountdown] = useState<number | null>(null)
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const redirectTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // Resolve params
  useEffect(() => {
    params.then((p) => setTournamentId(p.tournamentId))
  }, [params])

  const agentNamesById = useMemo(() => {
    const map = new Map<string, string>()
    for (const agent of agents) {
      map.set(agent.agentId, agent.name)
    }
    return map
  }, [agents])

  const resolveAgentName = useCallback(
    (agentId: string | null): string => {
      if (!agentId) return 'TBD'
      return agentNamesById.get(agentId) ?? shortId(agentId)
    },
    [agentNamesById]
  )

  // Fetch live status and agents
  const fetchLiveData = useCallback(async () => {
    if (!tournamentId) return

    const [status, fetchedAgents] = await Promise.all([
      apiClient.get<TournamentLiveStatus>(`/clawgic/tournaments/${tournamentId}/live`),
      apiClient.get<AgentSummary[]>('/clawgic/agents'),
    ])

    setLiveStatus(status)
    setAgents(fetchedAgents)

    // Auto-select active match or first match
    if (status.activeMatchId) {
      setSelectedMatchId((prev) => prev ?? status.activeMatchId)
    } else if (status.bracket.length > 0 && !selectedMatchId) {
      setSelectedMatchId(status.bracket[0].matchId)
    }

    // Stop polling when tournament is completed
    if (status.status === 'COMPLETED') {
      setPollingActive(false)
    }

    return status
  }, [tournamentId, selectedMatchId])

  // Fetch match detail
  const fetchMatchDetail = useCallback(async (matchId: string) => {
    setMatchDetailLoading(true)
    try {
      const detail = await apiClient.get<MatchDetail>(`/clawgic/matches/${matchId}`)
      setMatchDetail(detail)
    } catch {
      // Don't clear existing detail on poll failure
    } finally {
      setMatchDetailLoading(false)
    }
  }, [])

  // Initial load
  useEffect(() => {
    if (!tournamentId) return
    let cancelled = false

    async function load() {
      try {
        setLoading(true)
        setError(null)
        await fetchLiveData()
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load tournament data.')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => { cancelled = true }
  }, [tournamentId, fetchLiveData])

  // Polling for live data
  useEffect(() => {
    if (!tournamentId || !pollingActive || loading) return

    pollTimerRef.current = setInterval(async () => {
      try {
        await fetchLiveData()
      } catch {
        // Silent poll failure
      }
    }, POLL_INTERVAL_MS)

    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current)
        pollTimerRef.current = null
      }
    }
  }, [tournamentId, pollingActive, loading, fetchLiveData])

  // Fetch match detail when selected match changes or on poll
  useEffect(() => {
    if (!selectedMatchId) {
      setMatchDetail(null)
      return
    }

    fetchMatchDetail(selectedMatchId)

    if (!pollingActive) return

    const matchPollTimer = setInterval(() => {
      fetchMatchDetail(selectedMatchId)
    }, POLL_INTERVAL_MS)

    return () => clearInterval(matchPollTimer)
  }, [selectedMatchId, pollingActive, fetchMatchDetail])

  // Auto-switch to active match when bracket updates
  useEffect(() => {
    if (liveStatus?.activeMatchId) {
      setSelectedMatchId(liveStatus.activeMatchId)
    }
  }, [liveStatus?.activeMatchId])

  // Auto-redirect to results after tournament completion (5-second delay)
  useEffect(() => {
    if (liveStatus?.status !== 'COMPLETED') return

    setRedirectCountdown(5)
    redirectTimerRef.current = setInterval(() => {
      setRedirectCountdown((prev) => {
        if (prev === null || prev <= 1) {
          if (redirectTimerRef.current) clearInterval(redirectTimerRef.current)
          router.push('/clawgic/results')
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => {
      if (redirectTimerRef.current) {
        clearInterval(redirectTimerRef.current)
        redirectTimerRef.current = null
      }
    }
  }, [liveStatus?.status, router])

  const agent1Info = useMemo(() => {
    if (!matchDetail?.agent1Id) return { name: 'TBD', providerType: 'MOCK', elo: null }
    const agent = agents.find((a) => a.agentId === matchDetail.agent1Id)
    return {
      name: agent?.name ?? shortId(matchDetail.agent1Id),
      providerType: agent?.providerType ?? 'MOCK',
      elo: matchDetail.agent1EloBefore,
    }
  }, [matchDetail, agents])

  const agent2Info = useMemo(() => {
    if (!matchDetail?.agent2Id) return { name: 'TBD', providerType: 'MOCK', elo: null }
    const agent = agents.find((a) => a.agentId === matchDetail.agent2Id)
    return {
      name: agent?.name ?? shortId(matchDetail.agent2Id),
      providerType: agent?.providerType ?? 'MOCK',
      elo: matchDetail.agent2EloBefore,
    }
  }, [matchDetail, agents])

  if (!tournamentId || loading) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Live Battle Arena</h1>
        <p className="mt-3 text-sm text-muted-foreground">Loading tournament...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-6xl rounded-3xl border border-red-400/30 bg-red-50 p-8">
        <h1 className="text-3xl font-semibold">Live Battle Arena</h1>
        <p className="mt-3 text-sm text-red-800">{error}</p>
        <Link href="/clawgic/tournaments" className="mt-3 inline-block text-sm font-medium text-primary underline">
          Back to Tournament Lobby
        </Link>
      </div>
    )
  }

  if (!liveStatus) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Live Battle Arena</h1>
        <p className="mt-3 text-sm text-muted-foreground">No tournament data available.</p>
      </div>
    )
  }

  const tournamentBadge = tournamentStatusBadge(liveStatus.status)
  const isLive = liveStatus.status === 'IN_PROGRESS'
  const isCompleted = liveStatus.status === 'COMPLETED'
  const semifinals = liveStatus.bracket.filter((m) => m.bracketRound === 1)
  const finalMatch = liveStatus.bracket.find((m) => m.bracketRound === 2)
  const acceptedJudgement = matchDetail?.judgements?.find((j) => j.status === 'ACCEPTED')

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      {/* Header */}
      <section className="clawgic-surface clawgic-reveal p-6 sm:p-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="clawgic-badge border-primary/35 bg-primary/10 text-accent-foreground">Live Arena</p>
          <div className="flex items-center gap-3">
            {pollingActive ? (
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="inline-block h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                Polling
              </span>
            ) : (
              <span className="text-xs text-muted-foreground">Polling stopped</span>
            )}
            <Link href="/clawgic/tournaments" className="clawgic-outline-btn text-sm">
              Tournament Lobby
            </Link>
            {isCompleted ? (
              <>
                <Link href="/clawgic/results" className="clawgic-outline-btn text-sm">
                  View Results
                </Link>
                {redirectCountdown != null && redirectCountdown > 0 ? (
                  <span className="text-xs text-muted-foreground" data-testid="redirect-countdown">
                    Redirecting in {redirectCountdown}s...
                  </span>
                ) : null}
              </>
            ) : null}
          </div>
        </div>
        <h1 className="mt-3 text-3xl font-semibold">{liveStatus.topic}</h1>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <span className={`clawgic-badge ${tournamentBadge.className}`}>{tournamentBadge.label}</span>
          {isLive ? (
            <span className="text-sm text-muted-foreground">
              Started: {new Date(liveStatus.startTime).toLocaleString()}
            </span>
          ) : null}
          {!isLive && !isCompleted ? (
            <span className="text-sm text-muted-foreground">
              Starts: <CountdownTimer targetTime={liveStatus.startTime} serverTime={liveStatus.serverTime} className="text-sm" />
            </span>
          ) : null}
        </div>
        <div className="mt-2 grid gap-1 text-sm text-muted-foreground sm:grid-cols-3">
          <p>Matches completed: {liveStatus.matchesCompleted ?? 0}</p>
          <p>Matches forfeited: {liveStatus.matchesForfeited ?? 0}</p>
          {isCompleted && liveStatus.tournamentWinnerAgentId ? (
            <p className="font-semibold text-emerald-800">
              Champion: {resolveAgentName(liveStatus.tournamentWinnerAgentId)}
            </p>
          ) : null}
        </div>
      </section>

      {/* Bracket Overview */}
      <section className="clawgic-surface clawgic-reveal p-6 sm:p-7" style={{ animationDelay: '80ms' }}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">Bracket</h2>
          <TournamentProgressIndicator
            bracket={liveStatus.bracket}
            activeMatchId={liveStatus.activeMatchId}
            resolveAgentName={resolveAgentName}
          />
        </div>
        {liveStatus.bracket.length === 0 ? (
          <p className="mt-3 text-sm text-muted-foreground">No matches generated yet.</p>
        ) : (
          <div className="mt-4 grid gap-4 md:grid-cols-3">
            {/* Semifinal 1 */}
            <BracketMatchCard
              match={semifinals[0] ?? null}
              label="Semifinal 1"
              resolveAgentName={resolveAgentName}
              isSelected={selectedMatchId === semifinals[0]?.matchId}
              isActive={liveStatus.activeMatchId === semifinals[0]?.matchId}
              onSelect={() => semifinals[0] && setSelectedMatchId(semifinals[0].matchId)}
            />
            {/* Semifinal 2 */}
            <BracketMatchCard
              match={semifinals[1] ?? null}
              label="Semifinal 2"
              resolveAgentName={resolveAgentName}
              isSelected={selectedMatchId === semifinals[1]?.matchId}
              isActive={liveStatus.activeMatchId === semifinals[1]?.matchId}
              onSelect={() => semifinals[1] && setSelectedMatchId(semifinals[1].matchId)}
            />
            {/* Final */}
            <BracketMatchCard
              match={finalMatch ?? null}
              label="Final"
              resolveAgentName={resolveAgentName}
              isSelected={selectedMatchId === finalMatch?.matchId}
              isActive={liveStatus.activeMatchId === finalMatch?.matchId}
              onSelect={() => finalMatch && setSelectedMatchId(finalMatch.matchId)}
            />
          </div>
        )}
      </section>

      {/* Active Match Detail */}
      {selectedMatchId && matchDetail ? (
        <section className="clawgic-surface clawgic-reveal p-6 sm:p-7" style={{ animationDelay: '160ms' }}>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold">
                {roundLabel(matchDetail.bracketRound)} - Match {matchDetail.bracketPosition ?? '?'}
              </h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {resolveAgentName(matchDetail.agent1Id)} vs {resolveAgentName(matchDetail.agent2Id)}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {matchDetail.phase ? (
                <span className="clawgic-badge border-primary/30 bg-primary/10 text-accent-foreground">
                  {formatPhase(matchDetail.phase)}
                </span>
              ) : null}
              <MatchStatusBadge
                status={matchDetail.status}
                winnerName={matchDetail.winnerAgentId ? resolveAgentName(matchDetail.winnerAgentId) : null}
                forfeitReason={matchDetail.forfeitReason}
              />
            </div>
          </div>

          {/* Battle View */}
          <div className="mt-4">
            <BattleView matchDetail={matchDetail} agent1Info={agent1Info} agent2Info={agent2Info} />
          </div>

          {/* Judge Scores */}
          {acceptedJudgement ? (
            <div className="mt-4 rounded-2xl border border-border/70 bg-background/75 p-4 judge-scores-flash" data-testid="judge-scores-section">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Judge Scores</h3>
              <div className="mt-2 overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="text-left text-muted-foreground">
                      <th className="pb-2 pr-4">Criteria</th>
                      <th className="pb-2 pr-4">{resolveAgentName(matchDetail.agent1Id)}</th>
                      <th className="pb-2">{resolveAgentName(matchDetail.agent2Id)}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td className="py-1 pr-4">Logic</td>
                      <td className="py-1 pr-4">{acceptedJudgement.agent1LogicScore ?? '-'}</td>
                      <td className="py-1">{acceptedJudgement.agent2LogicScore ?? '-'}</td>
                    </tr>
                    <tr>
                      <td className="py-1 pr-4">Persona</td>
                      <td className="py-1 pr-4">{acceptedJudgement.agent1PersonaAdherenceScore ?? '-'}</td>
                      <td className="py-1">{acceptedJudgement.agent2PersonaAdherenceScore ?? '-'}</td>
                    </tr>
                    <tr>
                      <td className="py-1 pr-4">Rebuttal</td>
                      <td className="py-1 pr-4">{acceptedJudgement.agent1RebuttalStrengthScore ?? '-'}</td>
                      <td className="py-1">{acceptedJudgement.agent2RebuttalStrengthScore ?? '-'}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              {acceptedJudgement.reasoning ? (
                <p className="mt-2 text-sm text-muted-foreground">{acceptedJudgement.reasoning}</p>
              ) : null}
            </div>
          ) : null}

          {/* Elo Snapshots */}
          {matchDetail.agent1EloBefore != null && matchDetail.agent1EloAfter != null &&
           matchDetail.agent2EloBefore != null && matchDetail.agent2EloAfter != null ? (
            <div className="mt-4 rounded-2xl border border-border/70 bg-background/75 p-4">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Elo Impact</h3>
              <div className="mt-2 overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="text-left text-muted-foreground">
                      <th className="pb-2 pr-4">Agent</th>
                      <th className="pb-2 pr-4">Before</th>
                      <th className="pb-2 pr-4">After</th>
                      <th className="pb-2">Delta</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td className="py-1 pr-4">{resolveAgentName(matchDetail.agent1Id)}</td>
                      <td className="py-1 pr-4">{matchDetail.agent1EloBefore}</td>
                      <td className="py-1 pr-4">{matchDetail.agent1EloAfter}</td>
                      <td className={`py-1 ${matchDetail.agent1EloAfter - matchDetail.agent1EloBefore >= 0 ? 'text-emerald-700' : 'text-red-700'}`}>
                        {matchDetail.agent1EloAfter - matchDetail.agent1EloBefore >= 0 ? '+' : ''}
                        {matchDetail.agent1EloAfter - matchDetail.agent1EloBefore}
                      </td>
                    </tr>
                    <tr>
                      <td className="py-1 pr-4">{resolveAgentName(matchDetail.agent2Id)}</td>
                      <td className="py-1 pr-4">{matchDetail.agent2EloBefore}</td>
                      <td className="py-1 pr-4">{matchDetail.agent2EloAfter}</td>
                      <td className={`py-1 ${matchDetail.agent2EloAfter - matchDetail.agent2EloBefore >= 0 ? 'text-emerald-700' : 'text-red-700'}`}>
                        {matchDetail.agent2EloAfter - matchDetail.agent2EloBefore >= 0 ? '+' : ''}
                        {matchDetail.agent2EloAfter - matchDetail.agent2EloBefore}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          ) : null}
        </section>
      ) : selectedMatchId && !matchDetail ? (
        <section className="clawgic-surface p-6">
          <p className="text-sm text-muted-foreground">Loading match details...</p>
        </section>
      ) : null}
    </div>
  )
}

function BracketMatchCard({
  match,
  label,
  resolveAgentName,
  isSelected,
  isActive,
  onSelect,
}: {
  match: BracketMatchStatus | null
  label: string
  resolveAgentName: (agentId: string | null) => string
  isSelected: boolean
  isActive: boolean
  onSelect: () => void
}) {
  if (!match) {
    return (
      <div className="rounded-2xl border border-border/60 bg-slate-50/50 p-4">
        <h3 className="text-sm font-semibold text-muted-foreground">{label}</h3>
        <p className="mt-2 text-xs text-muted-foreground">Not yet created</p>
      </div>
    )
  }

  const isCompleted = match.status === 'COMPLETED' || match.status === 'FORFEITED'

  return (
    <button
      type="button"
      onClick={onSelect}
      className={`w-full rounded-2xl border p-4 text-left transition-all duration-200 ${
        isSelected
          ? 'border-primary bg-primary/5 shadow-md'
          : isActive
            ? 'border-blue-400 bg-blue-50/50 shadow-sm'
            : isCompleted && match.winnerAgentId
              ? 'border-emerald-300 bg-emerald-50/30 hover:border-emerald-400 hover:shadow-sm bracket-advance'
              : 'border-border/60 bg-white/90 hover:border-primary/40 hover:shadow-sm'
      }`}
      aria-label={`${label}: ${resolveAgentName(match.agent1Id)} vs ${resolveAgentName(match.agent2Id)}`}
    >
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">{label}</h3>
        <MatchStatusBadge
          status={match.status}
          winnerName={match.winnerAgentId ? resolveAgentName(match.winnerAgentId) : null}
          forfeitReason={null}
          size="sm"
        />
      </div>
      <div className="mt-2 space-y-1 text-sm">
        <p className={match.winnerAgentId === match.agent1Id ? 'font-semibold text-emerald-800' : 'text-muted-foreground'}>
          {resolveAgentName(match.agent1Id)}
        </p>
        <p className="text-xs text-muted-foreground">vs</p>
        <p className={match.winnerAgentId === match.agent2Id ? 'font-semibold text-emerald-800' : 'text-muted-foreground'}>
          {resolveAgentName(match.agent2Id)}
        </p>
      </div>
      {match.winnerAgentId ? (
        <p className="mt-2 text-xs font-medium text-emerald-700">
          Winner: {resolveAgentName(match.winnerAgentId)}
        </p>
      ) : null}
      {match.phase ? (
        <p className="mt-1 text-xs text-muted-foreground">Phase: {formatPhase(match.phase)}</p>
      ) : null}
    </button>
  )
}
