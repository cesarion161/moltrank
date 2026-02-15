import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { LinkTwitter } from './link-twitter'

// Mock next-auth
const mockSignIn = vi.fn()
const mockUseSession = vi.fn()
vi.mock('next-auth/react', () => ({
  signIn: (...args: any[]) => mockSignIn(...args),
  useSession: () => mockUseSession(),
}))

// Mock wallet adapter
const mockUseWallet = vi.fn()
vi.mock('@solana/wallet-adapter-react', () => ({
  useWallet: () => mockUseWallet(),
}))

// Mock useIdentity
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

const MOCK_PUBLIC_KEY = {
  toBase58: () => '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
}

describe('LinkTwitter', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    mockUseSession.mockReturnValue({ data: null, status: 'unauthenticated' })
    mockUseWallet.mockReturnValue({ publicKey: null })
    mockUseIdentity.mockReturnValue({
      hasTwitterLinked: false,
      twitterUsername: null,
      isLoading: false,
      reload: vi.fn(),
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders nothing when wallet is not connected', () => {
    const { container } = render(<LinkTwitter />)
    expect(container.innerHTML).toBe('')
  })

  it('renders Link X Account button when wallet is connected', () => {
    mockUseWallet.mockReturnValue({ publicKey: MOCK_PUBLIC_KEY })

    render(<LinkTwitter />)

    expect(screen.getByText('Link X Account')).toBeInTheDocument()
  })

  it('shows loading skeleton when identity is loading', () => {
    mockUseWallet.mockReturnValue({ publicKey: MOCK_PUBLIC_KEY })
    mockUseIdentity.mockReturnValue({
      hasTwitterLinked: false,
      twitterUsername: null,
      isLoading: true,
      reload: vi.fn(),
    })

    render(<LinkTwitter />)

    // Should show skeleton div, not the button
    expect(screen.queryByText('Link X Account')).not.toBeInTheDocument()
  })

  it('shows verified badge when twitter is already linked', () => {
    mockUseWallet.mockReturnValue({ publicKey: MOCK_PUBLIC_KEY })
    mockUseIdentity.mockReturnValue({
      hasTwitterLinked: true,
      twitterUsername: 'testuser',
      isLoading: false,
      reload: vi.fn(),
    })

    render(<LinkTwitter />)

    expect(screen.getByTestId('verified-badge')).toBeInTheDocument()
    expect(screen.getByText('@testuser')).toBeInTheDocument()
    expect(screen.queryByText('Link X Account')).not.toBeInTheDocument()
  })

  it('calls signIn with twitter when Link X Account is clicked', () => {
    mockUseWallet.mockReturnValue({ publicKey: MOCK_PUBLIC_KEY })

    render(<LinkTwitter />)

    fireEvent.click(screen.getByText('Link X Account'))
    expect(mockSignIn).toHaveBeenCalledWith('twitter')
  })

  it('auto-links identity when session has twitter account', async () => {
    const mockReload = vi.fn()
    mockUseWallet.mockReturnValue({ publicKey: MOCK_PUBLIC_KEY })
    mockUseSession.mockReturnValue({
      data: {
        user: {
          account: {
            provider: 'twitter',
            providerAccountId: '123456',
            username: 'testuser',
          },
        },
      },
      status: 'authenticated',
    })
    mockUseIdentity.mockReturnValue({
      hasTwitterLinked: false,
      twitterUsername: null,
      isLoading: false,
      reload: mockReload,
    })

    // Mock the fetch for identity linking
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ success: true }),
    })

    render(<LinkTwitter />)

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith('/api/identity/link', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          walletAddress: '4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro',
          twitterUsername: 'testuser',
          twitterId: '123456',
        }),
      })
    })

    await waitFor(() => {
      expect(mockReload).toHaveBeenCalled()
    })
  })

  it('shows error when identity linking fails', async () => {
    mockUseWallet.mockReturnValue({ publicKey: MOCK_PUBLIC_KEY })
    mockUseSession.mockReturnValue({
      data: {
        user: {
          account: {
            provider: 'twitter',
            providerAccountId: '123456',
            username: 'testuser',
          },
        },
      },
      status: 'authenticated',
    })
    mockUseIdentity.mockReturnValue({
      hasTwitterLinked: false,
      twitterUsername: null,
      isLoading: false,
      reload: vi.fn(),
    })

    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ error: 'Already linked' }),
    })

    render(<LinkTwitter />)

    await waitFor(() => {
      expect(screen.getByText('Already linked')).toBeInTheDocument()
    })
  })
})
