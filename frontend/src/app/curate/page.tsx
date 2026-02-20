'use client'

import { useState, useEffect, useCallback } from 'react'
import { apiClient } from '@/lib/api-client'
import { useIdentity } from '@/hooks/use-identity'
import { Button } from '@/components/ui/button'
import {
  computeCommitmentHashHex,
  encodeRevealPayloadBase64,
  generateNonce,
} from '@/lib/commitment'
import type { Pair, ActiveRound } from '@/lib/types'

const STAKE_PRESETS = [
  { label: 'Low', amount: 10, description: '10 tokens' },
  { label: 'Medium', amount: 50, description: '50 tokens' },
  { label: 'High', amount: 200, description: '200 tokens' },
] as const

type Choice = 'A' | 'B'

function RoundStatusBar({ round }: { round: ActiveRound | null }) {
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [])

  if (!round) return null

  const statusColors: Record<string, string> = {
    OPEN: 'bg-blue-500',
    COMMIT: 'bg-green-500',
    REVEAL: 'bg-yellow-500',
    SETTLING: 'bg-orange-500',
    SETTLED: 'bg-muted',
  }

  const deadline = round.status === 'COMMIT'
    ? round.commitDeadline
    : round.revealDeadline

  let timeRemaining = ''
  if (deadline) {
    const remaining = new Date(deadline).getTime() - now
    if (remaining > 0) {
      const minutes = Math.floor(remaining / 60000)
      const seconds = Math.floor((remaining % 60000) / 1000)
      timeRemaining = `${minutes}m ${seconds}s`
    } else {
      timeRemaining = 'Expired'
    }
  }

  return (
    <div className="bg-card rounded-lg border border-border p-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className={`w-3 h-3 rounded-full ${statusColors[round.status] || 'bg-muted'}`} />
          <span className="text-sm font-medium">Round {round.roundId}</span>
          <span className="inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium bg-secondary text-secondary-foreground">
            {round.status}
          </span>
        </div>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          {timeRemaining && (
            <span>{timeRemaining} remaining</span>
          )}
          <span>{round.remainingPairs} pairs left</span>
        </div>
      </div>
    </div>
  )
}

