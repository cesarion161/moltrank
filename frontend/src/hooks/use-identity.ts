'use client'

import { useWallet } from '@solana/wallet-adapter-react'
import { useSession } from 'next-auth/react'
import { useCallback, useEffect, useState } from 'react'
import { User } from '@/lib/types'

export function useIdentity() {
  const { publicKey, connected } = useWallet()
  const { data: session } = useSession()
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const walletAddress = publicKey?.toBase58() ?? null

  const loadUser = useCallback(async () => {
    if (!walletAddress) return

    setIsLoading(true)
    setError(null)
    try {
      const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'
      const response = await fetch(`${apiUrl}/users/${walletAddress}`)

      if (response.ok) {
        const userData = await response.json()
        setUser(userData)
      } else if (response.status === 404) {
        // User not registered yet - not an error
        setUser(null)
      } else {
        setError('Failed to load user profile')
      }
    } catch (err) {
      console.error('Error loading user:', err)
      setError('Network error loading user profile')
    } finally {
      setIsLoading(false)
    }
  }, [walletAddress])

  useEffect(() => {
    if (walletAddress && connected) {
      loadUser()
    } else {
      setUser(null)
      setError(null)
    }
  }, [walletAddress, connected, loadUser])

  return {
    user,
    isLoading,
    error,
    publicKey,
    connected,
    walletAddress,
    hasTwitterLinked: !!user?.twitterUsername,
    twitterUsername: user?.twitterUsername,
    isVerified: user?.identityVerified,
    session,
    reload: loadUser,
  }
}
