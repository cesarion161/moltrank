'use client'

import { useWallet } from '@solana/wallet-adapter-react'
import { signIn, useSession } from 'next-auth/react'
import { Button } from '@/components/ui/button'
import { useEffect, useState } from 'react'

export function LinkTwitter() {
  const { publicKey } = useWallet()
  const { data: session, status } = useSession()
  const [isLinking, setIsLinking] = useState(false)
  const [linkedTwitter, setLinkedTwitter] = useState<string | null>(null)

  useEffect(() => {
    // If user just authenticated with Twitter, link the identity
    if (session?.user?.account && publicKey && !linkedTwitter) {
      linkIdentity()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, publicKey, linkedTwitter])

  const linkIdentity = async () => {
    if (!publicKey || !session?.user?.account) return

    setIsLinking(true)
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
        throw new Error('Failed to link identity')
      }

      setLinkedTwitter(session.user.account.username)
    } catch (error) {
      console.error('Error linking identity:', error)
      alert('Failed to link X account. Please try again.')
    } finally {
      setIsLinking(false)
    }
  }

  const handleLinkClick = () => {
    if (!publicKey) {
      alert('Please connect your wallet first')
      return
    }
    signIn('twitter')
  }

  if (!publicKey) {
    return null
  }

  if (linkedTwitter) {
    return (
      <div className="flex items-center gap-2 text-sm">
        <span className="text-muted-foreground">X Account:</span>
        <span className="font-medium">@{linkedTwitter}</span>
        <span className="text-green-500">✓</span>
      </div>
    )
  }

  if (isLinking) {
    return (
      <Button variant="outline" size="sm" disabled>
        Linking...
      </Button>
    )
  }

  return (
    <Button variant="outline" size="sm" onClick={handleLinkClick}>
      Link X Account
    </Button>
  )
}
