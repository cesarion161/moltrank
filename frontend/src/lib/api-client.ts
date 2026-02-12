// API client for backend communication

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'

export class ApiClient {
  private baseUrl: string

  constructor(baseUrl: string = API_BASE_URL) {
    this.baseUrl = baseUrl
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
        console.error(`API request failed:`, {
          url,
          status: response.status,
          statusText: response.statusText,
          body: errorBody,
        })
        throw new Error(`API request failed: ${response.status} ${response.statusText}`)
      }

      return response.json()
    } catch (error) {
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

  async post<T>(endpoint: string, data: unknown): Promise<T> {
    return this.request<T>(endpoint, {
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
}

export const apiClient = new ApiClient()
