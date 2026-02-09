'use client'

import { useMemo } from 'react'
import { sankey, sankeyLinkHorizontal, SankeyNode, SankeyLink } from 'd3-sankey'

interface PoolData {
  balance: number
  alpha: number
  totalSubscriptions: number
  totalSlashing: number
  totalMinorityLosses: number
  markets: Array<{
    id: number
    name: string
    baseRewards: number
    premiumRewards: number
  }>
}

interface SankeyNodeData {
  name: string
  value?: number
  color: string
}

interface SankeyLinkData {
  source: number
  target: number
  value: number
  color: string
}

interface PoolSankeyProps {
  data: PoolData
}

export function PoolSankey({ data }: PoolSankeyProps) {
  const { nodes, links } = useMemo(() => {
    // Calculate flows
    const poolInflow = data.totalSubscriptions
    const poolOutflow = data.totalSlashing + data.totalMinorityLosses
    const poolBalance = data.balance
    const totalDistributed = data.markets.reduce(
      (sum, m) => sum + m.baseRewards + m.premiumRewards,
      0
    )
    const totalBase = data.markets.reduce((sum, m) => sum + m.baseRewards, 0)
    const totalPremium = data.markets.reduce((sum, m) => sum + m.premiumRewards, 0)

    // Define nodes (vertical ordering matters for layout)
    const nodeData: SankeyNodeData[] = [
      { name: 'Reader Subscriptions', color: '#10b981' }, // 0
      { name: 'Slashing Penalties', color: '#ef4444' }, // 1
      { name: 'Minority Losses', color: '#f59e0b' }, // 2
      { name: 'GlobalPool', color: '#2563eb' }, // 3
      { name: `Base Rewards (${Math.round(data.alpha * 100)}%)`, color: '#06b6d4' }, // 4
      { name: `Market Premiums (${Math.round((1 - data.alpha) * 100)}%)`, color: '#f97316' }, // 5
      ...data.markets.map((m) => ({
        name: m.name,
        color: '#8b5cf6',
      })),
    ]

    // Define links (flows between nodes)
    const linkData: SankeyLinkData[] = [
      // Inflows to GlobalPool
      { source: 0, target: 3, value: poolInflow, color: '#10b981' }, // Subscriptions -> Pool
      // Outflows from GlobalPool (penalties)
      { source: 3, target: 1, value: data.totalSlashing, color: '#ef4444' }, // Pool -> Slashing
      { source: 3, target: 2, value: data.totalMinorityLosses, color: '#f59e0b' }, // Pool -> Minority
      // Pool split to Base/Premium
      { source: 3, target: 4, value: totalBase, color: '#06b6d4' }, // Pool -> Base
      { source: 3, target: 5, value: totalPremium, color: '#f97316' }, // Pool -> Premium
    ]

    // Add market distribution links
    data.markets.forEach((market, idx) => {
      const marketNodeIdx = 6 + idx
      if (market.baseRewards > 0) {
        linkData.push({
          source: 4,
          target: marketNodeIdx,
          value: market.baseRewards,
          color: '#06b6d4',
        })
      }
      if (market.premiumRewards > 0) {
        linkData.push({
          source: 5,
          target: marketNodeIdx,
          value: market.premiumRewards,
          color: '#f97316',
        })
      }
    })

    return { nodes: nodeData, links: linkData }
  }, [data])

  const { sankeyNodes, sankeyLinks } = useMemo(() => {
    const width = 1000
    const height = 600
    const nodeWidth = 15
    const nodePadding = 20

    const sankeyGenerator = sankey<SankeyNodeData, SankeyLinkData>()
      .nodeWidth(nodeWidth)
      .nodePadding(nodePadding)
      .extent([
        [1, 1],
        [width - 1, height - 5],
      ])

    const graph = sankeyGenerator({
      nodes: nodes.map((d) => ({ ...d })),
      links: links.map((d) => ({ ...d })),
    })

    return {
      sankeyNodes: graph.nodes,
      sankeyLinks: graph.links,
    }
  }, [nodes, links])

  return (
    <div className="w-full overflow-x-auto">
      <svg width="1000" height="600" className="mx-auto">
        <defs>
          {sankeyLinks.map((link, i) => (
            <linearGradient
              key={`gradient-${i}`}
              id={`gradient-${i}`}
              gradientUnits="userSpaceOnUse"
              x1={(link.source as SankeyNode<SankeyNodeData, SankeyLinkData>).x1}
              x2={(link.target as SankeyNode<SankeyNodeData, SankeyLinkData>).x0}
            >
              <stop offset="0%" stopColor={link.color} stopOpacity={0.3} />
              <stop offset="100%" stopColor={link.color} stopOpacity={0.3} />
            </linearGradient>
          ))}
        </defs>

        {/* Links */}
        <g>
          {sankeyLinks.map((link, i) => {
            const path = sankeyLinkHorizontal()(link)
            return (
              <g key={`link-${i}`}>
                <path
                  d={path || undefined}
                  fill="none"
                  stroke={`url(#gradient-${i})`}
                  strokeWidth={Math.max(1, link.width || 0)}
                  opacity={0.5}
                  className="hover:opacity-80 transition-opacity cursor-pointer"
                >
                  <title>
                    {(link.source as SankeyNode<SankeyNodeData, SankeyLinkData>).name} →{' '}
                    {(link.target as SankeyNode<SankeyNodeData, SankeyLinkData>).name}:{' '}
                    {link.value.toLocaleString()} tokens
                  </title>
                </path>
              </g>
            )
          })}
        </g>

        {/* Nodes */}
        <g>
          {sankeyNodes.map((node, i) => (
            <g key={`node-${i}`}>
              <rect
                x={node.x0}
                y={node.y0}
                width={node.x1! - node.x0!}
                height={node.y1! - node.y0!}
                fill={node.color}
                className="hover:opacity-80 transition-opacity cursor-pointer"
              >
                <title>
                  {node.name}: {node.value?.toLocaleString()} tokens
                </title>
              </rect>
              <text
                x={node.x0! < 500 ? node.x1! + 6 : node.x0! - 6}
                y={(node.y0! + node.y1!) / 2}
                dy="0.35em"
                textAnchor={node.x0! < 500 ? 'start' : 'end'}
                className="fill-foreground text-sm font-medium pointer-events-none"
              >
                {node.name}
              </text>
            </g>
          ))}
        </g>
      </svg>
    </div>
  )
}
