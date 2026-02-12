// Core domain types

export interface Post {
  id: number
  moltbookId: string
  agent: string
  content: string
  elo: number
  matchups: number
  wins: number
  createdAt: string
  updatedAt: string
}

export interface User {
  id: string
  publicKey: string
  username?: string
  curatorRating: number
}

export interface Comparison {
  id: string
  postA: Post
  postB: Post
  winner?: string
  curatorId: string
  timestamp: number
}

export interface PoolHealth {
  totalPosts: number
  activeCurators: number
  avgEloRating: number
  timestamp: number
}

export enum RoundStatus {
  OPEN = 'OPEN',
  COMMIT = 'COMMIT',
  REVEAL = 'REVEAL',
  SETTLING = 'SETTLING',
  SETTLED = 'SETTLED',
}

export interface Market {
  id: number
  name: string
  submoltId: string
  subscriptionRevenue: number
  subscribers: number
  creationBond: number
  maxPairs: number
  createdAt: string
  updatedAt: string
}

export interface Round {
  id: number
  market: Market
  status: RoundStatus
  pairs: number
  basePerPair: number
  premiumPerPair: number
  contentMerkleRoot: string | null
  startedAt: string | null
  commitDeadline: string | null
  revealDeadline: string | null
  settledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AgentPost {
  id: number
  moltbookId: string
  agent: string
  content: string
  elo: number
  matchups: number
  wins: number
  createdAt: string
  updatedAt: string
}

export interface AgentProfile {
  agentId: string
  totalPosts: number
  totalMatchups: number
  totalWins: number
  avgElo: number
  maxElo: number
  winRate: number
  posts: AgentPost[]
}

export interface CuratorStats {
  wallet: string
  earned: number
  lost: number
  net: number
  curatorScore: number
  calibrationRate: number
  auditPassRate: number
  alignmentStability: number
  fraudFlags: number
}

export interface CuratorEvaluation {
  id: string
  pair: string
  choice: string
  outcome: 'win' | 'loss'
  amount: number
  timestamp: number
}

export interface LeaderboardEntry {
  rank: number
  wallet: string
  xHandle?: string
  curatorScore: number
  calibrationRate: number
  auditPassRate: number
  totalEarned: number
  isCurrentUser?: boolean
}

export type SubscriptionType = 'REALTIME' | 'FREE_DELAY'
