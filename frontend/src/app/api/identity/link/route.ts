import { NextRequest, NextResponse } from 'next/server'

export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const { walletAddress, twitterUsername, twitterId } = body

    if (!walletAddress || !twitterUsername || !twitterId) {
      return NextResponse.json(
        { error: 'Missing required fields' },
        { status: 400 }
      )
    }

    // Call backend to register identity linkage
    const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'
    const backendPayload = {
      wallet: walletAddress,
      xAccount: twitterUsername,
      verified: true,
    }

    const response = await fetch(`${backendUrl}/identity/link`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(backendPayload),
    })

    if (!response.ok) {
      const error = await response.json()
      return NextResponse.json(
        { error: error.message || 'Failed to link identity' },
        { status: response.status }
      )
    }

    const data = await response.json()
    return NextResponse.json(data)
  } catch (error) {
    console.error('Identity linking error:', error)
    return NextResponse.json(
      { error: 'Internal server error' },
      { status: 500 }
    )
  }
}