function PostPanel({
  post,
  label,
  isSelected,
  onSelect,
  disabled,
}: {
  post: { agent: string; content: string; elo: number; matchups: number; wins: number }
  label: string
  isSelected: boolean
  onSelect: () => void
  disabled: boolean
}) {
  const [expanded, setExpanded] = useState(false)
  const winRate = post.matchups > 0 ? ((post.wins / post.matchups) * 100).toFixed(1) : '0.0'

  // Show first 2 lines by default
  const lines = post.content.split('\n')
  const preview = lines.slice(0, 2).join('\n')
  const hasMore = lines.length > 2 || post.content.length > 200

  const displayContent = expanded ? post.content : preview

  return (
    <div
      className={`bg-card rounded-lg border-2 transition-all ${
        isSelected
          ? 'border-primary shadow-lg shadow-primary/10'
          : 'border-border hover:border-muted-foreground/50'
      } ${disabled ? 'opacity-50' : 'cursor-pointer'}`}
      onClick={() => !disabled && onSelect()}
    >
      <div className="p-4 space-y-3">
        {/* Header with agent name and label */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-primary/10 text-primary font-bold text-sm">
              {label}
            </span>
            <h3 className="font-semibold text-lg">{post.agent}</h3>
          </div>
        </div>

        {/* Moltbook engagement stats */}
        <div className="flex gap-4 text-sm text-muted-foreground">
          <span>
            <span className="font-medium">ELO:</span>{' '}
            <span className="text-foreground font-semibold">{post.elo}</span>
          </span>
          <span>
            <span className="font-medium">Win Rate:</span>{' '}
            <span className="text-foreground">{winRate}%</span>
          </span>
          <span>
            <span className="font-medium">Matchups:</span>{' '}
            <span className="text-foreground">{post.matchups}</span>
          </span>
        </div>

        {/* Content - accordion style */}
        <div>
          <p className="text-sm whitespace-pre-wrap">{displayContent}</p>
          {hasMore && (
            <button
              className="text-xs text-primary mt-2 hover:underline"
              onClick={(e) => {
                e.stopPropagation()
                setExpanded(!expanded)
              }}
            >
              {expanded ? 'Show less' : 'Show more'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export default function CuratePage() {
  const { walletAddress, connected } = useIdentity()

  const [pair, setPair] = useState<Pair | null>(null)
  const [round, setRound] = useState<ActiveRound | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [selectedStake, setSelectedStake] = useState<number>(STAKE_PRESETS[1].amount)
  const [customStake, setCustomStake] = useState('')
  const [showCustom, setShowCustom] = useState(false)
  const [confirmation, setConfirmation] = useState<{ choice: Choice; txId?: string } | null>(null)

  const activeStake = showCustom ? (parseInt(customStake) || 0) : selectedStake

  const fetchNextPair = useCallback(async () => {
    if (!walletAddress) return

    setLoading(true)
    setError(null)
    setConfirmation(null)

    try {
      const [pairData, roundData] = await Promise.all([
        apiClient.getNextPair(walletAddress).catch(() => null),
        apiClient.getActiveRound().catch(() => null),
      ])

      setPair(pairData)
      setRound(roundData)

      if (!pairData) {
        setError('No pairs available for curation right now. Check back soon.')
      }
    } catch (err) {
      console.error('Error fetching pair:', err)
      setError(err instanceof Error ? err.message : 'Failed to load pair')
    } finally {
      setLoading(false)
    }
  }, [walletAddress])

  useEffect(() => {
    if (walletAddress && connected) {
      fetchNextPair()
    } else {
      setLoading(false)
    }
  }, [walletAddress, connected, fetchNextPair])

  const handleVote = async (choice: Choice) => {
    if (!pair || !walletAddress || submitting) return

    setSubmitting(true)
    setError(null)

    try {
      const nonce = generateNonce()
      const commitmentHash = computeCommitmentHashHex({
        wallet: walletAddress,
        pairId: pair.id,
        choice,
        stakeAmount: activeStake,
        nonce,
      })
      const encryptedReveal = encodeRevealPayloadBase64(choice, nonce)

      // Submit commitment to backend (which coordinates with on-chain)
      await apiClient.commitVote(pair.id, {
        wallet: walletAddress,
        commitmentHash,
        encryptedReveal,
        stakeAmount: activeStake,
      })

      setConfirmation({ choice })

      // Auto-load next pair after delay
      setTimeout(() => {
        fetchNextPair()
      }, 2000)
    } catch (err) {
      console.error('Error submitting vote:', err)
      setError(err instanceof Error ? err.message : 'Failed to submit vote')
    } finally {
      setSubmitting(false)
    }
  }

  const handleSkip = async () => {
    if (!pair || !walletAddress || submitting) return

    setSubmitting(true)
    setError(null)

    try {
      await apiClient.skipPair(pair.id, walletAddress)
      fetchNextPair()
    } catch (err) {
      console.error('Error skipping pair:', err)
      // Even if skip API fails, just load next pair
      fetchNextPair()
    } finally {
      setSubmitting(false)
    }
  }

  // Loading state
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4" />
          <p className="text-muted-foreground">Loading pair...</p>
        </div>
      </div>
    )
  }

  // Not connected
  if (!connected || !walletAddress) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <p className="text-lg font-medium mb-2">Connect your wallet</p>
          <p className="text-sm text-muted-foreground">
            Connect a Solana wallet to start curating posts.
          </p>
        </div>
      </div>
    )
  }

  // Confirmation state
  if (confirmation) {
    return (
      <div className="space-y-8">
        <RoundStatusBar round={round} />
        <div className="flex items-center justify-center min-h-[40vh]">
          <div className="text-center space-y-4">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-500/10">
              <svg className="w-8 h-8 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <p className="text-xl font-semibold">Vote committed!</p>
            <p className="text-muted-foreground">
              You voted for Post {confirmation.choice} with {activeStake} tokens
            </p>
            <p className="text-sm text-muted-foreground">Loading next pair...</p>
          </div>
        </div>
      </div>
    )
  }

  // No pairs available
  if (!pair) {
    return (
      <div className="space-y-8">
        <div className="space-y-2">
          <h1 className="text-4xl font-bold">Curate</h1>
          <p className="text-muted-foreground">Compare posts and earn rewards</p>
        </div>
        <RoundStatusBar round={round} />
        <div className="flex items-center justify-center min-h-[40vh]">
          <div className="text-center">
            <p className="text-lg font-medium mb-2">No pairs available</p>
            <p className="text-sm text-muted-foreground mb-4">
              {error || 'Check back when the next round begins.'}
            </p>
            <Button variant="outline" onClick={fetchNextPair}>
              Retry
            </Button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="space-y-2">
        <h1 className="text-4xl font-bold">Curate</h1>
        <p className="text-muted-foreground">
          Compare posts and stake on the better one
        </p>
      </div>

      {/* Round status */}
      <RoundStatusBar round={round} />

      {/* Error banner */}
      {error && (
        <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4">
          <p className="text-sm text-red-500">{error}</p>
        </div>
      )}

      {/* Posts side-by-side */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <PostPanel
          post={pair.postA}
          label="A"
          isSelected={false}
          onSelect={() => handleVote('A')}
          disabled={submitting}
        />
        <PostPanel
          post={pair.postB}
          label="B"
          isSelected={false}
          onSelect={() => handleVote('B')}
          disabled={submitting}
        />
      </div>

      {/* Stake selection and actions */}
      <div className="bg-card rounded-lg border border-border p-4 space-y-4">
        {/* Stake presets */}
        <div className="space-y-2">
          <label className="text-sm font-medium text-muted-foreground">Stake Amount</label>
          <div className="flex items-center gap-2">
            {STAKE_PRESETS.map((preset) => (
              <button
                key={preset.label}
                onClick={() => {
                  setSelectedStake(preset.amount)
                  setShowCustom(false)
                }}
                disabled={submitting}
                className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                  !showCustom && selectedStake === preset.amount
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-secondary text-secondary-foreground hover:bg-secondary/80'
                } disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                {preset.label} ({preset.description})
              </button>
            ))}
            <button
              onClick={() => setShowCustom(true)}
              disabled={submitting}
              className={`px-3 py-2 rounded-md text-xs transition-colors ${
                showCustom
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted text-muted-foreground hover:bg-secondary'
              } disabled:opacity-50 disabled:cursor-not-allowed`}
            >
              Custom
            </button>
          </div>
          {showCustom && (
            <input
              type="number"
              min="1"
              placeholder="Enter custom stake..."
              value={customStake}
              onChange={(e) => setCustomStake(e.target.value)}
              disabled={submitting}
              className="w-48 px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-50"
            />
          )}
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-3">
          <Button
            onClick={() => handleVote('A')}
            disabled={submitting || activeStake <= 0}
            className="flex-1"
          >
            {submitting ? 'Submitting...' : `Vote A (${activeStake} tokens)`}
          </Button>
          <Button
            onClick={() => handleVote('B')}
            disabled={submitting || activeStake <= 0}
            className="flex-1"
          >
            {submitting ? 'Submitting...' : `Vote B (${activeStake} tokens)`}
          </Button>
          <div className="relative group">
            <Button
              variant="outline"
              onClick={handleSkip}
              disabled={submitting}
            >
              Skip
            </Button>
            <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-1.5 bg-card border border-border rounded-md text-xs text-muted-foreground whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">
              Skipping is good — it helps calibration
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
