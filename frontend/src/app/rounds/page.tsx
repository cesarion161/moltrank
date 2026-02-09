'use client'

import { useEffect, useState } from 'react'
import { apiClient } from '@/lib/api-client'
import { Round, RoundStatus } from '@/lib/types'

export default function RoundsPage() {
  const [rounds, setRounds] = useState<Round[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filterMarket, setFilterMarket] = useState<string>('1')
  const [filterStatus, setFilterStatus] = useState<RoundStatus | 'ALL'>('ALL')
  const [expandedRoundId, setExpandedRoundId] = useState<number | null>(null)
  const [currentPage, setCurrentPage] = useState(1)
  const itemsPerPage = 10

  useEffect(() => {
    fetchRounds()
  }, [filterMarket])

  const fetchRounds = async () => {
    try {
      setLoading(true)
      const data = await apiClient.get<Round[]>(`/rounds?marketId=${filterMarket}`)
      setRounds(data)
      setError(null)
    } catch (err) {
      setError('Failed to load rounds')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      alert('Settlement hash copied to clipboard!')
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  const filteredRounds = rounds.filter(
    (round) => filterStatus === 'ALL' || round.status === filterStatus
  )

  const paginatedRounds = filteredRounds.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  )

  const totalPages = Math.ceil(filteredRounds.length / itemsPerPage)

  const toggleExpand = (roundId: number) => {
    setExpandedRoundId(expandedRoundId === roundId ? null : roundId)
  }

  const calculateTotalStake = (round: Round) => {
    return (round.basePerPair + round.premiumPerPair) * round.pairs
  }

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return 'N/A'
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <p className="text-muted-foreground">Loading rounds...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <p className="text-red-500">{error}</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Rounds Archive</h1>
        <p className="text-muted-foreground mt-2">
          View settled rounds and their results
        </p>
      </div>

      {/* Filters */}
      <div className="flex gap-4 items-center">
        <div>
          <label className="text-sm font-medium mr-2">Market:</label>
          <select
            value={filterMarket}
            onChange={(e) => setFilterMarket(e.target.value)}
            className="bg-background border border-border rounded px-3 py-1.5"
          >
            <option value="1">Market 1</option>
            <option value="2">Market 2</option>
            <option value="3">Market 3</option>
          </select>
        </div>
        <div>
          <label className="text-sm font-medium mr-2">Status:</label>
          <select
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value as RoundStatus | 'ALL')}
            className="bg-background border border-border rounded px-3 py-1.5"
          >
            <option value="ALL">All</option>
            <option value={RoundStatus.SETTLED}>Settled</option>
            <option value={RoundStatus.SETTLING}>Settling</option>
            <option value={RoundStatus.REVEAL}>Reveal</option>
            <option value={RoundStatus.COMMIT}>Commit</option>
            <option value={RoundStatus.OPEN}>Open</option>
          </select>
        </div>
      </div>

      {/* Rounds Table */}
      <div className="border border-border rounded-lg overflow-hidden">
        <table className="w-full">
          <thead className="bg-muted">
            <tr>
              <th className="text-left px-4 py-3 font-medium">Round ID</th>
              <th className="text-left px-4 py-3 font-medium">Market</th>
              <th className="text-left px-4 py-3 font-medium">Status</th>
              <th className="text-left px-4 py-3 font-medium">Pairs</th>
              <th className="text-left px-4 py-3 font-medium">Total Stake</th>
              <th className="text-left px-4 py-3 font-medium">Settlement Hash</th>
              <th className="text-left px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {paginatedRounds.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-8 text-muted-foreground">
                  No rounds found
                </td>
              </tr>
            ) : (
              paginatedRounds.map((round) => (
                <>
                  <tr
                    key={round.id}
                    className="border-t border-border hover:bg-muted/50 cursor-pointer"
                    onClick={() => toggleExpand(round.id)}
                  >
                    <td className="px-4 py-3">#{round.id}</td>
                    <td className="px-4 py-3">{round.market.name}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-1 rounded text-xs font-medium ${
                          round.status === RoundStatus.SETTLED
                            ? 'bg-green-500/20 text-green-400'
                            : round.status === RoundStatus.OPEN
                            ? 'bg-blue-500/20 text-blue-400'
                            : 'bg-yellow-500/20 text-yellow-400'
                        }`}
                      >
                        {round.status}
                      </span>
                    </td>
                    <td className="px-4 py-3">{round.pairs}</td>
                    <td className="px-4 py-3">
                      {(calculateTotalStake(round) / 1e9).toFixed(2)} SURGE
                    </td>
                    <td className="px-4 py-3">
                      {round.contentMerkleRoot ? (
                        <span className="font-mono text-sm">
                          {round.contentMerkleRoot.substring(0, 10)}...
                        </span>
                      ) : (
                        'N/A'
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {round.contentMerkleRoot && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            copyToClipboard(round.contentMerkleRoot!)
                          }}
                          className="text-xs text-primary hover:underline"
                        >
                          Copy
                        </button>
                      )}
                    </td>
                  </tr>
                  {expandedRoundId === round.id && (
                    <tr className="border-t border-border bg-muted/30">
                      <td colSpan={7} className="px-4 py-4">
                        <div className="space-y-3">
                          <h3 className="font-medium text-sm">Round Details</h3>
                          <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                              <span className="text-muted-foreground">Created:</span>
                              <span className="ml-2">{formatDate(round.createdAt)}</span>
                            </div>
                            <div>
                              <span className="text-muted-foreground">Started:</span>
                              <span className="ml-2">{formatDate(round.startedAt)}</span>
                            </div>
                            <div>
                              <span className="text-muted-foreground">Commit Deadline:</span>
                              <span className="ml-2">
                                {formatDate(round.commitDeadline)}
                              </span>
                            </div>
                            <div>
                              <span className="text-muted-foreground">Reveal Deadline:</span>
                              <span className="ml-2">
                                {formatDate(round.revealDeadline)}
                              </span>
                            </div>
                            <div>
                              <span className="text-muted-foreground">Settled:</span>
                              <span className="ml-2">{formatDate(round.settledAt)}</span>
                            </div>
                            <div>
                              <span className="text-muted-foreground">Base Per Pair:</span>
                              <span className="ml-2">
                                {(round.basePerPair / 1e9).toFixed(2)} SURGE
                              </span>
                            </div>
                            <div>
                              <span className="text-muted-foreground">Premium Per Pair:</span>
                              <span className="ml-2">
                                {(round.premiumPerPair / 1e9).toFixed(2)} SURGE
                              </span>
                            </div>
                            {round.contentMerkleRoot && (
                              <div className="col-span-2">
                                <span className="text-muted-foreground">
                                  Full Settlement Hash:
                                </span>
                                <div className="mt-1 font-mono text-xs bg-background p-2 rounded break-all">
                                  {round.contentMerkleRoot}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <button
            onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
            disabled={currentPage === 1}
            className="px-3 py-1 border border-border rounded disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
          >
            Previous
          </button>
          <span className="px-3 py-1">
            Page {currentPage} of {totalPages}
          </span>
          <button
            onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
            disabled={currentPage === totalPages}
            className="px-3 py-1 border border-border rounded disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
