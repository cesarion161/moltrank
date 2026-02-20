import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import CuratePage from './page'

// Mock the API client module
const mockGetNextPair = vi.fn()
const mockGetActiveRound = vi.fn()
const mockCommitVote = vi.fn()
const mockSkipPair = vi.fn()

vi.mock('@/lib/api-client', () => ({
  apiClient: {
    getNextPair: (...args: any[]) => mockGetNextPair(...args),
    getActiveRound: (...args: any[]) => mockGetActiveRound(...args),
    commitVote: (...args: any[]) => mockCommitVote(...args),
    skipPair: (...args: any[]) => mockSkipPair(...args),
  },
}))

// Mock useIdentity hook
const mockUseIdentity = vi.fn()
vi.mock('@/hooks/use-identity', () => ({
  useIdentity: () => mockUseIdentity(),
}))

Object.defineProperty(globalThis, 'crypto', {
  value: {
    getRandomValues: (arr: Uint8Array) => {
      for (let i = 0; i < arr.length; i++) arr[i] = i
      return arr
    },
  },
})

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

const mockPair = {
  id: 1,
  pairId: 42,
  roundId: 1,
  postA: {
    id: 10,
    moltbookId: 'mb-1',
    agent: 'AgentAlpha',
    content: 'This is a great post about AI alignment.\nSecond line here.\nThird line for expansion.',
    elo: 1200,
    matchups: 50,
    wins: 30,
    createdAt: '2026-02-14T00:00:00Z',
    updatedAt: '2026-02-14T00:00:00Z',
  },
  postB: {
    id: 11,
    moltbookId: 'mb-2',
    agent: 'AgentBeta',
    content: 'A different take on interpretability research.',
    elo: 1150,
    matchups: 40,
    wins: 22,
    createdAt: '2026-02-14T00:00:00Z',
    updatedAt: '2026-02-14T00:00:00Z',
  },
  commitDeadline: new Date(Date.now() + 3600000).toISOString(),
  revealDeadline: new Date(Date.now() + 7200000).toISOString(),
  isGolden: false,
  votesCount: 3,
  createdAt: '2026-02-14T00:00:00Z',
}

const mockRound = {
  id: 1,
  roundId: 1,
  status: 'COMMIT',
  commitDeadline: new Date(Date.now() + 3600000).toISOString(),
  revealDeadline: new Date(Date.now() + 7200000).toISOString(),
  totalPairs: 20,
  remainingPairs: 15,
}

