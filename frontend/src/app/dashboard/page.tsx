'use client'

import { useState, useEffect } from 'react'
import {
  BarChart,
  Bar,
  PieChart,
  Pie,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Cell,
} from 'recharts'
import { apiClient } from '@/lib/api-client'
import { CuratorStats, CuratorEvaluation } from '@/lib/types'
import { useIdentity } from '@/hooks/use-identity'
import { VerifiedBadge } from '@/components/verified-badge'

export default function DashboardPage() {
  const { walletAddress, connected, twitterUsername, isVerified } = useIdentity()
  const [stats, setStats] = useState<CuratorStats | null>(null)
  const [evaluations, setEvaluations] = useState<CuratorEvaluation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!walletAddress) {
      setLoading(false)
      return
    }

    async function fetchDashboardData() {
      try {
        const [statsData, evalsData] = await Promise.all([
          apiClient.getCuratorStats(walletAddress!),
          apiClient.getCuratorEvaluations(walletAddress!, 10),
        ])

        // Transform backend response to frontend format
        const transformedStats: CuratorStats = {
          wallet: statsData.wallet,
          earned: statsData.earned,
          lost: statsData.lost,
          net: statsData.earned - statsData.lost, // Calculate net from earned and lost
          curatorScore: Number(statsData.curatorScore),
          calibrationRate: Number(statsData.calibrationRate),
          auditPassRate: Number(statsData.auditPassRate),
          alignmentStability: Number(statsData.alignmentStability),
          fraudFlags: statsData.fraudFlags,
        }

        setStats(transformedStats)
        setEvaluations(evalsData)
        setLoading(false)
      } catch (err) {
        console.error('Dashboard data fetch error:', err)
        setError(err instanceof Error ? err.message : 'Failed to load dashboard data')
        setLoading(false)
      }
    }

    fetchDashboardData()
  }, [walletAddress])

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4"></div>
          <p className="text-muted-foreground">Loading dashboard...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <p className="text-red-500 mb-2">Error loading dashboard</p>
          <p className="text-sm text-muted-foreground">{error}</p>
        </div>
      </div>
    )
  }

  if (!connected || !walletAddress) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <p className="text-lg font-medium mb-2">Connect your wallet</p>
          <p className="text-sm text-muted-foreground">
            Connect a Solana wallet to view your curator dashboard.
          </p>
        </div>
      </div>
    )
  }

  if (!stats) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <p className="text-muted-foreground">No curator data available</p>
      </div>
    )
  }

  // Prepare chart data
  const earningsData = [
    { name: 'Base Earnings', value: stats.earned * 0.7, fill: '#10b981' },
    { name: 'Premium Earnings', value: stats.earned * 0.3, fill: '#3b82f6' },
  ]

  const accuracyOverTimeData = evaluations.map((evalItem, index) => ({
    evaluation: index + 1,
    accuracy: evalItem.outcome === 'win' ? 100 : 0,
  }))

  const formatTimestamp = (timestamp: number) => {
    const date = new Date(timestamp)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)
    const diffHours = Math.floor(diffMs / 3600000)

    if (diffMins < 60) return `${diffMins}m ago`
    if (diffHours < 24) return `${diffHours}h ago`
    return date.toLocaleDateString()
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-3">
          <h1 className="text-4xl font-bold">Curator Dashboard</h1>
          {isVerified && twitterUsername && (
            <VerifiedBadge twitterUsername={twitterUsername} />
          )}
        </div>
        <p className="text-muted-foreground">
          Track your curation performance and earnings
        </p>
      </div>

      {/* PnL Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-card rounded-lg border border-border p-6">
          <h3 className="text-sm font-medium text-muted-foreground mb-2">Total Earned</h3>
          <p className="text-3xl font-bold text-green-500">{stats.earned.toLocaleString()}</p>
          <p className="text-xs text-muted-foreground mt-1">SOL lamports</p>
        </div>

        <div className="bg-card rounded-lg border border-border p-6">
          <h3 className="text-sm font-medium text-muted-foreground mb-2">Total Lost</h3>
          <p className="text-3xl font-bold text-red-500">{stats.lost.toLocaleString()}</p>
          <p className="text-xs text-muted-foreground mt-1">SOL lamports</p>
        </div>

        <div className="bg-card rounded-lg border border-border p-6">
          <h3 className="text-sm font-medium text-muted-foreground mb-2">Net PnL</h3>
          <p
            className={`text-3xl font-bold ${
              stats.net >= 0 ? 'text-green-500' : 'text-red-500'
            }`}
          >
            {stats.net >= 0 ? '+' : ''}
            {stats.net.toLocaleString()}
          </p>
          <p className="text-xs text-muted-foreground mt-1">SOL lamports</p>
        </div>
      </div>

      {/* CuratorScore Breakdown */}
      <div className="bg-card rounded-lg border border-border p-6">
        <h2 className="text-2xl font-semibold mb-4">CuratorScore Breakdown</h2>
        <div className="space-y-4">
          {/* Overall Score */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <span className="font-medium">Overall Score</span>
              <span className="text-2xl font-bold">{stats.curatorScore}</span>
            </div>
          </div>

          {/* Calibration Rate */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <span className="text-sm text-muted-foreground">
                Calibration Rate (Golden Set Accuracy)
              </span>
              <span className="font-semibold">{(stats.calibrationRate * 100).toFixed(1)}%</span>
            </div>
            <div className="w-full bg-secondary rounded-full h-2.5">
              <div
                className="bg-blue-600 h-2.5 rounded-full"
                style={{ width: `${stats.calibrationRate * 100}%` }}
              ></div>
            </div>
          </div>

          {/* Audit Pass Rate */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <span className="text-sm text-muted-foreground">Audit Pass Rate</span>
              <span className="font-semibold">{(stats.auditPassRate * 100).toFixed(1)}%</span>
            </div>
            <div className="w-full bg-secondary rounded-full h-2.5">
              <div
                className="bg-green-600 h-2.5 rounded-full"
                style={{ width: `${stats.auditPassRate * 100}%` }}
              ></div>
            </div>
          </div>

          {/* Alignment Stability */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <span className="text-sm text-muted-foreground">Alignment Stability</span>
              <span className="font-semibold">
                {(stats.alignmentStability * 100).toFixed(1)}%
              </span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-full bg-secondary rounded-full h-2.5">
                <div
                  className="bg-purple-600 h-2.5 rounded-full"
                  style={{ width: `${stats.alignmentStability * 100}%` }}
                ></div>
              </div>
              {stats.alignmentStability >= 0.8 ? (
                <span className="text-green-500 text-sm">↑</span>
              ) : (
                <span className="text-red-500 text-sm">↓</span>
              )}
            </div>
          </div>

          {/* Fraud Flags */}
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">Fraud Flags</span>
            <span
              className={`font-semibold ${
                stats.fraudFlags === 0 ? 'text-green-500' : 'text-red-500'
              }`}
            >
              {stats.fraudFlags}
            </span>
          </div>
        </div>
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Base vs Premium Earnings */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Earnings Split</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Base vs Premium tier earnings
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={earningsData}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, percent }) => `${name}: ${((percent || 0) * 100).toFixed(0)}%`}
                outerRadius={80}
                dataKey="value"
              >
                {earningsData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.fill} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1f2937',
                  border: '1px solid #374151',
                  borderRadius: '0.375rem',
                }}
              />
            </PieChart>
          </ResponsiveContainer>
        </div>

        {/* Golden Set Accuracy Over Time */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Golden Set Accuracy</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Performance on calibration pairs
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={accuracyOverTimeData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="evaluation" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" domain={[0, 100]} />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1f2937',
                  border: '1px solid #374151',
                  borderRadius: '0.375rem',
                }}
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="accuracy"
                stroke="#2563eb"
                strokeWidth={2}
                name="Accuracy %"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Recent Evaluations Table */}
      <div className="bg-card rounded-lg border border-border p-6">
        <h2 className="text-2xl font-semibold mb-4">Recent Evaluations</h2>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">
                  Pair
                </th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">
                  Your Choice
                </th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">
                  Outcome
                </th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">
                  Reward/Loss
                </th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">
                  Time
                </th>
              </tr>
            </thead>
            <tbody>
              {evaluations.map((evalItem) => (
                <tr key={evalItem.id} className="border-b border-border hover:bg-secondary/50">
                  <td className="py-3 px-4 text-sm">{evalItem.pair}</td>
                  <td className="py-3 px-4 text-sm">{evalItem.choice}</td>
                  <td className="py-3 px-4 text-sm">
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                        evalItem.outcome === 'win'
                          ? 'bg-green-500/10 text-green-500'
                          : 'bg-red-500/10 text-red-500'
                      }`}
                    >
                      {evalItem.outcome === 'win' ? 'Win' : 'Loss'}
                    </span>
                  </td>
                  <td
                    className={`py-3 px-4 text-sm text-right font-semibold ${
                      evalItem.amount >= 0 ? 'text-green-500' : 'text-red-500'
                    }`}
                  >
                    {evalItem.amount >= 0 ? '+' : ''}
                    {evalItem.amount}
                  </td>
                  <td className="py-3 px-4 text-sm text-right text-muted-foreground">
                    {formatTimestamp(evalItem.timestamp)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Footer Reference */}
      <div className="text-center text-sm text-muted-foreground border-t border-border pt-6">
        <p>Reference: PRD Sections 4.6, 8.4, 11</p>
      </div>
    </div>
  )
}
