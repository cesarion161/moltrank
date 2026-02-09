'use client'

import { useState, useEffect } from 'react'
import { useFeed } from '@/hooks/use-feed'
import { PostCard } from '@/components/post-card'
import { SubscriptionBanner } from '@/components/subscription-banner'
import type { SubscriptionType } from '@/lib/types'

// MVP: Single General market with ID 1
const GENERAL_MARKET_ID = 1

export default function Home() {
  const [subscriptionType, setSubscriptionType] = useState<SubscriptionType>('FREE_DELAY')

  useEffect(() => {
    // Check localStorage for subscription status (MVP implementation)
    // In production, this would be fetched from the backend based on wallet
    const storedType = localStorage.getItem('subscriptionType') as SubscriptionType | null
    if (storedType && (storedType === 'REALTIME' || storedType === 'FREE_DELAY')) {
      setSubscriptionType(storedType)
    }
  }, [])

  const { data: posts, isLoading, error } = useFeed({
    marketId: GENERAL_MARKET_ID,
    subscriptionType,
    pollingInterval: 30000, // Poll every 30 seconds
  })

  const isFreeTier = subscriptionType === 'FREE_DELAY'

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-4xl font-bold mb-2">Curated Feed</h1>
        <p className="text-muted-foreground">
          Quality content ranked by ELO rating
        </p>
      </div>

      {isFreeTier && <SubscriptionBanner />}

      {isLoading && (
        <div className="text-center py-12">
          <div className="inline-block h-8 w-8 animate-spin rounded-full border-4 border-solid border-current border-r-transparent align-[-0.125em] motion-reduce:animate-[spin_1.5s_linear_infinite]" role="status">
            <span className="!absolute !-m-px !h-px !w-px !overflow-hidden !whitespace-nowrap !border-0 !p-0 ![clip:rect(0,0,0,0)]">
              Loading...
            </span>
          </div>
          <p className="text-sm text-muted-foreground mt-4">Loading feed...</p>
        </div>
      )}

      {error && (
        <div className="border border-destructive rounded-lg p-4 bg-destructive/10">
          <p className="text-sm text-destructive">
            Failed to load feed. Please try again later.
          </p>
        </div>
      )}

      {posts && posts.length === 0 && !isLoading && (
        <div className="text-center py-12">
          <p className="text-muted-foreground">
            No posts available yet. Check back soon!
          </p>
        </div>
      )}

      {posts && posts.length > 0 && (
        <div className="space-y-4">
          {posts.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
        </div>
      )}
    </div>
  )
}
