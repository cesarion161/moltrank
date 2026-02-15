import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import DashboardPage from './page'

// Mock recharts to avoid SVG rendering issues in jsdom
vi.mock('recharts', () => ({
  BarChart: ({ children }: any) => <div data-testid="bar-chart">{children}</div>,
  Bar: () => null,
  PieChart: ({ children }: any) => <div data-testid="pie-chart">{children}</div>,
  Pie: () => null,
  LineChart: ({ children }: any) => <div data-testid="line-chart">{children}</div>,
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  Legend: () => null,
  ResponsiveContainer: ({ children }: any) => <div>{children}</div>,
  Cell: () => null,
}))

// Mock the API client module
const mockGetCuratorStats = vi.fn()
const mockGetCuratorEvaluations = vi.fn()

vi.mock('@/lib/api-client', () => ({
  apiClient: {
    getCuratorStats: (...args: any[]) => mockGetCuratorStats(...args),
    getCuratorEvaluations: (...args: any[]) => mockGetCuratorEvaluations(...args),
  },
}))

// Mock useIdentity hook
const mockUseIdentity = vi.fn()
vi.mock('@/hooks/use-identity', () => ({
  useIdentity: () => mockUseIdentity(),
}))

// Mock VerifiedBadge
vi.mock('@/components/verified-badge', () => ({
  VerifiedBadge: ({ twitterUsername }: { twitterUsername: string }) => (
    <span data-testid="verified-badge">@{twitterUsername}</span>
  ),
}))

const MOCK_WALLET = '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro'

const defaultIdentity = {
  walletAddress: MOCK_WALLET,
  connected: true,
  twitterUsername: null,
  isVerified: false,
  publicKey: { toBase58: () => MOCK_WALLET },
  user: null,
  isLoading: false,
  error: null,
  hasTwitterLinked: false,
  session: null,
  reload: vi.fn(),
}

const mockStatsResponse = {
  wallet: MOCK_WALLET,
  earned: 5000,
  lost: 1200,
  curatorScore: '0.8250',
  calibrationRate: '0.9000',
  auditPassRate: '0.7500',
  alignmentStability: '0.8500',
  fraudFlags: 0,
}

