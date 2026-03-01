// API client for backend communication

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'

type ParsedApiErrorBody = {
  detail?: string
  message?: string
  error?: string
  title?: string
  fieldErrors?: Record<string, string>
}

type ApiRequestErrorInit = {
  url: string
  status: number
  statusText: string
  body: string
  detail?: string
  fieldErrors?: Record<string, string>
}

export class ApiRequestError extends Error {
  readonly url: string
  readonly status: number
  readonly statusText: string
  readonly body: string
  readonly detail?: string
  readonly fieldErrors?: Record<string, string>

  constructor(message: string, init: ApiRequestErrorInit) {
    super(message)
    this.name = 'ApiRequestError'
    this.url = init.url
    this.status = init.status
    this.statusText = init.statusText
    this.body = init.body
    this.detail = init.detail
    this.fieldErrors = init.fieldErrors
  }
}

export class ApiClient {
  private baseUrl: string

  constructor(baseUrl: string = API_BASE_URL) {
    this.baseUrl = baseUrl
  }

  private static parseErrorDetail(errorBody: string): { detail?: string; fieldErrors?: Record<string, string> } {
    if (!errorBody) {
      return {}
    }

    try {
      const parsed = JSON.parse(errorBody) as ParsedApiErrorBody
      return {
        detail: parsed.detail || parsed.message || parsed.error || parsed.title,
        fieldErrors: parsed.fieldErrors && typeof parsed.fieldErrors === 'object'
          ? parsed.fieldErrors
          : undefined,
      }
    } catch {
      return {}
    }
  }

  private async request<T>(
    endpoint: string,
    options?: RequestInit
  ): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`

    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          ...options?.headers,
        },
      })

      if (!response.ok) {
        const errorBody = await response.text().catch(() => 'Unable to read error response')
        const { detail: errorDetail, fieldErrors } = ApiClient.parseErrorDetail(errorBody)
        console.error(`API request failed:`, {
          url,
          status: response.status,
          statusText: response.statusText,
          body: errorBody,
          detail: errorDetail,
        })
        throw new ApiRequestError(
          `API request failed: ${response.status} ${response.statusText}`,
          {
            url,
            status: response.status,
            statusText: response.statusText,
            body: errorBody,
            detail: errorDetail,
            fieldErrors,
          }
        )
      }

      try {
        return await response.json()
      } catch (parseError) {
        if (response.status === 201 || response.status === 204) {
          return undefined as T
        }
        console.error(`Failed to parse JSON response from ${url}:`, parseError)
        throw new Error(`API request failed: invalid JSON response`)
      }
    } catch (error) {
      if (error instanceof ApiRequestError) {
        throw error
      }
      if (error instanceof Error && error.message.includes('API request failed')) {
        throw error
      }
      console.error(`Network error calling ${url}:`, error)
      throw new Error(`Network error: ${error instanceof Error ? error.message : 'Unknown error'}`)
    }
  }

  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' })
  }

  async post<T>(
    endpoint: string,
    data: unknown,
    options?: Omit<RequestInit, 'method' | 'body'>
  ): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  async put<T>(endpoint: string, data: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  }

  async delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' })
  }

  // Curator-specific endpoints
  async getCuratorProfile(wallet: string, marketId = 1): Promise<any> {
    return this.get(`/curators/${wallet}?marketId=${marketId}`)
  }

  async getCuratorStats(wallet: string, marketId = 1): Promise<any> {
    // Alias for getCuratorProfile for backward compatibility
    return this.getCuratorProfile(wallet, marketId)
  }

  async getCuratorEvaluations(wallet: string, limit = 10): Promise<any> {
    // TODO: Backend endpoint not yet implemented
    // This will need to be added to the backend or retrieved from the curator profile
    console.warn('getCuratorEvaluations: endpoint not yet implemented')
    return Promise.resolve([])
  }

  async getLeaderboard(marketId = 1, limit = 50): Promise<any> {
    return this.get(`/leaderboard?marketId=${marketId}&limit=${limit}`)
  }

  // Curation endpoints
  async getNextPair(wallet: string, marketId = 1): Promise<any> {
    return this.get(`/pairs/next?wallet=${wallet}&marketId=${marketId}`)
  }

  async commitVote(pairId: number, data: {
    wallet: string
    commitmentHash: string
    encryptedReveal: string
    revealIv: string
    signature: string
    signedAt: number
    requestNonce: string
    stakeAmount: number
  }): Promise<any> {
    return this.post(`/pairs/${pairId}/commit`, data)
  }

  async skipPair(pairId: number, wallet: string): Promise<any> {
    return this.post(`/pairs/${pairId}/skip`, { wallet })
  }

  async getActiveRound(marketId = 1): Promise<any> {
    return this.get(`/rounds/active?marketId=${marketId}`)
  }
}

export const apiClient = new ApiClient()
