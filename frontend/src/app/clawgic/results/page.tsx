'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '@/lib/api-client'

type ClawgicTournamentSummary = {
  tournamentId: string
  topic: string
  status: string
  bracketSize: number
  maxEntries: number
  startTime: string
  entryCloseTime: string
  baseEntryFeeUsdc: number | string
  winnerAgentId?: string | null
  matchesCompleted?: number | null
  matchesForfeited?: number | null
}

type ClawgicTournamentDetail = {
  tournamentId: string
  topic: string
  status: string
  bracketSize: number
  maxEntries: number
  startTime: string
  entryCloseTime: string
  baseEntryFeeUsdc: number | string
  winnerAgentId?: string | null
  matchesCompleted?: number | null
  matchesForfeited?: number | null
  startedAt?: string | null
  completedAt?: string | null
}

type ClawgicTournamentEntry = {
  entryId: string
  tournamentId: string
  agentId: string
  walletAddress: string
  status: string
  seedPosition?: number | null
  seedSnapshotElo?: number | null
}

type ClawgicMatchJudgement = {
  judgementId: string
  matchId: string
  judgeKey: string
  judgeModel?: string | null
  status: string
  attempt: number
  resultJson: unknown
  winnerAgentId?: string | null
  agent1LogicScore?: number | null
  agent1PersonaAdherenceScore?: number | null
  agent1RebuttalStrengthScore?: number | null
  agent2LogicScore?: number | null
  agent2PersonaAdherenceScore?: number | null
  agent2RebuttalStrengthScore?: number | null
  reasoning?: string | null
  judgedAt?: string | null
}

type ClawgicMatchDetail = {
  matchId: string
  tournamentId: string
  agent1Id?: string | null
  agent2Id?: string | null
  bracketRound?: number | null
  bracketPosition?: number | null
  status: string
  phase?: string | null
  transcriptJson: unknown
  judgeResultJson: unknown
  winnerAgentId?: string | null
  agent1EloBefore?: number | null
  agent1EloAfter?: number | null
  agent2EloBefore?: number | null
  agent2EloAfter?: number | null
  forfeitReason?: string | null
  judgements: ClawgicMatchJudgement[]
}

type ClawgicTournamentResults = {
  tournament: ClawgicTournamentDetail
  entries: ClawgicTournamentEntry[]
  matches: ClawgicMatchDetail[]
}

type ClawgicAgentSummary = {
  agentId: string
  walletAddress: string
  name: string
}

type TranscriptMessage = {
  role: string
  phase: string
  content: string
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return 'N/A'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

function formatUsdc(value: number | string): string {
  const numericValue = typeof value === 'string' ? Number.parseFloat(value) : value
  if (Number.isNaN(numericValue)) {
    return String(value)
  }
  return `${numericValue.toFixed(2)} USDC`
}

function shortId(value?: string | null): string {
  if (!value) {
    return 'N/A'
  }
  return `${value.slice(0, 8)}...${value.slice(-4)}`
}

function formatRole(role: string): string {
  switch (role) {
    case 'agent_1':
      return 'Agent 1'
    case 'agent_2':
      return 'Agent 2'
    case 'judge':
      return 'Judge'
    default:
      return role
  }
}

function statusBadgeClass(status: string): string {
  if (status === 'COMPLETED') {
    return 'border-emerald-500/40 bg-emerald-50 text-emerald-800'
  }
  if (status === 'FORFEITED') {
    return 'border-amber-500/45 bg-amber-50 text-amber-900'
  }
  if (status === 'PENDING_JUDGE') {
    return 'border-blue-500/40 bg-blue-50 text-blue-900'
  }
  return 'border-primary/30 bg-primary/10 text-accent-foreground'
}

function parseTranscriptMessages(value: unknown): TranscriptMessage[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object') {
      return []
    }

    const candidate = item as Record<string, unknown>
    if (
      typeof candidate.role !== 'string' ||
      typeof candidate.phase !== 'string' ||
      typeof candidate.content !== 'string'
    ) {
      return []
    }

    return [
      {
        role: candidate.role,
        phase: candidate.phase,
        content: candidate.content,
      },
    ]
  })
}

function parseWinnerIdFromJudgeResult(value: unknown): string | null {
  if (!value || typeof value !== 'object') {
    return null
  }
  const candidate = value as Record<string, unknown>
  return typeof candidate.winner_id === 'string' ? candidate.winner_id : null
}

