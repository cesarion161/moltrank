'use client'

import { useWallet } from '@solana/wallet-adapter-react'
import { signIn, useSession } from 'next-auth/react'
import { Button } from '@/components/ui/button'
import { VerifiedBadge } from '@/components/verified-badge'
import { useIdentity } from '@/hooks/use-identity'
import { useEffect, useState } from 'react'

export function LinkTwitter() {
  const { publicKey } = useWallet()
  const { data: session } = useSession()
  const { hasTwitterLinked, twitterUsername, isLoading: identityLoading, reload } = useIdentity()
  const [isLinking, setIsLinking] = useState(false)
  const [linkError, setLinkError] = useState<string | null>(null)

  useEffect(() => {
    // If user just authenticated with Twitter and wallet is connected,
    // auto-link the identity
    if (session?.user?.account && publicKey && !hasTwitterLinked && !isLinking) {
      linkIdentity()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, publicKey, hasTwitterLinked])

  const linkIdentity = async () => {
    if (!publicKey || !session?.user?.account) return

    setIsLinking(true)
    setLinkError(null)
    try {
      const response = await fetch('/api/identity/link', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          walletAddress: publicKey.toBase58(),
          twitterUsername: session.user.account.username,
          twitterId: session.user.account.providerAccountId,
        }),
      })

      if (!response.ok) {
        const data = await response.json().catch(() => ({}))
        throw new Error(data.error || 'Failed to link identity')
      }

      // Reload user identity to reflect the linked state
      await reload()
    } catch (error) {
      console.error('Error linking identity:', error)
      setLinkError(error instanceof Error ? error.message : 'Failed to link X account')
    } finally {
      setIsLinking(false)
    }
  }

  const handleLinkClick = () => {
    if (!publicKey) {
      setLinkError('Please connect your wallet first')
      return
    }
    setLinkError(null)
    signIn('twitter')
  }

  if (!publicKey) {
    return null
  }

  if (identityLoading) {
    return (
      <div className="h-9 w-24 animate-pulse rounded-md bg-secondary" />
    )
  }

  if (hasTwitterLinked && twitterUsername) {
    return <VerifiedBadge twitterUsername={twitterUsername} />
  }

  if (isLinking) {
    return (
      <Button variant="outline" size="sm" disabled>
        Linking...
      </Button>
    )
  }

  return (
    <div className="flex items-center gap-2">
      <Button variant="outline" size="sm" onClick={handleLinkClick}>
        Link X Account
      </Button>
      {linkError && (
        <span className="text-xs text-destructive max-w-[200px] truncate" title={linkError}>
          {linkError}
        </span>
      )}
    </div>
  )
}