const mockEvaluations = [
  {
    id: 'eval-1',
    pair: 'Post A vs Post B',
    choice: 'A',
    outcome: 'win' as const,
    amount: 100,
    timestamp: Date.now() - 300000, // 5 minutes ago
  },
  {
    id: 'eval-2',
    pair: 'Post C vs Post D',
    choice: 'B',
    outcome: 'loss' as const,
    amount: -50,
    timestamp: Date.now() - 7200000, // 2 hours ago
  },
]

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    mockUseIdentity.mockReturnValue(defaultIdentity)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows connect wallet prompt when not connected', () => {
    mockUseIdentity.mockReturnValue({
      ...defaultIdentity,
      walletAddress: null,
      connected: false,
      publicKey: null,
    })

    render(<DashboardPage />)

    expect(screen.getByText('Connect your wallet')).toBeInTheDocument()
    expect(screen.getByText(/Connect a Solana wallet/)).toBeInTheDocument()
  })

  it('shows loading state initially', () => {
    mockGetCuratorStats.mockReturnValue(new Promise(() => {})) // never resolves
    mockGetCuratorEvaluations.mockReturnValue(new Promise(() => {}))

    render(<DashboardPage />)

    expect(screen.getByText('Loading dashboard...')).toBeInTheDocument()
  })

  it('renders dashboard with curator stats after loading', async () => {
    mockGetCuratorStats.mockResolvedValue(mockStatsResponse)
    mockGetCuratorEvaluations.mockResolvedValue(mockEvaluations)

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('Curator Dashboard')).toBeInTheDocument()
    })

    // Verify PnL cards
    expect(screen.getByText('Total Earned')).toBeInTheDocument()
    expect(screen.getByText('5,000')).toBeInTheDocument()
    expect(screen.getByText('Total Lost')).toBeInTheDocument()
    expect(screen.getByText('1,200')).toBeInTheDocument()
    expect(screen.getByText('Net PnL')).toBeInTheDocument()
    expect(screen.getByText('+3,800')).toBeInTheDocument()
  })

  it('displays curator score breakdown', async () => {
    mockGetCuratorStats.mockResolvedValue(mockStatsResponse)
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('CuratorScore Breakdown')).toBeInTheDocument()
    })

    expect(screen.getByText('90.0%')).toBeInTheDocument() // calibrationRate
    expect(screen.getByText('75.0%')).toBeInTheDocument() // auditPassRate
    expect(screen.getByText('85.0%')).toBeInTheDocument() // alignmentStability
  })

  it('transforms BigDecimal strings to numbers correctly', async () => {
    mockGetCuratorStats.mockResolvedValue({
      ...mockStatsResponse,
      curatorScore: '0.9100',
    })
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('0.91')).toBeInTheDocument()
    })
  })

  it('shows error state on API failure', async () => {
    mockGetCuratorStats.mockRejectedValue(new Error('API request failed: 500 Internal Server Error'))
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('Error loading dashboard')).toBeInTheDocument()
    })

    expect(
      screen.getByText('API request failed: 500 Internal Server Error')
    ).toBeInTheDocument()
  })

  it('shows error state on network failure', async () => {
    mockGetCuratorStats.mockRejectedValue(new Error('Network error: Failed to fetch'))
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('Error loading dashboard')).toBeInTheDocument()
    })

    expect(
      screen.getByText('Network error: Failed to fetch')
    ).toBeInTheDocument()
  })

  it('renders evaluations table with data', async () => {
    mockGetCuratorStats.mockResolvedValue(mockStatsResponse)
    mockGetCuratorEvaluations.mockResolvedValue(mockEvaluations)

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('Recent Evaluations')).toBeInTheDocument()
    })

    expect(screen.getByText('Post A vs Post B')).toBeInTheDocument()
    expect(screen.getByText('Post C vs Post D')).toBeInTheDocument()
    expect(screen.getByText('Win')).toBeInTheDocument()
    expect(screen.getByText('Loss')).toBeInTheDocument()
    expect(screen.getByText('+100')).toBeInTheDocument()
    expect(screen.getByText('-50')).toBeInTheDocument()
  })

  it('renders charts', async () => {
    mockGetCuratorStats.mockResolvedValue(mockStatsResponse)
    mockGetCuratorEvaluations.mockResolvedValue(mockEvaluations)

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('Earnings Split')).toBeInTheDocument()
    })

    expect(screen.getByText('Golden Set Accuracy')).toBeInTheDocument()
    expect(screen.getByTestId('pie-chart')).toBeInTheDocument()
    expect(screen.getByTestId('line-chart')).toBeInTheDocument()
  })

  it('shows negative net PnL correctly', async () => {
    mockGetCuratorStats.mockResolvedValue({
      ...mockStatsResponse,
      earned: 500,
      lost: 2000,
    })
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('-1,500')).toBeInTheDocument()
    })
  })

  it('shows fraud flag count with correct styling', async () => {
    mockGetCuratorStats.mockResolvedValue({
      ...mockStatsResponse,
      fraudFlags: 3,
    })
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('Fraud Flags')).toBeInTheDocument()
    })

    const flagElement = screen.getByText('3')
    expect(flagElement).toHaveClass('text-red-500')
  })

  it('shows zero fraud flags with green styling', async () => {
    mockGetCuratorStats.mockResolvedValue(mockStatsResponse)
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByText('Fraud Flags')).toBeInTheDocument()
    })

    const flagElements = screen.getAllByText('0')
    const fraudFlagElement = flagElements.find(el =>
      el.classList.contains('text-green-500')
    )
    expect(fraudFlagElement).toBeDefined()
  })

  it('calls API with connected wallet address', async () => {
    mockGetCuratorStats.mockResolvedValue(mockStatsResponse)
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(mockGetCuratorStats).toHaveBeenCalledWith(MOCK_WALLET)
      expect(mockGetCuratorEvaluations).toHaveBeenCalledWith(MOCK_WALLET, 10)
    })
  })

  it('shows verified badge when identity is verified', async () => {
    mockUseIdentity.mockReturnValue({
      ...defaultIdentity,
      twitterUsername: 'testuser',
      isVerified: true,
      hasTwitterLinked: true,
    })
    mockGetCuratorStats.mockResolvedValue(mockStatsResponse)
    mockGetCuratorEvaluations.mockResolvedValue([])

    render(<DashboardPage />)

    await waitFor(() => {
      expect(screen.getByTestId('verified-badge')).toBeInTheDocument()
    })

    expect(screen.getByText('@testuser')).toBeInTheDocument()
  })
})