describe('CuratePage', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    mockUseIdentity.mockReturnValue(defaultIdentity)
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('shows connect wallet prompt when not connected', async () => {
    mockUseIdentity.mockReturnValue({
      ...defaultIdentity,
      walletAddress: null,
      connected: false,
    })

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Connect your wallet')).toBeInTheDocument()
    })
    expect(screen.getByText(/Connect a Solana wallet to start curating/)).toBeInTheDocument()
  })

  it('shows loading state initially', () => {
    mockGetNextPair.mockReturnValue(new Promise(() => {}))
    mockGetActiveRound.mockReturnValue(new Promise(() => {}))

    render(<CuratePage />)

    expect(screen.getByText('Loading pair...')).toBeInTheDocument()
  })

  it('renders pair comparison after loading', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Curate')).toBeInTheDocument()
    })

    // Post A details
    expect(screen.getByText('AgentAlpha')).toBeInTheDocument()
    // Post B details
    expect(screen.getByText('AgentBeta')).toBeInTheDocument()
    expect(mockGetActiveRound).toHaveBeenCalledTimes(1)
  })

  it('renders stake preset buttons', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Low (10 tokens)')).toBeInTheDocument()
    })

    expect(screen.getByText('Medium (50 tokens)')).toBeInTheDocument()
    expect(screen.getByText('High (200 tokens)')).toBeInTheDocument()
    expect(screen.getByText('Custom')).toBeInTheDocument()
  })

  it('renders vote buttons with stake amounts', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Vote A (50 tokens)')).toBeInTheDocument()
    })
    expect(screen.getByText('Vote B (50 tokens)')).toBeInTheDocument()
  })

  it('renders skip button', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Skip')).toBeInTheDocument()
    })
  })

  it('renders round status bar', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Round 1')).toBeInTheDocument()
    })

    expect(screen.getByText('COMMIT')).toBeInTheDocument()
    expect(screen.getByText('15 pairs left')).toBeInTheDocument()
  })

  it('shows no pairs available when API returns null', async () => {
    mockGetNextPair.mockResolvedValue(null)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('No pairs available')).toBeInTheDocument()
    })

    expect(screen.getByText('Retry')).toBeInTheDocument()
  })

  it('loads a pair after round transitions to commit phase', async () => {
    const openRound = { ...mockRound, status: 'OPEN', remainingPairs: 20 }
    const commitRound = { ...mockRound, status: 'COMMIT', remainingPairs: 19 }

    mockGetNextPair.mockResolvedValueOnce(null).mockResolvedValueOnce(mockPair)
    mockGetActiveRound
      .mockResolvedValueOnce(openRound)
      .mockResolvedValueOnce(commitRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('No pairs available')).toBeInTheDocument()
    })
    expect(screen.getByText('OPEN')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Retry'))

    await waitFor(() => {
      expect(screen.getByText('AgentAlpha')).toBeInTheDocument()
    })
    expect(screen.getByText('COMMIT')).toBeInTheDocument()
    expect(mockGetNextPair).toHaveBeenCalledTimes(2)
  })

  it('calls commitVote API when voting', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)
    mockCommitVote.mockResolvedValue({ success: true })

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Vote A (50 tokens)')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Vote A (50 tokens)'))

    await waitFor(() => {
      expect(mockCommitVote).toHaveBeenCalledWith(1, expect.objectContaining({
        wallet: MOCK_WALLET,
        commitmentHash: expect.stringMatching(/^0x[0-9a-f]{64}$/),
        encryptedReveal: expect.any(String),
        stakeAmount: 50,
      }))
    })
  })

  it('shows confirmation after successful vote', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)
    mockCommitVote.mockResolvedValue({ success: true })

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Vote A (50 tokens)')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Vote A (50 tokens)'))

    await waitFor(() => {
      expect(screen.getByText('Vote committed!')).toBeInTheDocument()
    })
  })

  it('shows error on vote failure', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)
    mockCommitVote.mockRejectedValue(new Error('Insufficient stake balance'))

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Vote A (50 tokens)')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Vote A (50 tokens)'))

    await waitFor(() => {
      expect(screen.getByText('Insufficient stake balance')).toBeInTheDocument()
    })
  })

  it('calls skipPair API when skipping and loads next pair', async () => {
    mockGetNextPair.mockResolvedValueOnce(mockPair).mockResolvedValueOnce(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)
    mockSkipPair.mockResolvedValue({ success: true })

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Skip')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Skip'))

    await waitFor(() => {
      expect(mockSkipPair).toHaveBeenCalledWith(1, MOCK_WALLET)
    })

    await waitFor(() => {
      expect(mockGetNextPair).toHaveBeenCalledTimes(2)
    })
  })

  it('changes stake when selecting different preset', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('Vote A (50 tokens)')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('High (200 tokens)'))

    expect(screen.getByText('Vote A (200 tokens)')).toBeInTheDocument()
    expect(screen.getByText('Vote B (200 tokens)')).toBeInTheDocument()
  })

  it('shows agent stats in post panels', async () => {
    mockGetNextPair.mockResolvedValue(mockPair)
    mockGetActiveRound.mockResolvedValue(mockRound)

    render(<CuratePage />)

    await waitFor(() => {
      expect(screen.getByText('AgentAlpha')).toBeInTheDocument()
    })

    // Check ELO stats are shown
    const eloLabels = screen.getAllByText('ELO:')
    expect(eloLabels).toHaveLength(2)
    expect(screen.getByText('1200')).toBeInTheDocument()
    expect(screen.getByText('1150')).toBeInTheDocument()
  })
})
