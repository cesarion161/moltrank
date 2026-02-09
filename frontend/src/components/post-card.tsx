'use client'

import { useState } from 'react'
import type { Post } from '@/lib/types'

interface PostCardProps {
  post: Post
}

export function PostCard({ post }: PostCardProps) {
  const [isExpanded, setIsExpanded] = useState(false)

  const winRate = post.matchups > 0
    ? ((post.wins / post.matchups) * 100).toFixed(1)
    : '0.0'

  const contentPreview = post.content.length > 200 && !isExpanded
    ? post.content.slice(0, 200) + '...'
    : post.content

  return (
    <div
      className="border rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer bg-card"
      onClick={() => setIsExpanded(!isExpanded)}
    >
      <div className="flex justify-between items-start mb-2">
        <div>
          <h3 className="font-semibold text-lg">{post.agent}</h3>
          <div className="flex gap-4 text-sm text-muted-foreground mt-1">
            <span className="flex items-center gap-1">
              <span className="font-medium">ELO:</span>
              <span className="text-foreground font-semibold">{post.elo}</span>
            </span>
            <span className="flex items-center gap-1">
              <span className="font-medium">Win Rate:</span>
              <span className="text-foreground">{winRate}%</span>
            </span>
            <span className="flex items-center gap-1">
              <span className="font-medium">Matchups:</span>
              <span className="text-foreground">{post.matchups}</span>
            </span>
          </div>
        </div>
      </div>

      <p className="text-sm whitespace-pre-wrap mt-3">
        {contentPreview}
      </p>

      {post.content.length > 200 && (
        <button
          className="text-xs text-primary mt-2 hover:underline"
          onClick={(e) => {
            e.stopPropagation()
            setIsExpanded(!isExpanded)
          }}
        >
          {isExpanded ? 'Show less' : 'Show more'}
        </button>
      )}
    </div>
  )
}
