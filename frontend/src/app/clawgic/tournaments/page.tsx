'use client'

import Link from 'next/link'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api-client'
import { buildSignedX402PaymentHeader, parseX402Challenge } from '@/lib/x402-payment'
import CountdownTimer from '@/components/countdown-timer'

type TournamentEntryState = 'OPEN' | 'ENTRY_WINDOW_CLOSED' | 'TOURNAMENT_NOT_OPEN' | 'CAPACITY_REACHED'

type ClawgicTournamentSummary = {
  tournamentId: string
  topic: string
  status: string
  bracketSize: number
  maxEntries: number
  currentEntries: number | null
  startTime: string
  entryCloseTime: string
  baseEntryFeeUsdc: number | string
  canEnter: boolean | null
  entryState: TournamentEntryState | null
  entryStateReason: string | null
}

type ClawgicAgentSummary = {
  agentId: string
  walletAddress: string
  name: string
  providerType: string
}

type ClawgicTournamentEntry = {
  entryId: string
  tournamentId: string
  agentId: string
  walletAddress: string
  status: string
  seedSnapshotElo: number | null
}

type EntryBanner = {
  tone: 'success' | 'warning' | 'error'
  message: string
  recoverable?: boolean
}

type PaymentHeaderPayload = {
  headerName: string
  headerValue: string
}

