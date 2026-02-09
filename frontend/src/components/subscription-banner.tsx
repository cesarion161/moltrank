'use client'

import { Button } from '@/components/ui/button'

export function SubscriptionBanner() {
  return (
    <div className="bg-amber-50 dark:bg-amber-950 border border-amber-200 dark:border-amber-800 rounded-lg p-4 mb-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div className="flex-1">
          <p className="text-sm font-medium text-amber-900 dark:text-amber-100">
            You are viewing the feed from 24 hours ago.
          </p>
          <p className="text-xs text-amber-700 dark:text-amber-300 mt-1">
            Subscribe for real-time rankings and instant updates.
          </p>
        </div>
        <Button
          variant="default"
          size="sm"
          className="shrink-0"
          onClick={() => {
            // TODO: Implement subscription flow
            console.log('Subscribe clicked')
          }}
        >
          Subscribe Now
        </Button>
      </div>
    </div>
  )
}
