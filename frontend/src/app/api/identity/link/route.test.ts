import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { POST } from './route'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

function makeRequest(body: Record<string, any>) {
  return new Request('http://localhost:3000/api/identity/link', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

describe('POST /api/identity/link', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns 400 when walletAddress is missing', async () => {
    const request = makeRequest({
      twitterUsername: 'testuser',
      twitterId: '123',
    }) as any

    const response = await POST(request)
    const data = await response.json()

    expect(response.status).toBe(400)
    expect(data.error).toBe('Missing required fields')
  })

  it('returns 400 when twitterUsername is missing', async () => {
    const request = makeRequest({
      walletAddress: '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
      twitterId: '123',
    }) as any

    const response = await POST(request)
    const data = await response.json()

    expect(response.status).toBe(400)
    expect(data.error).toBe('Missing required fields')
  })

  it('returns 400 when twitterId is missing', async () => {
    const request = makeRequest({
      walletAddress: '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
      twitterUsername: 'testuser',
    }) as any

    const response = await POST(request)
    const data = await response.json()

    expect(response.status).toBe(400)
    expect(data.error).toBe('Missing required fields')
  })

  it('forwards request to backend and returns success', async () => {
    const backendResponse = { success: true, identityId: 'id-123' }
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(backendResponse),
    })

    const request = makeRequest({
      walletAddress: '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
      twitterUsername: 'testuser',
      twitterId: '123456',
    }) as any

    const response = await POST(request)
    const data = await response.json()

    expect(response.status).toBe(200)
    expect(data).toEqual(backendResponse)
    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/identity/link',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          walletAddress: '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
          twitterUsername: 'testuser',
          twitterId: '123456',
        }),
      }
    )
  })

  it('returns backend error status when backend fails', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 409,
      json: () => Promise.resolve({ message: 'Identity already linked' }),
    })

    const request = makeRequest({
      walletAddress: '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
      twitterUsername: 'testuser',
      twitterId: '123456',
    }) as any

    const response = await POST(request)
    const data = await response.json()

    expect(response.status).toBe(409)
    expect(data.error).toBe('Identity already linked')
  })

  it('returns 500 on unexpected error', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const request = makeRequest({
      walletAddress: '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
      twitterUsername: 'testuser',
      twitterId: '123456',
    }) as any

    const response = await POST(request)
    const data = await response.json()

    expect(response.status).toBe(500)
    expect(data.error).toBe('Internal server error')
  })
})