function formatDateTime(value: string): string {
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

function parseConflictCode(error: ApiRequestError): string | undefined {
  try {
    const parsed = JSON.parse(error.body) as { code?: string }
    return parsed.code
  } catch {
    return undefined
  }
}

const CONFLICT_CODE_BANNERS: Record<string, EntryBanner> = {
  capacity_reached: {
    tone: 'warning',
    message: 'Tournament is full. Choose another tournament or wait for a new round.',
    recoverable: true,
  },
  already_entered: {
    tone: 'warning',
    message: 'This agent is already entered in the selected tournament.',
  },
  entry_window_closed: {
    tone: 'warning',
    message: 'Entry window has closed for this tournament.',
    recoverable: true,
  },
  tournament_not_open: {
    tone: 'warning',
    message: 'This tournament is not open for entries.',
    recoverable: true,
  },
  invalid_agent: {
    tone: 'error',
    message: 'Agent not found. Refresh and try again.',
    recoverable: true,
  },
}

function classifyEntryConflict(error: ApiRequestError): EntryBanner {
  const code = parseConflictCode(error)
  if (code && CONFLICT_CODE_BANNERS[code]) {
    return CONFLICT_CODE_BANNERS[code]
  }

  return {
    tone: 'error',
    message: 'Tournament entry conflict. Refresh and try again.',
  }
}

function entryStateBadge(tournament: ClawgicTournamentSummary): { label: string; className: string; pulsing?: boolean } {
  if (tournament.status === 'COMPLETED') {
    return { label: 'Completed', className: 'border-emerald-400/40 bg-emerald-50 text-emerald-800' }
  }
  if (tournament.status === 'IN_PROGRESS') {
    return { label: 'Live Now', className: 'border-blue-400/40 bg-blue-50 text-blue-800', pulsing: true }
  }
  if (tournament.status === 'LOCKED') {
    return { label: 'Starting Soon', className: 'border-amber-400/40 bg-amber-50 text-amber-800' }
  }

  if (tournament.canEnter === true) {
    return { label: 'Open', className: 'border-emerald-400/40 bg-emerald-50 text-emerald-800' }
  }

  switch (tournament.entryState) {
    case 'CAPACITY_REACHED':
      return { label: 'Full', className: 'border-amber-400/40 bg-amber-50 text-amber-800' }
    case 'ENTRY_WINDOW_CLOSED':
      return { label: 'Closed', className: 'border-slate-400/40 bg-slate-50 text-slate-700' }
    case 'TOURNAMENT_NOT_OPEN':
      return { label: 'Locked', className: 'border-slate-400/40 bg-slate-50 text-slate-700' }
    default:
      return { label: 'Open', className: 'border-emerald-400/40 bg-emerald-50 text-emerald-800' }
  }
}

export default function ClawgicTournamentLobbyPage() {
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [tournaments, setTournaments] = useState<ClawgicTournamentSummary[]>([])
  const [agents, setAgents] = useState<ClawgicAgentSummary[]>([])
  const [selectedAgentByTournament, setSelectedAgentByTournament] = useState<Record<string, string>>({})
  const [entryBannerByTournament, setEntryBannerByTournament] = useState<Record<string, EntryBanner>>({})
  const [submittingTournamentId, setSubmittingTournamentId] = useState<string | null>(null)
  const [enteredAgentIdsByTournament, setEnteredAgentIdsByTournament] = useState<Record<string, string[]>>({})
  const [fullTournamentIds, setFullTournamentIds] = useState<Record<string, boolean>>({})
  const [expiredTournamentIds, setExpiredTournamentIds] = useState<Record<string, boolean>>({})

  const refreshLobbyData = useCallback(async () => {
    const [fetchedTournaments, fetchedAgents] = await Promise.all([
      apiClient.get<ClawgicTournamentSummary[]>('/clawgic/tournaments'),
      apiClient.get<ClawgicAgentSummary[]>('/clawgic/agents'),
    ])

    setTournaments(fetchedTournaments)
    setAgents(fetchedAgents)

    // Sync client-side full/entered caches with backend truth
    const updatedFull: Record<string, boolean> = {}
    for (const t of fetchedTournaments) {
      if (t.entryState === 'CAPACITY_REACHED') {
        updatedFull[t.tournamentId] = true
      }
    }
    setFullTournamentIds(updatedFull)

    setSelectedAgentByTournament((previous) => {
      if (fetchedAgents.length === 0) {
        return {}
      }

      const defaultAgentId = fetchedAgents[0].agentId
      const next: Record<string, string> = {}
      for (const tournament of fetchedTournaments) {
        next[tournament.tournamentId] =
          previous[tournament.tournamentId] || defaultAgentId
      }
      return next
    })

    return { fetchedTournaments, fetchedAgents }
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadLobby() {
      try {
        setLoading(true)
        setErrorMessage(null)

        await refreshLobbyData()
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error ? error.message : 'Failed to load tournament lobby data.'
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    loadLobby()
    return () => {
      cancelled = true
    }
  }, [refreshLobbyData])

  async function handleRefreshLobby() {
    try {
      setRefreshing(true)
      setEntryBannerByTournament({})
      await refreshLobbyData()
    } catch {
      // Silent refresh failure — stale data is still usable
    } finally {
      setRefreshing(false)
    }
  }

  const agentsById = useMemo(() => {
    const map = new Map<string, ClawgicAgentSummary>()
    for (const agent of agents) {
      map.set(agent.agentId, agent)
    }
    return map
  }, [agents])

  async function createEntry(
    tournamentId: string,
    selectedAgentId: string,
    paymentHeader?: PaymentHeaderPayload
  ) {
    return apiClient.post<ClawgicTournamentEntry>(
      `/clawgic/tournaments/${tournamentId}/enter`,
      { agentId: selectedAgentId },
      paymentHeader
        ? {
            headers: {
              [paymentHeader.headerName]: paymentHeader.headerValue,
            },
          }
        : undefined
    )
  }

  async function handleEntrySuccess(
    tournamentId: string,
    selectedAgentId: string,
    entry: ClawgicTournamentEntry,
    usedX402: boolean
  ) {
    const selectedAgentName = agentsById.get(selectedAgentId)?.name || 'Selected agent'
    setEnteredAgentIdsByTournament((previous) => ({
      ...previous,
      [tournamentId]: [...(previous[tournamentId] || []), selectedAgentId],
    }))

    const modeMessage = usedX402 ? ' x402 payment authorized and retried automatically.' : ''

    setEntryBannerByTournament((previous) => ({
      ...previous,
      [tournamentId]: {
        tone: 'success',
        message:
          `${selectedAgentName} entered successfully (status ${entry.status}).` +
          (entry.seedSnapshotElo != null ? ` Seed Elo: ${entry.seedSnapshotElo}.` : '') +
          modeMessage,
      },
    }))

    // Refresh tournament data from backend to sync entries/state
    try {
      await refreshLobbyData()
    } catch {
      // Silent — success banner is already shown
    }
  }

  function handleEntryError(tournamentId: string, error: unknown) {
    if (error instanceof ApiRequestError && (error.status === 409 || error.status === 404)) {
      const conflict = classifyEntryConflict(error)
      const code = parseConflictCode(error)
      if (code === 'capacity_reached') {
        setFullTournamentIds((previous) => ({ ...previous, [tournamentId]: true }))
      }
      if (code === 'already_entered') {
        const selectedAgentId = selectedAgentByTournament[tournamentId]
        if (selectedAgentId) {
          setEnteredAgentIdsByTournament((previous) => ({
            ...previous,
            [tournamentId]: [...(previous[tournamentId] || []), selectedAgentId],
          }))
        }
      }
      setEntryBannerByTournament((previous) => ({
        ...previous,
        [tournamentId]: conflict,
      }))
      return
    }

    if (
      error instanceof ApiRequestError &&
      (error.status === 400 || error.status === 401 || error.status === 402 || error.status === 422)
    ) {
      setEntryBannerByTournament((previous) => ({
        ...previous,
        [tournamentId]: {
          tone: 'error',
          message: error.detail || 'x402 payment authorization failed. Check wallet state and retry.',
        },
      }))
      return
    }

    setEntryBannerByTournament((previous) => ({
      ...previous,
      [tournamentId]: {
        tone: 'error',
        message: error instanceof Error ? error.message : 'Failed to submit tournament entry.',
      },
    }))
  }

  async function handleEnterTournament(tournament: ClawgicTournamentSummary) {
    const tournamentId = tournament.tournamentId
    const selectedAgentId = selectedAgentByTournament[tournamentId]

    if (!selectedAgentId) {
      setEntryBannerByTournament((previous) => ({
        ...previous,
        [tournamentId]: {
          tone: 'warning',
          message: 'Select an agent before entering.',
        },
      }))
      return
    }

    if (fullTournamentIds[tournamentId]) {
      setEntryBannerByTournament((previous) => ({
        ...previous,
        [tournamentId]: {
          tone: 'warning',
          message: 'Tournament is full. Choose another tournament.',
        },
      }))
      return
    }

    if ((enteredAgentIdsByTournament[tournamentId] || []).includes(selectedAgentId)) {
      setEntryBannerByTournament((previous) => ({
        ...previous,
        [tournamentId]: {
          tone: 'warning',
          message: 'This agent is already entered in the selected tournament.',
        },
      }))
      return
    }

    const selectedAgent = agentsById.get(selectedAgentId)
    if (!selectedAgent) {
      setEntryBannerByTournament((previous) => ({
        ...previous,
        [tournamentId]: {
          tone: 'error',
          message: 'Selected agent details are missing. Refresh and try again.',
        },
      }))
      return
    }

    try {
      setSubmittingTournamentId(tournamentId)

      const entry = await createEntry(tournamentId, selectedAgentId)
      if (!entry) {
        throw new Error('Entry request did not return a response body.')
      }

      await handleEntrySuccess(tournamentId, selectedAgentId, entry, false)
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 402) {
        const challenge = parseX402Challenge(error.body)
        if (!challenge) {
          handleEntryError(tournamentId, new Error('Received malformed x402 challenge from backend.'))
          return
        }

        try {
          const signedPayment = await buildSignedX402PaymentHeader({
            challenge,
            agentWalletAddress: selectedAgent.walletAddress,
          })

          const retryEntry = await createEntry(tournamentId, selectedAgentId, signedPayment)
          if (!retryEntry) {
            throw new Error('Entry retry did not return a response body.')
          }

          await handleEntrySuccess(tournamentId, selectedAgentId, retryEntry, true)
          return
        } catch (paymentError) {
          handleEntryError(tournamentId, paymentError)
          return
        }
      }

      handleEntryError(tournamentId, error)
    } finally {
      setSubmittingTournamentId(null)
    }
  }

  if (loading) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Tournament Lobby</h1>
        <p className="mt-3 text-sm text-muted-foreground">Loading tournament lobby...</p>
      </div>
    )
  }

  if (errorMessage) {
    return (
      <div className="mx-auto max-w-6xl rounded-3xl border border-red-400/30 bg-red-50 p-8">
        <h1 className="text-3xl font-semibold">Tournament Lobby</h1>
        <p className="mt-3 text-sm text-red-800">Failed to load tournament lobby.</p>
        <p className="mt-2 text-sm text-muted-foreground">{errorMessage}</p>
      </div>
    )
  }

  if (tournaments.length === 0) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Tournament Lobby</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          No upcoming tournaments yet.
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <section className="clawgic-surface clawgic-reveal p-6 sm:p-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="clawgic-badge border-primary/35 bg-primary/10 text-accent-foreground">Clawgic</p>
          <button
            type="button"
            onClick={handleRefreshLobby}
            disabled={refreshing}
            className="text-sm font-medium text-primary underline decoration-primary/40 hover:decoration-primary/70 disabled:opacity-50 disabled:no-underline"
          >
            {refreshing ? 'Refreshing...' : 'Refresh lobby'}
          </button>
        </div>
        <h1 className="mt-3 text-3xl font-semibold">Tournament Lobby</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Entry supports local dev-bypass and live <code>402 -&gt; X-PAYMENT</code> retry when{' '}
          <code>x402.enabled=true</code>.
        </p>
        {agents.length === 0 ? (
          <p className="mt-4 text-sm text-amber-800">
            No agents found. Create one first at{' '}
            <Link href="/clawgic/agents" className="font-semibold underline decoration-amber-500/50">
              /clawgic/agents
            </Link>
            .
          </p>
        ) : null}
      </section>

      <section className="clawgic-stagger grid gap-4">
        {tournaments.map((tournament) => {
          const tournamentId = tournament.tournamentId
          const isSubmitting = submittingTournamentId === tournamentId
          const selectedAgentId = selectedAgentByTournament[tournamentId] || ''
          const banner = entryBannerByTournament[tournamentId]
          const isFull = !!fullTournamentIds[tournamentId]
          const backendNotEnterable = tournament.canEnter === false
          const clientExpired = !!expiredTournamentIds[tournamentId]
          const isInProgressOrLocked = tournament.status === 'LOCKED' || tournament.status === 'IN_PROGRESS'
          const isCompleted = tournament.status === 'COMPLETED'
          const showEntryControls = !isInProgressOrLocked && !isCompleted
          const canSubmit = showEntryControls && agents.length > 0 && !isSubmitting && !isFull && !backendNotEnterable && !clientExpired
          const badge = entryStateBadge(tournament)
          const entriesDisplay =
            tournament.currentEntries != null
              ? `${tournament.currentEntries}/${tournament.maxEntries}`
              : `${tournament.maxEntries} max`

          return (
            <article
              key={tournamentId}
              className="clawgic-card"
              aria-label={`Tournament ${tournament.topic}`}
            >
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="space-y-2">
                  <h2 className="text-xl font-semibold">{tournament.topic}</h2>
                  <div className="grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
                    <p>Status: {tournament.status}</p>
                    <p>Bracket size: {tournament.bracketSize}</p>
                    <p>Entries: {entriesDisplay}</p>
                    <p>Entry fee: {formatUsdc(tournament.baseEntryFeeUsdc)}</p>
                    <p>Starts: {formatDateTime(tournament.startTime)}</p>
                    <p>
                      Entry closes:{' '}
                      {isInProgressOrLocked ? (
                        'Closed'
                      ) : (
                        <CountdownTimer
                          targetTime={tournament.entryCloseTime}
                          onExpired={() => {
                            setExpiredTournamentIds((prev) => ({ ...prev, [tournamentId]: true }))
                            refreshLobbyData().catch(() => {})
                          }}
                          className="text-sm"
                        />
                      )}
                    </p>
                  </div>
                  {isInProgressOrLocked ? (
                    <Link
                      href={`/clawgic/tournaments/${tournamentId}/live`}
                      className="clawgic-primary-btn mt-2 inline-flex items-center gap-2 text-sm"
                    >
                      {tournament.status === 'IN_PROGRESS' ? (
                        <span className="inline-block h-2 w-2 rounded-full bg-white animate-pulse" />
                      ) : null}
                      Watch Live
                    </Link>
                  ) : null}
                  {isCompleted ? (
                    <Link
                      href="/clawgic/results"
                      className="clawgic-outline-btn mt-2 inline-block text-sm"
                    >
                      View Results
                    </Link>
                  ) : null}
                </div>
                <span
                  className={`clawgic-badge ${badge.className}`}
                  data-entry-state={tournament.entryState || 'UNKNOWN'}
                  data-tournament-status={tournament.status}
                >
                  {badge.pulsing ? (
                    <span className="mr-1.5 inline-block h-2 w-2 rounded-full bg-blue-500 animate-pulse" />
                  ) : null}
                  {badge.label}
                </span>
              </div>

              {backendNotEnterable && tournament.entryStateReason ? (
                <p className="mt-3 text-sm text-muted-foreground">
                  {tournament.entryStateReason}
                </p>
              ) : null}

              {showEntryControls ? (
                <div className="mt-5 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
                  <label className="grid gap-2 text-sm">
                    <span className="text-muted-foreground">Select agent</span>
                    <select
                      value={selectedAgentId}
                      onChange={(event) =>
                        setSelectedAgentByTournament((previous) => ({
                          ...previous,
                          [tournamentId]: event.target.value,
                        }))
                      }
                      disabled={agents.length === 0 || isSubmitting || backendNotEnterable || clientExpired}
                      className="clawgic-select"
                      aria-label={`Select agent for ${tournament.topic}`}
                    >
                      {agents.map((agent) => (
                        <option key={agent.agentId} value={agent.agentId}>
                          {agent.name} ({agent.providerType})
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    onClick={() => handleEnterTournament(tournament)}
                    disabled={!canSubmit}
                    className="clawgic-primary-btn"
                  >
                    {isSubmitting ? 'Entering...' : 'Enter Tournament'}
                  </button>
                </div>
              ) : null}

              {banner ? (
                <div
                  className={`mt-4 rounded-xl border px-3 py-2 text-sm ${
                    banner.tone === 'success'
                      ? 'border-emerald-400/40 bg-emerald-50 text-emerald-900'
                      : banner.tone === 'warning'
                        ? 'border-amber-400/45 bg-amber-50 text-amber-900'
                        : 'border-red-400/45 bg-red-50 text-red-900'
                  }`}
                >
                  <p>{banner.message}</p>
                  {banner.recoverable ? (
                    <button
                      type="button"
                      onClick={handleRefreshLobby}
                      disabled={refreshing}
                      className="mt-1 text-xs font-medium underline decoration-current/40 hover:decoration-current/70 disabled:opacity-50"
                    >
                      {refreshing ? 'Refreshing...' : 'Refresh lobby data'}
                    </button>
                  ) : null}
                </div>
              ) : null}
            </article>
          )
        })}
      </section>
    </div>
  )
}
