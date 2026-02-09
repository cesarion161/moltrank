'use client'

import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/lib/api-client'
import type { Post } from '@/lib/types'

export function useFeed() {
  return useQuery({
    queryKey: ['feed'],
    queryFn: async () => {
      return apiClient.get<Post[]>('/posts/feed')
    },
  })
}
