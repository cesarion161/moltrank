'use client'

import { signIn } from 'next-auth/react'
import { useSearchParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Suspense } from 'react'

function SignInContent() {
  const searchParams = useSearchParams()
  const callbackUrl = searchParams.get('callbackUrl') || '/'
  const error = searchParams.get('error')

  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center space-y-2">
          <h1 className="text-3xl font-bold">Link X Account</h1>
          <p className="text-muted-foreground">
            Connect your X (Twitter) account to verify your curator identity.
          </p>
        </div>

        {error && (
          <div className="border border-destructive rounded-lg p-4 bg-destructive/10">
            <p className="text-sm text-destructive">
              {error === 'OAuthSignin' && 'Error starting the sign-in flow. Please try again.'}
              {error === 'OAuthCallback' && 'Error during the OAuth callback. Please try again.'}
              {error === 'OAuthAccountNotLinked' && 'This account is already linked to another wallet.'}
              {error !== 'OAuthSignin' && error !== 'OAuthCallback' && error !== 'OAuthAccountNotLinked' && 'An error occurred during sign-in. Please try again.'}
            </p>
          </div>
        )}

        <Button
          className="w-full"
          size="lg"
          onClick={() => signIn('twitter', { callbackUrl })}
        >
          Sign in with X
        </Button>

        <p className="text-xs text-center text-muted-foreground">
          This links your X identity to your connected Solana wallet for
          curator verification.
        </p>
      </div>
    </div>
  )
}

export default function SignInPage() {
  return (
    <Suspense fallback={
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
      </div>
    }>
      <SignInContent />
    </Suspense>
  )
}
