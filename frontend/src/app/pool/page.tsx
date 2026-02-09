'use client'

import { useState, useEffect } from 'react'
import { PoolSankey } from '@/components/pool-sankey'

interface GlobalPool {
  id: number
  balance: number
  alpha: number
  round?: {
    id: number
    status: string
    pairs: number
    basePerPair: number
    premiumPerPair: number
  }
  settlementHash?: string
  updatedAt: string
}

interface PoolHealth {
  pool: GlobalPool
  subscriptions: number
  slashing: number
  minorityLosses: number
  markets: Array<{
    id: number
    name: string
    baseRewards: number
    premiumRewards: number
  }>
}

export default function PoolPage() {
  const [data, setData] = useState<PoolHealth | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'

    // Fetch pool health data
    fetch(`${apiUrl}/pool`)
      .then((res) => {
        if (!res.ok) throw new Error('Failed to load pool data')
        return res.json()
      })
      .then((pool: GlobalPool) => {
        // For now, simulate additional data based on the pool balance and alpha
        // In production, this would come from additional API endpoints
        const simulatedData: PoolHealth = {
          pool,
          subscriptions: Math.floor(pool.balance * 0.15), // 15% of balance as new subscriptions
          slashing: Math.floor(pool.balance * 0.02), // 2% slashed
          minorityLosses: Math.floor(pool.balance * 0.03), // 3% minority losses
          markets: generateMarketData(pool),
        }
        setData(simulatedData)
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
          <p className="text-muted-foreground">Loading pool health data...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <p className="text-red-500 mb-2">Error loading pool data</p>
          <p className="text-sm text-muted-foreground">{error}</p>
        </div>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <p className="text-muted-foreground">No pool data available</p>
      </div>
    )
  }

  const { pool, subscriptions, slashing, minorityLosses, markets } = data
  const totalBase = markets.reduce((sum, m) => sum + m.baseRewards, 0)
  const totalPremium = markets.reduce((sum, m) => sum + m.premiumRewards, 0)
  const totalDistributed = totalBase + totalPremium

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="space-y-2">
        <h1 className="text-4xl font-bold">GlobalPool Health</h1>
        <p className="text-muted-foreground">
          Real-time token flow visualization showing subscriptions, penalties, and market
          distribution
        </p>
      </div>

      {/* Pool Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-card rounded-lg border border-border p-4">
          <div className="text-sm text-muted-foreground mb-1">Current Balance</div>
          <div className="text-2xl font-bold">{pool.balance.toLocaleString()} tokens</div>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <div className="text-sm text-muted-foreground mb-1">Alpha Parameter</div>
          <div className="text-2xl font-bold">{pool.alpha} (30% base)</div>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <div className="text-sm text-muted-foreground mb-1">Subscriptions (This Round)</div>
          <div className="text-2xl font-bold text-green-500">
            +{subscriptions.toLocaleString()}
          </div>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <div className="text-sm text-muted-foreground mb-1">
            Penalties (Slashing + Minority)
          </div>
          <div className="text-2xl font-bold text-red-500">
            -{(slashing + minorityLosses).toLocaleString()}
          </div>
        </div>
      </div>

      {/* Additional Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-card rounded-lg border border-border p-4">
          <div className="text-sm text-muted-foreground mb-1">Slashing Penalties</div>
          <div className="text-xl font-semibold text-red-500">
            {slashing.toLocaleString()} tokens
          </div>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <div className="text-sm text-muted-foreground mb-1">Minority Losses</div>
          <div className="text-xl font-semibold text-orange-500">
            {minorityLosses.toLocaleString()} tokens
          </div>
        </div>
        <div className="bg-card rounded-lg border border-border p-4">
          <div className="text-sm text-muted-foreground mb-1">Current Round</div>
          <div className="text-xl font-semibold">
            {pool.round ? `#${pool.round.id} (${pool.round.status})` : 'N/A'}
          </div>
        </div>
      </div>

      {/* Sankey Diagram */}
      <div className="bg-card rounded-lg border border-border p-6">
        <h2 className="text-2xl font-semibold mb-2">Token Flow Visualization</h2>
        <p className="text-sm text-muted-foreground mb-6">
          Sankey diagram showing how tokens flow from subscriptions through the GlobalPool,
          split by alpha parameter, and distributed to markets
        </p>
        <PoolSankey
          data={{
            balance: pool.balance,
            alpha: pool.alpha,
            totalSubscriptions: subscriptions,
            totalSlashing: slashing,
            totalMinorityLosses: minorityLosses,
            markets,
          }}
        />
        <div className="mt-4 flex gap-6 justify-center text-sm">
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 rounded" style={{ backgroundColor: '#10b981' }}></div>
            <span>Inflows</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 rounded" style={{ backgroundColor: '#06b6d4' }}></div>
            <span>Base Rewards (30%)</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 rounded" style={{ backgroundColor: '#f97316' }}></div>
            <span>Market Premiums (70%)</span>
          </div>
        </div>
      </div>

      {/* Per-Market Breakdown Table */}
      <div className="bg-card rounded-lg border border-border p-6">
        <h2 className="text-2xl font-semibold mb-4">Per-Market Distribution</h2>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left py-3 px-4 font-semibold">Market</th>
                <th className="text-right py-3 px-4 font-semibold">Base Rewards (30%)</th>
                <th className="text-right py-3 px-4 font-semibold">Market Premium (70%)</th>
                <th className="text-right py-3 px-4 font-semibold">Total</th>
              </tr>
            </thead>
            <tbody>
              {markets.map((market) => (
                <tr key={market.id} className="border-b border-border last:border-0">
                  <td className="py-3 px-4">{market.name}</td>
                  <td className="text-right py-3 px-4 text-cyan-400">
                    {market.baseRewards.toLocaleString()}
                  </td>
                  <td className="text-right py-3 px-4 text-orange-400">
                    {market.premiumRewards.toLocaleString()}
                  </td>
                  <td className="text-right py-3 px-4 font-semibold">
                    {(market.baseRewards + market.premiumRewards).toLocaleString()}
                  </td>
                </tr>
              ))}
              <tr className="font-bold">
                <td className="py-3 px-4">Total Distributed</td>
                <td className="text-right py-3 px-4 text-cyan-400">
                  {totalBase.toLocaleString()}
                </td>
                <td className="text-right py-3 px-4 text-orange-400">
                  {totalPremium.toLocaleString()}
                </td>
                <td className="text-right py-3 px-4">{totalDistributed.toLocaleString()}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Footer Note */}
      <div className="text-center text-sm text-muted-foreground border-t border-border pt-6">
        <p>Reference: PRD Sections 4.1, 8.7, 11</p>
      </div>
    </div>
  )
}

// Helper function to generate simulated market data
// In production, this would come from the backend API
function generateMarketData(pool: GlobalPool): Array<{
  id: number
  name: string
  baseRewards: number
  premiumRewards: number
}> {
  // Simulate 5 markets with varying distributions
  const marketNames = [
    'Bitcoin Market',
    'Ethereum Market',
    'Tech Stocks',
    'Crypto News',
    'DeFi Protocols',
  ]

  const totalDistributable = Math.floor(pool.balance * 0.1) // 10% distributed this round
  const baseTotal = Math.floor(totalDistributable * pool.alpha)
  const premiumTotal = totalDistributable - baseTotal

  return marketNames.map((name, idx) => {
    // Varying distribution weights
    const weight = [0.3, 0.25, 0.2, 0.15, 0.1][idx]
    return {
      id: idx + 1,
      name,
      baseRewards: Math.floor(baseTotal * weight),
      premiumRewards: Math.floor(premiumTotal * weight),
    }
  })
}
