'use client'

import { useState, useEffect } from 'react'
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'

// Type definitions for chart data
interface PoolBalanceData {
  round: number
  balance: number
}

interface CumulativePnLData {
  round: number
  agent_type: string
  cumulative_pnl: number
}

interface NewMarketBootstrapData {
  round: number
  curators_attracted: number
}

interface AlphaSensitivityData {
  alpha: number
  pool_balance: number
  avg_curator_rewards: number
}

interface MinorityLossSensitivityData {
  minority_loss_pct: number
  consensus_alignment: number
  avg_pnl_loss: number
}

interface AuditDetectionData {
  evaluations: number
  lazy_detection_rate: number
  bot_detection_rate: number
}

interface BotVsHumanData {
  bot_percentage: number
  human_avg_earnings: number
  bot_avg_earnings: number
}

interface FeedQualityData {
  round: number
  scenario: string
  elo_stability: number
}

interface SimulationData {
  chart_1_pool_balance: PoolBalanceData[]
  chart_2_cumulative_pnl: CumulativePnLData[]
  chart_3_new_market_bootstrap: NewMarketBootstrapData[]
  chart_4a_alpha_sensitivity: AlphaSensitivityData[]
  chart_4b_minority_loss_sensitivity: MinorityLossSensitivityData[]
  chart_5_audit_detection: AuditDetectionData[]
  chart_6_bot_vs_human: BotVsHumanData[]
  chart_7_feed_quality: FeedQualityData[]
}

