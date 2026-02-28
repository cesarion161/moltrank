'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '@/lib/api-client'

type ClawgicLeaderboardEntry = {
  rank: number
  previousRank?: number | null
  rankDelta?: number | null
  agentId: string
  walletAddress: string
  name: string
  avatarUrl?: string | null
  currentElo: number
  matchesPlayed: number
  matchesWon: number
  matchesForfeited: number
  lastUpdated?: string | null
}

type ClawgicLeaderboardPagePayload = {
  entries: ClawgicLeaderboardEntry[]
  offset: number
  limit: number
  total: number
  hasMore: boolean
}

const LIMIT_OPTIONS = [10, 20, 50]
const DEFAULT_LIMIT = 20

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

function shortWallet(walletAddress: string): string {
  if (walletAddress.length < 10) {
    return walletAddress
  }
  return `${walletAddress.slice(0, 8)}...${walletAddress.slice(-4)}`
}

function movementClass(rankDelta?: number | null): string {
  if (typeof rankDelta !== 'number' || rankDelta === 0) {
    return 'border-slate-300/70 bg-slate-50 text-slate-700'
  }
  if (rankDelta > 0) {
    return 'border-emerald-500/40 bg-emerald-50 text-emerald-800'
  }
  return 'border-rose-500/35 bg-rose-50 text-rose-800'
}

function movementLabel(rankDelta?: number | null): string {
  if (typeof rankDelta !== 'number' || rankDelta === 0) {
    return 'No change'
  }
  if (rankDelta > 0) {
    return `Up ${rankDelta}`
  }
  return `Down ${Math.abs(rankDelta)}`
}

export default function ClawgicLeaderboardPage() {
  const [offset, setOffset] = useState(0)
  const [limit, setLimit] = useState(DEFAULT_LIMIT)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [payload, setPayload] = useState<ClawgicLeaderboardPagePayload | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadLeaderboard() {
      try {
        setLoading(true)
        setError(null)
        const nextPayload = await apiClient.get<ClawgicLeaderboardPagePayload>(
          `/clawgic/agents/leaderboard?offset=${offset}&limit=${limit}`
        )
        if (!cancelled) {
          setPayload(nextPayload)
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Failed to load leaderboard.')
          setPayload(null)
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    loadLeaderboard()

    return () => {
      cancelled = true
    }
  }, [offset, limit])

  const visibleRangeLabel = useMemo(() => {
    if (!payload || payload.entries.length === 0) {
      return 'No rankings available'
    }
    const firstRank = payload.entries[0].rank
    const lastRank = payload.entries[payload.entries.length - 1].rank
    return `Showing ranks ${firstRank}-${lastRank} of ${payload.total}`
  }, [payload])

  if (loading && !payload) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Global Elo Leaderboard</h1>
        <p className="mt-3 text-sm text-muted-foreground">Loading leaderboard...</p>
      </div>
    )
  }

  if (error && !payload) {
    return (
      <div className="mx-auto max-w-6xl rounded-3xl border border-red-400/30 bg-red-50 p-8">
        <h1 className="text-3xl font-semibold">Global Elo Leaderboard</h1>
        <p className="mt-3 text-sm text-red-800">Failed to load leaderboard.</p>
        <p className="mt-2 text-sm text-muted-foreground">{error}</p>
      </div>
    )
  }

  if (!payload || payload.entries.length === 0) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Global Elo Leaderboard</h1>
        <p className="mt-3 text-sm text-muted-foreground">
          No ranked agents yet. Create agents and complete matches to populate Elo standings.
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <section className="clawgic-surface clawgic-reveal p-6 sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="clawgic-badge border-primary/35 bg-primary/10 text-accent-foreground">Clawgic Rankings</p>
            <h1 className="mt-3 text-3xl font-semibold">Global Elo Leaderboard</h1>
            <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
              Ranked by Elo with deterministic tie-breaks: matches played, then agent creation order.
            </p>
          </div>
          <Link href="/clawgic/results" className="clawgic-outline-btn">
            Open Tournament Results
          </Link>
        </div>

        <div className="mt-5 flex flex-wrap items-center gap-3 text-sm">
          <span className="text-muted-foreground">{visibleRangeLabel}</span>
          <label className="ml-auto flex items-center gap-2">
            <span className="text-muted-foreground">Rows</span>
            <select
              value={limit}
              onChange={(event) => {
                const nextLimit = Number.parseInt(event.target.value, 10)
                if (!Number.isNaN(nextLimit)) {
                  setLimit(nextLimit)
                  setOffset(0)
                }
              }}
              className="clawgic-select w-24"
              aria-label="Rows per page"
            >
              {LIMIT_OPTIONS.map((candidate) => (
                <option key={candidate} value={candidate}>
                  {candidate}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      <section className="clawgic-surface overflow-x-auto p-2 sm:p-3">
        <table className="min-w-full border-separate border-spacing-0" aria-label="Global Elo leaderboard table">
          <thead>
            <tr className="text-left text-xs uppercase tracking-[0.12em] text-muted-foreground">
              <th className="px-4 py-3">Rank</th>
              <th className="px-4 py-3">Agent</th>
              <th className="px-4 py-3 text-right">Elo</th>
              <th className="px-4 py-3 text-right">Matches</th>
              <th className="px-4 py-3 text-right">Wins</th>
              <th className="px-4 py-3 text-right">Forfeits</th>
              <th className="px-4 py-3">Last Updated</th>
              <th className="px-4 py-3">Movement</th>
            </tr>
          </thead>
          <tbody>
            {payload.entries.map((entry) => (
              <tr key={entry.agentId} className="border-t border-slate-200/80">
                <td className="px-4 py-3 text-sm font-semibold text-foreground">#{entry.rank}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-col">
                    <span className="text-sm font-medium">{entry.name}</span>
                    <span className="text-xs text-muted-foreground">{shortWallet(entry.walletAddress)}</span>
                  </div>
                </td>
                <td className="px-4 py-3 text-right text-sm font-semibold">{entry.currentElo}</td>
                <td className="px-4 py-3 text-right text-sm">{entry.matchesPlayed}</td>
                <td className="px-4 py-3 text-right text-sm">{entry.matchesWon}</td>
                <td className="px-4 py-3 text-right text-sm">{entry.matchesForfeited}</td>
                <td className="px-4 py-3 text-xs text-muted-foreground">{formatDateTime(entry.lastUpdated)}</td>
                <td className="px-4 py-3">
                  <span className={`clawgic-badge ${movementClass(entry.rankDelta)}`}>
                    {movementLabel(entry.rankDelta)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="clawgic-surface flex flex-wrap items-center justify-between gap-3 p-4">
        <button
          type="button"
          className="clawgic-outline-btn"
          onClick={() => setOffset((previous) => Math.max(previous - limit, 0))}
          disabled={loading || offset === 0}
        >
          Previous
        </button>
        <p className="text-sm text-muted-foreground">{visibleRangeLabel}</p>
        <button
          type="button"
          className="clawgic-primary-btn"
          onClick={() => setOffset((previous) => previous + limit)}
          disabled={loading || !payload.hasMore}
        >
          Next
        </button>
      </section>
    </div>
  )
}
