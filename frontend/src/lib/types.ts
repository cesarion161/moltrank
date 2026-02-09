// Core domain types

export interface Post {
  id: string
  content: string
  author: string
  timestamp: number
  eloRating: number
  category?: string
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
