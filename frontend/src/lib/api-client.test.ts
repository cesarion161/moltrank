import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ApiClient, ApiRequestError } from './api-client'

describe('ApiClient', () => {
  let client: ApiClient
  const mockFetch = vi.fn()

  beforeEach(() => {
    client = new ApiClient('http://localhost:8080/api')
    globalThis.fetch = mockFetch
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('request basics', () => {
    it('sends GET request with correct URL and headers', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ data: 'test' }),
      })

      await client.get('/test-endpoint')

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/test-endpoint',
        expect.objectContaining({
          method: 'GET',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
        })
      )
    })

    it('sends POST request with JSON body', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ id: 1 }),
      })

      await client.post('/items', { name: 'test' })

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/items',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ name: 'test' }),
        })
      )
    })

    it('supports custom headers for POST retries', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ id: 2 }),
      })

      await client.post('/clawgic/tournaments/test/enter', { agentId: 'agent-1' }, {
        headers: {
          'X-PAYMENT': '{"proof":"signed"}',
        },
      })

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/clawgic/tournaments/test/enter',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ agentId: 'agent-1' }),
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
            'X-PAYMENT': '{"proof":"signed"}',
          }),
        })
      )
    })

    it('sends PUT request with JSON body', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ id: 1 }),
      })

      await client.put('/items/1', { name: 'updated' })

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/items/1',
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify({ name: 'updated' }),
        })
      )
    })

    it('sends DELETE request', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({}),
      })

      await client.delete('/items/1')

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/items/1',
        expect.objectContaining({ method: 'DELETE' })
      )
    })

    it('handles 201 response with empty body', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
      })

      const result = await client.post('/pairs/1/commit', { example: true })

      expect(result).toBeUndefined()
    })
  })

  describe('error handling', () => {
    it('throws on non-OK response with status details', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        text: () => Promise.resolve('Resource not found'),
      })

      await expect(client.get('/missing')).rejects.toThrow(
        'API request failed: 404 Not Found'
      )
    })

    it('throws ApiRequestError with parsed problem detail when provided', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 409,
        statusText: 'Conflict',
        text: () =>
          Promise.resolve(
            JSON.stringify({
              type: 'about:blank',
              title: 'Conflict',
              status: 409,
              detail: 'Tournament entry capacity reached',
            })
          ),
      })

      await expect(client.post('/clawgic/tournaments/abc/enter', { agentId: '123' })).rejects.toMatchObject({
        name: 'ApiRequestError',
        status: 409,
        statusText: 'Conflict',
        detail: 'Tournament entry capacity reached',
      })
    })

    it('parses fieldErrors from 400 validation response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        text: () =>
          Promise.resolve(
            JSON.stringify({
              detail: 'Validation failed: name is required',
              fieldErrors: {
                name: 'name is required',
                walletAddress: 'walletAddress must be a valid 0x-prefixed EVM address',
              },
            })
          ),
      })

      await expect(client.post('/clawgic/agents', {})).rejects.toMatchObject({
        name: 'ApiRequestError',
        status: 400,
        detail: 'Validation failed: name is required',
        fieldErrors: {
          name: 'name is required',
          walletAddress: 'walletAddress must be a valid 0x-prefixed EVM address',
        },
      })
    })

    it('sets fieldErrors to undefined when not present in error response', async () => {
      expect.assertions(2)
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        text: () =>
          Promise.resolve(JSON.stringify({ detail: 'Something went wrong' })),
      })

      try {
        await client.post('/clawgic/agents', {})
      } catch (error) {
        expect(error).toBeInstanceOf(ApiRequestError)
        if (error instanceof ApiRequestError) {
          expect(error.fieldErrors).toBeUndefined()
        }
      }
    })

    it('throws ApiRequestError with undefined detail for non-json response body', async () => {
      expect.assertions(3)
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: () => Promise.resolve('plain-text failure'),
      })

      try {
        await client.get('/plain-error')
        throw new Error('Expected ApiRequestError to be thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiRequestError)
        if (error instanceof ApiRequestError) {
          expect(error.detail).toBeUndefined()
          expect(error.body).toBe('plain-text failure')
        }
      }
    })

    it('logs error details on non-OK response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: () => Promise.resolve('Server error body'),
      })

      await expect(client.get('/broken')).rejects.toThrow()

      expect(console.error).toHaveBeenCalledWith(
        'API request failed:',
        expect.objectContaining({
          url: 'http://localhost:8080/api/broken',
          status: 500,
          body: 'Server error body',
        })
      )
    })

    it('throws network error on fetch failure', async () => {
      mockFetch.mockRejectedValueOnce(new TypeError('Failed to fetch'))

      await expect(client.get('/endpoint')).rejects.toThrow(
        'Network error: Failed to fetch'
      )
    })

    it('handles error body read failure gracefully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 502,
        statusText: 'Bad Gateway',
        text: () => Promise.reject(new Error('stream error')),
      })

      await expect(client.get('/proxy')).rejects.toThrow(
        'API request failed: 502 Bad Gateway'
      )

      expect(console.error).toHaveBeenCalledWith(
        'API request failed:',
        expect.objectContaining({
          body: 'Unable to read error response',
        })
      )
    })
  })

  describe('curator endpoints', () => {
    it('getCuratorProfile fetches correct endpoint with default marketId', async () => {
      const mockProfile = {
        wallet: 'abc123',
        earned: 1000,
        lost: 200,
        curatorScore: '0.85',
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockProfile),
      })

      const result = await client.getCuratorProfile('abc123')

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/curators/abc123?marketId=1',
        expect.any(Object)
      )
      expect(result).toEqual(mockProfile)
    })

    it('getCuratorProfile uses custom marketId', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({}),
      })

      await client.getCuratorProfile('wallet1', 5)

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/curators/wallet1?marketId=5',
        expect.any(Object)
      )
    })

    it('getCuratorStats is an alias for getCuratorProfile', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ wallet: 'test' }),
      })

      const result = await client.getCuratorStats('test')

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/curators/test?marketId=1',
        expect.any(Object)
      )
      expect(result).toEqual({ wallet: 'test' })
    })

    it('getCuratorEvaluations returns empty array (not yet implemented)', async () => {
      const result = await client.getCuratorEvaluations('wallet1')

      expect(result).toEqual([])
      expect(console.warn).toHaveBeenCalledWith(
        'getCuratorEvaluations: endpoint not yet implemented'
      )
      expect(mockFetch).not.toHaveBeenCalled()
    })

    it('getLeaderboard fetches correct endpoint', async () => {
      const mockLeaderboard = [
        { wallet: 'a', curatorScore: 0.9 },
        { wallet: 'b', curatorScore: 0.8 },
      ]

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockLeaderboard),
      })

      const result = await client.getLeaderboard(2, 25)

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/leaderboard?marketId=2&limit=25',
        expect.any(Object)
      )
      expect(result).toEqual(mockLeaderboard)
    })

    it('getLeaderboard uses default params', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve([]),
      })

      await client.getLeaderboard()

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/leaderboard?marketId=1&limit=50',
        expect.any(Object)
      )
    })

    it('commitVote posts frontend payload contract to commit endpoint', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 201,
        json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
      })

      await client.commitVote(7, {
        wallet: 'wallet-123',
        commitmentHash: '0xabc',
        encryptedReveal: 'ciphertext',
        revealIv: 'iv',
        signature: 'sig',
        signedAt: 1730000000,
        requestNonce: '00112233445566778899aabbccddeeff',
        stakeAmount: 42,
      })

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/pairs/7/commit',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            wallet: 'wallet-123',
            commitmentHash: '0xabc',
            encryptedReveal: 'ciphertext',
            revealIv: 'iv',
            signature: 'sig',
            signedAt: 1730000000,
            requestNonce: '00112233445566778899aabbccddeeff',
            stakeAmount: 42,
          }),
        })
      )
    })

    it('skipPair posts wallet payload to skip endpoint', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 204,
        json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
      })

      await client.skipPair(7, 'wallet-123')

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/pairs/7/skip',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            wallet: 'wallet-123',
          }),
        })
      )
    })

    it('getActiveRound fetches active round with default marketId', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ id: 1, roundId: 1, status: 'COMMIT' }),
      })

      await client.getActiveRound()

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/rounds/active?marketId=1',
        expect.any(Object)
      )
    })

    it('getActiveRound uses custom marketId', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ id: 2, roundId: 2, status: 'OPEN' }),
      })

      await client.getActiveRound(2)

      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/rounds/active?marketId=2',
        expect.any(Object)
      )
    })
  })

  describe('constructor', () => {
    it('uses provided base URL', () => {
      const customClient = new ApiClient('https://custom.api/v2')

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({}),
      })

      customClient.get('/test')

      expect(mockFetch).toHaveBeenCalledWith(
        'https://custom.api/v2/test',
        expect.any(Object)
      )
    })
  })
})
