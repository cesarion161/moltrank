'use client'

import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/lib/api-client'
import type { Post, SubscriptionType } from '@/lib/types'

interface UseFeedOptions {
  marketId: number
  subscriptionType: SubscriptionType
  pollingInterval?: number
}

export function useFeed({
  marketId,
  subscriptionType,
  pollingInterval = 30000
}: UseFeedOptions) {
  const feedType = subscriptionType === 'REALTIME' ? 'realtime' : 'delayed'

  return useQuery({
    queryKey: ['feed', marketId, feedType],
    queryFn: async () => {
      return apiClient.get<Post[]>(`/feed?marketId=${marketId}&type=${feedType}`)
    },
    refetchInterval: pollingInterval,
    refetchIntervalInBackground: false,
    staleTime: 10000,
  })
}