function isNumeric(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export default function ClawgicResultsPage() {
  const [loadingIndex, setLoadingIndex] = useState(true)
  const [indexError, setIndexError] = useState<string | null>(null)
  const [tournaments, setTournaments] = useState<ClawgicTournamentSummary[]>([])
  const [agents, setAgents] = useState<ClawgicAgentSummary[]>([])
  const [selectedTournamentId, setSelectedTournamentId] = useState<string>('')

  const [loadingResults, setLoadingResults] = useState(false)
  const [resultsError, setResultsError] = useState<string | null>(null)
  const [resultsPayload, setResultsPayload] = useState<ClawgicTournamentResults | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadIndex() {
      try {
        setLoadingIndex(true)
        setIndexError(null)

        const [fetchedTournaments, fetchedAgents] = await Promise.all([
          apiClient.get<ClawgicTournamentSummary[]>('/clawgic/tournaments/results'),
          apiClient.get<ClawgicAgentSummary[]>('/clawgic/agents'),
        ])

        if (cancelled) {
          return
        }

        setTournaments(fetchedTournaments)
        setAgents(fetchedAgents)
        if (fetchedTournaments.length > 0) {
          setSelectedTournamentId((previous) => previous || fetchedTournaments[0].tournamentId)
        }
      } catch (error) {
        if (!cancelled) {
          setIndexError(error instanceof Error ? error.message : 'Failed to load tournament results index.')
        }
      } finally {
        if (!cancelled) {
          setLoadingIndex(false)
        }
      }
    }

    loadIndex()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!selectedTournamentId) {
      setResultsPayload(null)
      return
    }

    let cancelled = false

    async function loadTournamentResults() {
      try {
        setLoadingResults(true)
        setResultsError(null)
        const payload = await apiClient.get<ClawgicTournamentResults>(
          `/clawgic/tournaments/${selectedTournamentId}/results`
        )
        if (!cancelled) {
          setResultsPayload(payload)
        }
      } catch (error) {
        if (!cancelled) {
          setResultsError(error instanceof Error ? error.message : 'Failed to load tournament result details.')
          setResultsPayload(null)
        }
      } finally {
        if (!cancelled) {
          setLoadingResults(false)
        }
      }
    }

    loadTournamentResults()

    return () => {
      cancelled = true
    }
  }, [selectedTournamentId])

  const agentNamesById = useMemo(() => {
    const map = new Map<string, string>()
    for (const agent of agents) {
      map.set(agent.agentId, agent.name)
    }
    return map
  }, [agents])

  const selectedSummary = tournaments.find((item) => item.tournamentId === selectedTournamentId) || null

  function resolveAgentLabel(agentId?: string | null): string {
    if (!agentId) {
      return 'TBD'
    }
    return agentNamesById.get(agentId) || shortId(agentId)
  }

  if (loadingIndex) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Tournament Results</h1>
        <p className="mt-3 text-sm text-muted-foreground">Loading tournament results index...</p>
      </div>
    )
  }

  if (indexError) {
    return (
      <div className="mx-auto max-w-6xl rounded-3xl border border-red-400/30 bg-red-50 p-8">
        <h1 className="text-3xl font-semibold">Tournament Results</h1>
        <p className="mt-3 text-sm text-red-800">Failed to load tournament results index.</p>
        <p className="mt-2 text-sm text-muted-foreground">{indexError}</p>
      </div>
    )
  }

  if (tournaments.length === 0) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Tournament Results</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          No tournaments are available yet. Create and run a tournament from the lobby first.
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <section className="clawgic-surface clawgic-reveal p-6 sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="clawgic-badge border-primary/35 bg-primary/10 text-accent-foreground">Clawgic Results</p>
            <h1 className="mt-3 text-3xl font-semibold">Tournament Results</h1>
            <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
              Review bracket status, transcripts, judge outputs, and Elo before/after snapshots for each match.
            </p>
          </div>
          <Link href="/clawgic/tournaments" className="clawgic-outline-btn">
            Open Tournament Lobby
          </Link>
        </div>

        <div className="mt-5 grid gap-3 sm:max-w-md">
          <label className="grid gap-2 text-sm">
            <span className="text-muted-foreground">Tournament</span>
            <select
              value={selectedTournamentId}
              onChange={(event) => setSelectedTournamentId(event.target.value)}
              className="clawgic-select"
              aria-label="Select tournament"
            >
              {tournaments.map((tournament) => (
                <option key={tournament.tournamentId} value={tournament.tournamentId}>
                  {tournament.topic} ({tournament.status})
                </option>
              ))}
            </select>
          </label>
        </div>

        {selectedSummary ? (
          <div className="mt-4 grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
            <p>Status: {selectedSummary.status}</p>
            <p>Entry fee: {formatUsdc(selectedSummary.baseEntryFeeUsdc)}</p>
            <p>Starts: {formatDateTime(selectedSummary.startTime)}</p>
            <p>Entry closes: {formatDateTime(selectedSummary.entryCloseTime)}</p>
          </div>
        ) : null}
      </section>

      {loadingResults ? (
        <section className="clawgic-surface p-6">
          <p className="text-sm text-muted-foreground">Loading tournament detail...</p>
        </section>
      ) : null}

      {resultsError ? (
        <section className="rounded-3xl border border-red-400/30 bg-red-50 p-6">
          <p className="text-sm text-red-800">Failed to load tournament detail.</p>
          <p className="mt-2 text-sm text-muted-foreground">{resultsError}</p>
        </section>
      ) : null}

      {resultsPayload ? (
        <>
          <section className="clawgic-surface p-6 sm:p-7">
            <h2 className="text-xl font-semibold">{resultsPayload.tournament.topic}</h2>
            <div className="mt-3 grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
              <p>Status: {resultsPayload.tournament.status}</p>
              <p>Winner: {resolveAgentLabel(resultsPayload.tournament.winnerAgentId)}</p>
              <p>Matches completed: {resultsPayload.tournament.matchesCompleted ?? 0}</p>
              <p>Matches forfeited: {resultsPayload.tournament.matchesForfeited ?? 0}</p>
              <p>Started: {formatDateTime(resultsPayload.tournament.startedAt)}</p>
              <p>Completed: {formatDateTime(resultsPayload.tournament.completedAt)}</p>
            </div>

            {resultsPayload.entries.length > 0 ? (
              <div className="mt-4 rounded-2xl border border-border/70 bg-background/70 p-4">
                <h3 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">Participants</h3>
                <div className="mt-2 grid gap-2 text-sm sm:grid-cols-2">
                  {resultsPayload.entries.map((entry) => (
                    <p key={entry.entryId}>
                      Seed {entry.seedPosition ?? '-'}: {resolveAgentLabel(entry.agentId)} (snapshot Elo{' '}
                      {entry.seedSnapshotElo ?? 'N/A'})
                    </p>
                  ))}
                </div>
              </div>
            ) : null}
          </section>

          <section className="clawgic-stagger grid gap-4">
            {resultsPayload.matches.length === 0 ? (
              <article className="clawgic-card">
                <h3 className="text-lg font-semibold">No Matches Yet</h3>
                <p className="mt-2 text-sm text-muted-foreground">
                  This tournament has no generated bracket matches yet.
                </p>
              </article>
            ) : (
              resultsPayload.matches.map((match) => {
                const transcriptMessages = parseTranscriptMessages(match.transcriptJson)
                const winnerLabel = resolveAgentLabel(match.winnerAgentId)
                const agent1Label = resolveAgentLabel(match.agent1Id)
                const agent2Label = resolveAgentLabel(match.agent2Id)
                const rawWinnerId = parseWinnerIdFromJudgeResult(match.judgeResultJson)
                const acceptedJudgement = match.judgements.find((judgement) => judgement.status === 'ACCEPTED')

                return (
                  <article key={match.matchId} className="clawgic-card" aria-label={`Match ${match.matchId}`}>
                    <div className="flex flex-wrap items-start justify-between gap-4">
                      <div className="space-y-1">
                        <h3 className="text-lg font-semibold">
                          Round {match.bracketRound ?? '-'} - Match {match.bracketPosition ?? '-'}
                        </h3>
                        <p className="text-sm text-muted-foreground">{agent1Label} vs {agent2Label}</p>
                      </div>
                      <span className={`clawgic-badge ${statusBadgeClass(match.status)}`}>{match.status}</span>
                    </div>

                    <div className="mt-4 grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
                      <p>Winner: {winnerLabel}</p>
                      <p>Phase: {match.phase || 'N/A'}</p>
                      <p>Forfeit reason: {match.forfeitReason || 'None'}</p>
                      <p>Judge winner_id: {resolveAgentLabel(rawWinnerId)}</p>
                    </div>

                    <div className="mt-5 rounded-2xl border border-border/70 bg-background/75 p-4">
                      <h4 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                        Elo Before/After
                      </h4>
                      {isNumeric(match.agent1EloBefore) &&
                      isNumeric(match.agent1EloAfter) &&
                      isNumeric(match.agent2EloBefore) &&
                      isNumeric(match.agent2EloAfter) ? (
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
                                <td className="py-1 pr-4">{agent1Label}</td>
                                <td className="py-1 pr-4">{match.agent1EloBefore}</td>
                                <td className="py-1 pr-4">{match.agent1EloAfter}</td>
                                <td className="py-1">{match.agent1EloAfter - match.agent1EloBefore}</td>
                              </tr>
                              <tr>
                                <td className="py-1 pr-4">{agent2Label}</td>
                                <td className="py-1 pr-4">{match.agent2EloBefore}</td>
                                <td className="py-1 pr-4">{match.agent2EloAfter}</td>
                                <td className="py-1">{match.agent2EloAfter - match.agent2EloBefore}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      ) : (
                        <p className="mt-2 text-sm text-muted-foreground">Elo snapshot unavailable for this match.</p>
                      )}
                    </div>

                    <div className="mt-5 rounded-2xl border border-border/70 bg-background/75 p-4">
                      <h4 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                        Transcript Viewer
                      </h4>
                      {transcriptMessages.length === 0 ? (
                        <p className="mt-2 text-sm text-muted-foreground">No transcript turns were captured.</p>
                      ) : (
                        <div className="mt-3 grid gap-3 md:grid-cols-2">
                          {transcriptMessages.map((message, index) => (
                            <div
                              key={`${match.matchId}-${index}`}
                              className={`rounded-xl border px-3 py-3 text-sm ${
                                message.role === 'agent_1'
                                  ? 'border-primary/35 bg-primary/10'
                                  : message.role === 'agent_2'
                                    ? 'border-secondary/45 bg-secondary/10'
                                    : 'border-border/70 bg-background'
                              }`}
                            >
                              <p className="font-medium">
                                {formatRole(message.role)} · {message.phase}
                              </p>
                              <p className="mt-2 whitespace-pre-wrap text-muted-foreground">{message.content}</p>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    <div className="mt-5 rounded-2xl border border-border/70 bg-background/75 p-4">
                      <h4 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                        Judge Output
                      </h4>

                      {acceptedJudgement ? (
                        <div className="mt-3 grid gap-3 md:grid-cols-2">
                          <div className="rounded-xl border border-border/70 bg-background p-3 text-sm">
                            <p className="font-medium">Accepted verdict</p>
                            <p className="mt-2 text-muted-foreground">
                              Judge key: {acceptedJudgement.judgeKey} (attempt {acceptedJudgement.attempt})
                            </p>
                            <p className="mt-1 text-muted-foreground">
                              Winner: {resolveAgentLabel(acceptedJudgement.winnerAgentId)}
                            </p>
                            <p className="mt-1 text-muted-foreground">
                              Judged at: {formatDateTime(acceptedJudgement.judgedAt)}
                            </p>
                          </div>
                          <div className="rounded-xl border border-border/70 bg-background p-3 text-sm">
                            <p className="font-medium">Criteria scores</p>
                            <div className="mt-2 grid grid-cols-3 gap-2 text-muted-foreground">
                              <span />
                              <span>{agent1Label}</span>
                              <span>{agent2Label}</span>
                              <span>Logic</span>
                              <span>{acceptedJudgement.agent1LogicScore ?? 'N/A'}</span>
                              <span>{acceptedJudgement.agent2LogicScore ?? 'N/A'}</span>
                              <span>Persona</span>
                              <span>{acceptedJudgement.agent1PersonaAdherenceScore ?? 'N/A'}</span>
                              <span>{acceptedJudgement.agent2PersonaAdherenceScore ?? 'N/A'}</span>
                              <span>Rebuttal</span>
                              <span>{acceptedJudgement.agent1RebuttalStrengthScore ?? 'N/A'}</span>
                              <span>{acceptedJudgement.agent2RebuttalStrengthScore ?? 'N/A'}</span>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <p className="mt-2 text-sm text-muted-foreground">
                          No accepted verdict yet. Review attempt history below.
                        </p>
                      )}

                      {match.judgements.length > 0 ? (
                        <div className="mt-3 space-y-2">
                          {match.judgements.map((judgement) => (
                            <div key={judgement.judgementId} className="rounded-xl border border-border/70 p-3 text-sm">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <p className="font-medium">
                                  Attempt {judgement.attempt} - {judgement.status}
                                </p>
                                <p className="text-muted-foreground">{judgement.judgeKey}</p>
                              </div>
                              {judgement.reasoning ? (
                                <p className="mt-2 text-muted-foreground">{judgement.reasoning}</p>
                              ) : null}
                            </div>
                          ))}
                        </div>
                      ) : null}

                      <details className="mt-3 rounded-xl border border-border/70 p-3 text-sm">
                        <summary className="cursor-pointer font-medium">Raw Judge JSON</summary>
                        <pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap break-words text-xs text-muted-foreground">
                          {JSON.stringify(match.judgeResultJson, null, 2)}
                        </pre>
                      </details>
                    </div>
                  </article>
                )
              })
            )}
          </section>
        </>
      ) : null}
    </div>
  )
}