export default function SimulationPage() {
  const [data, setData] = useState<SimulationData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedScenario, setSelectedScenario] = useState<string>('baseline')

  useEffect(() => {
    // Load simulation results from JSON file
    fetch('/simulation/results.json')
      .then((res) => {
        if (!res.ok) throw new Error('Failed to load simulation data')
        return res.json()
      })
      .then((jsonData) => {
        setData(jsonData)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
      })
  }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4"></div>
          <p className="text-muted-foreground">Loading simulation data...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <p className="text-red-500 mb-2">Error loading simulation data</p>
          <p className="text-sm text-muted-foreground">{error}</p>
        </div>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <p className="text-muted-foreground">No simulation data available</p>
      </div>
    )
  }

  // Get unique scenarios from feed quality data
  const scenarios = Array.from(
    new Set(data.chart_7_feed_quality.map((d) => d.scenario))
  )

  // Transform cumulative PnL data for the selected scenario
  const pnlDataByType: { [key: string]: { round: number; cumulative_pnl: number }[] } = {}
  data.chart_2_cumulative_pnl.forEach((item) => {
    if (!pnlDataByType[item.agent_type]) {
      pnlDataByType[item.agent_type] = []
    }
    pnlDataByType[item.agent_type].push({
      round: item.round,
      cumulative_pnl: item.cumulative_pnl,
    })
  })

  // Combine PnL data for multi-line chart
  const combinedPnLData: any[] = []
  const rounds = Array.from(new Set(data.chart_2_cumulative_pnl.map((d) => d.round))).sort(
    (a, b) => a - b
  )
  rounds.forEach((round) => {
    const dataPoint: any = { round }
    Object.keys(pnlDataByType).forEach((agentType) => {
      const item = pnlDataByType[agentType].find((d) => d.round === round)
      if (item) {
        dataPoint[agentType] = item.cumulative_pnl
      }
    })
    combinedPnLData.push(dataPoint)
  })

  // Filter feed quality data by selected scenario
  const selectedFeedQualityData = data.chart_7_feed_quality.filter(
    (d) => d.scenario === selectedScenario
  )

  const agentColors = {
    honest: '#10b981',
    random: '#f59e0b',
    lazy: '#ef4444',
    colluder: '#8b5cf6',
    bot: '#06b6d4',
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="space-y-2">
        <h1 className="text-4xl font-bold">Simulation Playback</h1>
        <p className="text-muted-foreground">
          Reproducible results from 10,000-round simulation proving system integrity
        </p>
      </div>

      {/* Scenario Selector */}
      <div className="flex items-center gap-4">
        <label className="text-sm font-medium">Scenario:</label>
        <select
          value={selectedScenario}
          onChange={(e) => setSelectedScenario(e.target.value)}
          className="px-4 py-2 bg-secondary text-foreground rounded-md border border-border focus:outline-none focus:ring-2 focus:ring-primary"
        >
          {scenarios.map((scenario) => (
            <option key={scenario} value={scenario}>
              {scenario.replace(/_/g, ' ').replace(/\b\w/g, (l) => l.toUpperCase())}
            </option>
          ))}
        </select>
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Chart 1: GlobalPool Balance Over Time */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">GlobalPool Balance Over Time</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Solvency proof: Pool balance remains stable over 10K rounds
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={data.chart_1_pool_balance}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="round" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
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
                dataKey="balance"
                stroke="#2563eb"
                strokeWidth={2}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Chart 2: Cumulative PnL by Agent Type */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Cumulative PnL by Agent Type</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Expected hierarchy: Honest &gt; Random &gt; Lazy &gt; Colluder
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={combinedPnLData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="round" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1f2937',
                  border: '1px solid #374151',
                  borderRadius: '0.375rem',
                }}
              />
              <Legend />
              {Object.keys(pnlDataByType).map((agentType) => (
                <Line
                  key={agentType}
                  type="monotone"
                  dataKey={agentType}
                  stroke={agentColors[agentType as keyof typeof agentColors] || '#6b7280'}
                  strokeWidth={2}
                  dot={false}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Chart 3: New Market Bootstrap Curve */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">New Market Bootstrap Curve</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Curators attracted to new market over time
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={data.chart_3_new_market_bootstrap}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="round" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
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
                dataKey="curators_attracted"
                stroke="#10b981"
                strokeWidth={2}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Chart 4a: Alpha Sensitivity */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Alpha Sensitivity Analysis</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Impact of alpha parameter on pool balance and curator rewards
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={data.chart_4a_alpha_sensitivity}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="alpha" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
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
                dataKey="pool_balance"
                stroke="#2563eb"
                strokeWidth={2}
                name="Pool Balance"
              />
              <Line
                type="monotone"
                dataKey="avg_curator_rewards"
                stroke="#10b981"
                strokeWidth={2}
                name="Avg Curator Rewards"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Chart 4b: Minority Loss Sensitivity */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Minority Loss Sensitivity</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Effect of minority loss percentage on consensus and PnL
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={data.chart_4b_minority_loss_sensitivity}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="minority_loss_pct" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
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
                dataKey="consensus_alignment"
                stroke="#8b5cf6"
                strokeWidth={2}
                name="Consensus Alignment"
              />
              <Line
                type="monotone"
                dataKey="avg_pnl_loss"
                stroke="#ef4444"
                strokeWidth={2}
                name="Avg PnL Loss"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Chart 5: Audit Pair Detection Rate */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Audit Pair Detection Rate</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Evaluations needed to detect lazy/bot curators
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={data.chart_5_audit_detection}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="evaluations" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
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
                dataKey="lazy_detection_rate"
                stroke="#ef4444"
                strokeWidth={2}
                name="Lazy Detection"
              />
              <Line
                type="monotone"
                dataKey="bot_detection_rate"
                stroke="#06b6d4"
                strokeWidth={2}
                name="Bot Detection"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        {/* Chart 6: Bot vs Human Earnings */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Bot vs Human Earnings</h2>
          <p className="text-sm text-muted-foreground mb-4">
            Impact of bot infiltration on earnings
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={data.chart_6_bot_vs_human}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="bot_percentage" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1f2937',
                  border: '1px solid #374151',
                  borderRadius: '0.375rem',
                }}
              />
              <Legend />
              <Bar dataKey="human_avg_earnings" fill="#10b981" name="Human Curators" />
              <Bar dataKey="bot_avg_earnings" fill="#06b6d4" name="Bot Curators" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Chart 7: Feed Quality / ELO Stability */}
        <div className="bg-card rounded-lg border border-border p-6">
          <h2 className="text-xl font-semibold mb-2">Feed Quality / ELO Stability</h2>
          <p className="text-sm text-muted-foreground mb-4">
            ELO stability over time for {selectedScenario.replace(/_/g, ' ')} scenario
          </p>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={selectedFeedQualityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="round" stroke="#9ca3af" />
              <YAxis stroke="#9ca3af" />
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
                dataKey="elo_stability"
                stroke="#10b981"
                strokeWidth={2}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Footer Note */}
      <div className="text-center text-sm text-muted-foreground border-t border-border pt-6">
        <p>
          All data generated from reproducible simulation runs. Not screenshot artifacts.
        </p>
        <p className="mt-1">Reference: PRD Sections 6.4, 8.5, 11</p>
      </div>
    </div>
  )
}
