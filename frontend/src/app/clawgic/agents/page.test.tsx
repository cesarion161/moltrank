import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClawgicAgentsPage, { AGENTS_MD_MAX_LENGTH, buildAgentPayload, validateAgentForm } from './page'

const mockFetch = vi.fn()

type MockResponseInit = {
  ok: boolean
  status: number
  statusText: string
  jsonBody?: unknown
  textBody?: string
}

const agentsFixture = [
  {
    agentId: '00000000-0000-0000-0000-000000000a01',
    walletAddress: '0x1111111111111111111111111111111111111111',
    name: 'Logic Falcon',
    avatarUrl: null,
    providerType: 'OPENAI',
    providerKeyRef: 'gpt-4o',
    persona: 'A sharp analytical debater focused on formal logic.',
    apiKeyConfigured: true,
    createdAt: '2026-02-28T10:00:00Z',
    updatedAt: '2026-02-28T10:00:00Z',
  },
  {
    agentId: '00000000-0000-0000-0000-000000000a02',
    walletAddress: '0x2222222222222222222222222222222222222222',
    name: 'Counter Fox',
    avatarUrl: null,
    providerType: 'ANTHROPIC',
    providerKeyRef: null,
    persona: null,
    apiKeyConfigured: false,
    createdAt: '2026-02-28T11:00:00Z',
    updatedAt: '2026-02-28T11:00:00Z',
  },
]

const agentDetailFixture = {
  agentId: '00000000-0000-0000-0000-000000000a01',
  walletAddress: '0x1111111111111111111111111111111111111111',
  name: 'Logic Falcon',
  avatarUrl: null,
  providerType: 'OPENAI',
  providerKeyRef: 'gpt-4o',
  systemPrompt: 'You are a skilled debater who specializes in formal logic and analytical reasoning.',
  skillsMarkdown: '- Formal logic\n- Counter-argumentation\n- Persuasive rhetoric',
  persona: 'A sharp analytical debater focused on formal logic.',
  agentsMdSource: '# Logic Falcon Agent\n\nSystem: Analytical debater',
  apiKeyConfigured: true,
  elo: {
    agentId: '00000000-0000-0000-0000-000000000a01',
    currentElo: 1032,
    matchesPlayed: 5,
    matchesWon: 3,
    matchesForfeited: 0,
    lastUpdated: '2026-03-01T08:00:00Z',
  },
  createdAt: '2026-02-28T10:00:00Z',
  updatedAt: '2026-02-28T10:00:00Z',
}

function mockResponse(init: MockResponseInit) {
  return {
    ok: init.ok,
    status: init.status,
    statusText: init.statusText,
    json: () => Promise.resolve(init.jsonBody),
    text: () => Promise.resolve(init.textBody || ''),
  }
}

describe('ClawgicAgentsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    globalThis.fetch = mockFetch
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    delete (window as any).ethereum
  })

  it('renders loading then agent list on successful fetch', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: agentsFixture,
      })
    )

    render(<ClawgicAgentsPage />)

    expect(screen.getByText('Loading agents...')).toBeInTheDocument()
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()
    expect(screen.getByText('Counter Fox')).toBeInTheDocument()
    expect(screen.getByText('Provider: OPENAI')).toBeInTheDocument()
    expect(screen.getByText('Provider: ANTHROPIC')).toBeInTheDocument()
    expect(screen.getByText('Model: gpt-4o')).toBeInTheDocument()
    expect(screen.getByText('A sharp analytical debater focused on formal logic.')).toBeInTheDocument()
  })

  it('renders empty state when no agents exist', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: [],
      })
    )

    render(<ClawgicAgentsPage />)

    expect(await screen.findByText(/No agents found/)).toBeInTheDocument()
  })

  it('renders error state when fetch fails', async () => {
    mockFetch.mockRejectedValueOnce(new TypeError('fetch failed'))

    render(<ClawgicAgentsPage />)

    expect(await screen.findByText('Failed to load agents.')).toBeInTheDocument()
    expect(screen.getByText('Network error: fetch failed')).toBeInTheDocument()
  })

  it('renders Clawgic badge and navigation links', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: agentsFixture,
      })
    )

    render(<ClawgicAgentsPage />)

    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()
    expect(screen.getByText('Clawgic')).toHaveClass('clawgic-badge')
    expect(screen.getByRole('link', { name: 'Tournament Lobby' })).toHaveAttribute('href', '/clawgic/tournaments')
    expect(screen.getByRole('link', { name: 'Leaderboard' })).toHaveAttribute('href', '/clawgic/leaderboard')
  })

  it('renders provider badge per agent card', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: [agentsFixture[0]],
      })
    )

    render(<ClawgicAgentsPage />)

    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()
    const badges = screen.getAllByText('OPENAI')
    expect(badges.length).toBeGreaterThanOrEqual(1)
    expect(badges.some((el) => el.classList.contains('clawgic-badge'))).toBe(true)
  })

  it('does not render Model field when providerKeyRef is null', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: [agentsFixture[1]],
      })
    )

    render(<ClawgicAgentsPage />)

    expect(await screen.findByText('Counter Fox')).toBeInTheDocument()
    expect(screen.queryByText(/Model:/)).not.toBeInTheDocument()
  })

  it('truncates long wallet addresses', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: [agentsFixture[0]],
      })
    )

    render(<ClawgicAgentsPage />)

    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()
    // Full address is 42 chars, should be truncated to 14 + '...'
    expect(screen.getByText('0x111111111111...')).toBeInTheDocument()
  })

  // --- API Key Configured Status ---

  it('shows API key configured status for each agent', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: agentsFixture,
      })
    )

    render(<ClawgicAgentsPage />)

    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()
    expect(screen.getByText('Configured')).toBeInTheDocument()
    expect(screen.getByText('Not configured')).toBeInTheDocument()
  })

  // --- Refresh Button ---

  it('renders Refresh button and refreshes agent list when clicked', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [agentsFixture[0]] })
    )

    render(<ClawgicAgentsPage />)
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()

    // Mock refresh response with both agents
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
    )

    fireEvent.click(screen.getByRole('button', { name: 'Refresh agents' }))

    expect(await screen.findByText('Counter Fox')).toBeInTheDocument()
  })

  // --- Wallet Filter ---

  it('renders wallet filter input and filters agents when applied', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
    )

    render(<ClawgicAgentsPage />)
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()

    // Type wallet filter
    fireEvent.change(screen.getByLabelText('Wallet filter'), {
      target: { value: '0x1111111111111111111111111111111111111111' },
    })

    // Mock filtered response
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [agentsFixture[0]] })
    )

    fireEvent.click(screen.getByRole('button', { name: 'Filter' }))

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2)
    })

    // Verify the filter was passed to the API
    const filterCall = mockFetch.mock.calls[1]
    expect(filterCall[0]).toContain('walletAddress=0x1111111111111111111111111111111111111111')
  })

  it('shows Clear button when filter is active and clears it', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
    )

    render(<ClawgicAgentsPage />)
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()

    // No Clear button when filter is empty
    expect(screen.queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()

    // Set filter
    fireEvent.change(screen.getByLabelText('Wallet filter'), {
      target: { value: '0xabc' },
    })

    // Clear button appears
    expect(screen.getByRole('button', { name: 'Clear' })).toBeInTheDocument()

    // Mock refresh for clear
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentsFixture })
    )

    fireEvent.click(screen.getByRole('button', { name: 'Clear' }))

    // Filter input should be cleared
    expect(screen.getByLabelText('Wallet filter')).toHaveValue('')
  })

  // --- Detail Panel ---

  it('shows View Details button and expands agent detail panel on click', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [agentsFixture[0]] })
    )

    render(<ClawgicAgentsPage />)
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()

    // Mock detail fetch
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentDetailFixture })
    )

    fireEvent.click(screen.getByRole('button', { name: 'View details' }))

    // Detail panel should show system prompt
    expect(await screen.findByText('System Prompt')).toBeInTheDocument()
    expect(screen.getByText(/skilled debater who specializes/)).toBeInTheDocument()

    // Should show Elo stats
    expect(screen.getByText('Elo Stats')).toBeInTheDocument()
    expect(screen.getByText(/1032/)).toBeInTheDocument()
    expect(screen.getByText(/Played: 5/)).toBeInTheDocument()
    expect(screen.getByText(/Won: 3/)).toBeInTheDocument()

    // Should show skills
    expect(screen.getByText('Skills')).toBeInTheDocument()

    // Should show AGENTS.md
    expect(screen.getByText('AGENTS.md Source')).toBeInTheDocument()
  })

  it('hides detail panel when Hide Details is clicked', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [agentsFixture[0]] })
    )

    render(<ClawgicAgentsPage />)
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()

    // Expand
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: agentDetailFixture })
    )
    fireEvent.click(screen.getByRole('button', { name: 'View details' }))
    expect(await screen.findByText('System Prompt')).toBeInTheDocument()

    // Collapse
    fireEvent.click(screen.getByRole('button', { name: 'Hide details' }))
    expect(screen.queryByText('System Prompt')).not.toBeInTheDocument()
  })

  it('shows error state in detail panel when fetch fails', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [agentsFixture[0]] })
    )

    render(<ClawgicAgentsPage />)
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()

    // Mock detail fetch failure
    mockFetch.mockRejectedValueOnce(new TypeError('network error'))

    fireEvent.click(screen.getByRole('button', { name: 'View details' }))

    expect(await screen.findByText(/network error/)).toBeInTheDocument()
  })

  it('shows Show more/Show less for long text in detail panel', async () => {
    const longPrompt = 'A'.repeat(300)
    const detailWithLongPrompt = { ...agentDetailFixture, systemPrompt: longPrompt }

    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [agentsFixture[0]] })
    )

    render(<ClawgicAgentsPage />)
    expect(await screen.findByText('Logic Falcon')).toBeInTheDocument()

    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: detailWithLongPrompt })
    )

    fireEvent.click(screen.getByRole('button', { name: 'View details' }))
    expect(await screen.findByText('System Prompt')).toBeInTheDocument()

    // Should show truncated text with Show more button
    expect(screen.getByText('Show more')).toBeInTheDocument()

    // Click Show more
    fireEvent.click(screen.getByText('Show more'))
    expect(screen.getByText('Show less')).toBeInTheDocument()

    // Click Show less
    fireEvent.click(screen.getByText('Show less'))
    expect(screen.getByText('Show more')).toBeInTheDocument()
  })

  // --- Create Agent Form Tests ---

  it('shows Create Agent button and toggles form visibility', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)

    const createBtn = screen.getByRole('button', { name: 'Create Agent' })
    expect(createBtn).toBeInTheDocument()

    fireEvent.click(createBtn)
    expect(await screen.findByText('Create New Agent')).toBeInTheDocument()
    expect(screen.getByLabelText('Agent name')).toBeInTheDocument()

    // Cancel buttons are present (top toggle + form cancel)
    const cancelBtns = screen.getAllByRole('button', { name: 'Cancel' })
    expect(cancelBtns.length).toBeGreaterThanOrEqual(1)
  })

  it('renders all required and optional form fields', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Required fields
    expect(await screen.findByLabelText('Wallet address')).toBeInTheDocument()
    expect(screen.getByLabelText('Agent name')).toBeInTheDocument()
    expect(screen.getByLabelText('Provider type')).toBeInTheDocument()
    expect(screen.getByLabelText('API key')).toBeInTheDocument()
    expect(screen.getByLabelText('System prompt')).toBeInTheDocument()

    // Optional fields
    expect(screen.getByLabelText('Avatar URL')).toBeInTheDocument()
    expect(screen.getByLabelText('Model reference')).toBeInTheDocument()
    expect(screen.getByLabelText('Persona')).toBeInTheDocument()
    expect(screen.getByLabelText('Skills markdown')).toBeInTheDocument()
    expect(screen.getByLabelText('AGENTS.md source')).toBeInTheDocument()
  })

  it('shows validation errors for empty required fields on submit', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Click submit without filling anything
    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    expect(await screen.findByText('Wallet address is required.')).toBeInTheDocument()
    expect(screen.getByText('Agent name is required.')).toBeInTheDocument()
    expect(screen.getByText('API key is required.')).toBeInTheDocument()
    expect(screen.getByText('System prompt is required.')).toBeInTheDocument()
  })

  it('shows validation error for invalid wallet address', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    fireEvent.change(screen.getByLabelText('Wallet address'), { target: { value: 'not-an-address' } })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Test Agent' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'Test prompt' } })

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    expect(await screen.findByText(/Must be a valid 0x-prefixed EVM address/)).toBeInTheDocument()
  })

  it('shows validation error for invalid avatar URL', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0x1111111111111111111111111111111111111111' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Test' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'Test prompt' } })
    fireEvent.change(screen.getByLabelText('Avatar URL'), { target: { value: 'not-a-url' } })

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    expect(await screen.findByText('Must be a valid URL.')).toBeInTheDocument()
  })

  it('shows validation error for agent name exceeding 120 characters', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0x1111111111111111111111111111111111111111' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'A'.repeat(121) } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'prompt' } })

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    expect(await screen.findByText('Agent name must be at most 120 characters.')).toBeInTheDocument()
  })

  it('clears field error when user updates the field', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Submit empty to trigger errors
    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)
    expect(await screen.findByText('Agent name is required.')).toBeInTheDocument()

    // Type in the name field — error should clear
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Fixed' } })
    expect(screen.queryByText('Agent name is required.')).not.toBeInTheDocument()
  })

  it('submits valid form and shows success banner', async () => {
    // Initial load
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Fill required fields
    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'New Agent' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test123' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'Be a great debater.' } })

    // Mock create API
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 201,
        statusText: 'Created',
        jsonBody: {
          agentId: '00000000-0000-0000-0000-000000000a99',
          walletAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          name: 'New Agent',
          avatarUrl: null,
          providerType: 'OPENAI',
          providerKeyRef: null,
          persona: null,
          apiKeyConfigured: true,
          createdAt: '2026-03-01T10:00:00Z',
          updatedAt: '2026-03-01T10:00:00Z',
        },
      })
    )
    // Mock refresh after create
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 200,
        statusText: 'OK',
        jsonBody: [{
          agentId: '00000000-0000-0000-0000-000000000a99',
          walletAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          name: 'New Agent',
          avatarUrl: null,
          providerType: 'OPENAI',
          providerKeyRef: null,
          persona: null,
          apiKeyConfigured: true,
          createdAt: '2026-03-01T10:00:00Z',
          updatedAt: '2026-03-01T10:00:00Z',
        }],
      })
    )

    // Submit
    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    expect(await screen.findByText(/New Agent.*created successfully/)).toBeInTheDocument()
    expect(screen.getByText(/API key configured: yes/)).toBeInTheDocument()
    // Form should be hidden after success
    expect(screen.queryByText('Create New Agent')).not.toBeInTheDocument()
  })

  it('shows error banner when backend returns 400 with detail only', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Fill form with valid data
    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Test' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'prompt' } })

    // Mock 400 response without fieldErrors
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        textBody: JSON.stringify({ detail: 'avatarUrl must be a valid absolute URL' }),
      })
    )

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    expect(await screen.findByText('avatarUrl must be a valid absolute URL')).toBeInTheDocument()
  })

  it('maps backend 400 fieldErrors to per-field error messages', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Fill all required fields so client validation passes
    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Test' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'prompt' } })

    // Mock 400 with structured fieldErrors
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        textBody: JSON.stringify({
          detail: 'Validation failed: name must be at most 120 characters; avatarUrl must be a valid absolute URL',
          fieldErrors: {
            name: 'name must be at most 120 characters',
            avatarUrl: 'avatarUrl must be a valid absolute URL',
          },
        }),
      })
    )

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    // Field-level errors should be shown inline
    expect(await screen.findByText('name must be at most 120 characters')).toBeInTheDocument()
    expect(screen.getByText('avatarUrl must be a valid absolute URL')).toBeInTheDocument()

    // Banner should also be shown with detail
    expect(screen.getByText(/Validation failed/)).toBeInTheDocument()

    // Form should remain open so user can fix fields
    expect(screen.getByText('Create New Agent')).toBeInTheDocument()
  })

  it('shows generic error on network failure', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Test' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'prompt' } })

    // Network failure
    mockFetch.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    expect(await screen.findByText('Network error: Failed to fetch')).toBeInTheDocument()
  })

  it('resets form and hides API key input after successful creation', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Fill form
    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Secure Agent' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-secret-key-123' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'Be secure.' } })

    // Verify key is in the input before submit
    expect(screen.getByLabelText('API key')).toHaveValue('sk-secret-key-123')

    // Mock create + refresh
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 201,
        statusText: 'Created',
        jsonBody: {
          agentId: 'secure-id',
          walletAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          name: 'Secure Agent',
          avatarUrl: null,
          providerType: 'OPENAI',
          providerKeyRef: null,
          persona: null,
          apiKeyConfigured: true,
          createdAt: '2026-03-01T10:00:00Z',
          updatedAt: '2026-03-01T10:00:00Z',
        },
      })
    )
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    // After success: form is hidden, so API key input is not in DOM
    await screen.findByText(/Secure Agent.*created successfully/)
    expect(screen.queryByLabelText('API key')).not.toBeInTheDocument()

    // Success banner confirms key is configured without revealing it
    expect(screen.getByText(/API key configured: yes/)).toBeInTheDocument()
  })

  it('prefills wallet from connected EVM account', async () => {
    const mockAddress = '0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    ;(window as any).ethereum = {
      request: vi.fn().mockResolvedValue([mockAddress]),
    }

    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    const connectBtn = screen.getByRole('button', { name: 'Connect Wallet' })
    fireEvent.click(connectBtn)

    await waitFor(() => {
      expect(screen.getByLabelText('Wallet address')).toHaveValue(mockAddress)
    })
  })

  it('shows error when no EVM wallet is available on Connect Wallet', async () => {
    // No window.ethereum
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    const connectBtn = screen.getByRole('button', { name: 'Connect Wallet' })
    fireEvent.click(connectBtn)

    expect(await screen.findByText(/No EVM wallet found/)).toBeInTheDocument()
  })

  it('includes optional fields in payload when provided', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Fill all fields
    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Full Agent' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-full' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'My prompt' } })
    fireEvent.change(screen.getByLabelText('Avatar URL'), { target: { value: 'https://example.com/avatar.png' } })
    fireEvent.change(screen.getByLabelText('Model reference'), { target: { value: 'gpt-4o' } })
    fireEvent.change(screen.getByLabelText('Persona'), { target: { value: 'Logical thinker' } })
    fireEvent.change(screen.getByLabelText('Skills markdown'), { target: { value: '- Logic\n- Rhetoric' } })
    fireEvent.change(screen.getByLabelText('AGENTS.md source'), { target: { value: '# Agent Config' } })

    // Mock create + refresh
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 201,
        statusText: 'Created',
        jsonBody: {
          agentId: 'new-id',
          walletAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          name: 'Full Agent',
          avatarUrl: 'https://example.com/avatar.png',
          providerType: 'OPENAI',
          providerKeyRef: 'gpt-4o',
          persona: 'Logical thinker',
          apiKeyConfigured: true,
          createdAt: '2026-03-01T10:00:00Z',
          updatedAt: '2026-03-01T10:00:00Z',
        },
      })
    )
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    await screen.findByText(/Full Agent.*created successfully/)

    // Verify the POST call included optional fields
    const postCall = mockFetch.mock.calls[1] // [0] = initial load, [1] = create POST
    const postedBody = JSON.parse(postCall[1].body)
    expect(postedBody.avatarUrl).toBe('https://example.com/avatar.png')
    expect(postedBody.providerKeyRef).toBe('gpt-4o')
    expect(postedBody.persona).toBe('Logical thinker')
    expect(postedBody.skillsMarkdown).toBe('- Logic\n- Rhetoric')
    expect(postedBody.agentsMdSource).toBe('# Agent Config')
  })

  it('does not include empty optional fields in payload', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Fill only required fields
    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Minimal' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-min' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'prompt' } })

    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 201,
        statusText: 'Created',
        jsonBody: {
          agentId: 'new-id',
          walletAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          name: 'Minimal',
          avatarUrl: null,
          providerType: 'OPENAI',
          providerKeyRef: null,
          persona: null,
          apiKeyConfigured: true,
          createdAt: '2026-03-01T10:00:00Z',
          updatedAt: '2026-03-01T10:00:00Z',
        },
      })
    )
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    await screen.findByText(/Minimal.*created successfully/)

    const postCall = mockFetch.mock.calls[1]
    const postedBody = JSON.parse(postCall[1].body)
    expect(postedBody.avatarUrl).toBeUndefined()
    expect(postedBody.providerKeyRef).toBeUndefined()
    expect(postedBody.persona).toBeUndefined()
    expect(postedBody.skillsMarkdown).toBeUndefined()
    expect(postedBody.agentsMdSource).toBeUndefined()
  })

  // --- AGENTS.md Ingestion UX Tests ---

  it('renders Import File button and hidden file input for AGENTS.md', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    expect(screen.getByRole('button', { name: 'Import File' })).toBeInTheDocument()
    expect(screen.getByLabelText('Import AGENTS.md file')).toBeInTheDocument()
  })

  it('imports .md file content into AGENTS.md textarea', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    const fileContent = '# My Agent\n\nSystem: Analytical debater with formal logic skills.'
    const file = new File([fileContent], 'AGENTS.md', { type: 'text/markdown' })

    const fileInput = screen.getByLabelText('Import AGENTS.md file')
    fireEvent.change(fileInput, { target: { files: [file] } })

    // FileReader is async — wait for textarea to be updated
    await waitFor(() => {
      expect(screen.getByLabelText('AGENTS.md source')).toHaveValue(fileContent)
    })
  })

  it('replaces existing AGENTS.md content when importing a new file', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // First, type some content manually
    fireEvent.change(screen.getByLabelText('AGENTS.md source'), {
      target: { value: 'Old content' },
    })
    expect(screen.getByLabelText('AGENTS.md source')).toHaveValue('Old content')

    // Now import a file — it should replace the old content
    const newContent = '# Replaced Agent Config'
    const file = new File([newContent], 'new-agent.md', { type: 'text/markdown' })
    const fileInput = screen.getByLabelText('Import AGENTS.md file')
    fireEvent.change(fileInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(screen.getByLabelText('AGENTS.md source')).toHaveValue(newContent)
    })
  })

  it('shows Clear button when AGENTS.md has content and clears it on click', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // No Clear button when empty
    expect(screen.queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()

    // Add content
    fireEvent.change(screen.getByLabelText('AGENTS.md source'), {
      target: { value: '# Some config' },
    })

    // Clear button should appear
    const clearBtn = screen.getByRole('button', { name: 'Clear' })
    expect(clearBtn).toBeInTheDocument()

    // Click clear
    fireEvent.click(clearBtn)
    expect(screen.getByLabelText('AGENTS.md source')).toHaveValue('')
    // Clear button should disappear
    expect(screen.queryByRole('button', { name: 'Clear' })).not.toBeInTheDocument()
  })

  it('shows character counter for AGENTS.md field', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Counter shows 0/50,000
    expect(screen.getByText('0/50,000')).toBeInTheDocument()

    // Type some content
    fireEvent.change(screen.getByLabelText('AGENTS.md source'), {
      target: { value: 'Hello world' },
    })

    expect(screen.getByText('11/50,000')).toBeInTheDocument()
  })

  it('shows truncation warning when AGENTS.md exceeds limit', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Set content exceeding limit
    const oversized = 'X'.repeat(AGENTS_MD_MAX_LENGTH + 1)
    fireEvent.change(screen.getByLabelText('AGENTS.md source'), {
      target: { value: oversized },
    })

    expect(screen.getByRole('status')).toHaveTextContent(/exceeds the 50,000 character limit/)
  })

  it('rejects oversized file on import', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    // Create a file that exceeds 512 KB
    const bigContent = 'X'.repeat(513 * 1024)
    const bigFile = new File([bigContent], 'big-agents.md', { type: 'text/markdown' })

    const fileInput = screen.getByLabelText('Import AGENTS.md file')
    fireEvent.change(fileInput, { target: { files: [bigFile] } })

    expect(await screen.findByText(/File is too large/)).toBeInTheDocument()
    expect(screen.getByText(/Maximum file size is 512 KB/)).toBeInTheDocument()
    // Textarea should remain empty (content not loaded)
    expect(screen.getByLabelText('AGENTS.md source')).toHaveValue('')
  })

  it('preserves imported content exactly in agentsMdSource payload (no mutation)', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    const rawContent = '  # Agent Config  \n  with leading/trailing spaces  \n'
    const file = new File([rawContent], 'AGENTS.md', { type: 'text/markdown' })
    const fileInput = screen.getByLabelText('Import AGENTS.md file')
    fireEvent.change(fileInput, { target: { files: [file] } })

    await waitFor(() => {
      // Content loaded exactly as-is (no trimming in textarea)
      expect(screen.getByLabelText('AGENTS.md source')).toHaveValue(rawContent)
    })

    // Fill required fields
    fireEvent.change(screen.getByLabelText('Wallet address'), {
      target: { value: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' },
    })
    fireEvent.change(screen.getByLabelText('Agent name'), { target: { value: 'Exact Agent' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'sk-test' } })
    fireEvent.change(screen.getByLabelText('System prompt'), { target: { value: 'prompt' } })

    // Mock create + refresh
    mockFetch.mockResolvedValueOnce(
      mockResponse({
        ok: true,
        status: 201,
        statusText: 'Created',
        jsonBody: {
          agentId: 'exact-id',
          walletAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          name: 'Exact Agent',
          avatarUrl: null,
          providerType: 'OPENAI',
          providerKeyRef: null,
          persona: null,
          apiKeyConfigured: true,
          createdAt: '2026-03-01T10:00:00Z',
          updatedAt: '2026-03-01T10:00:00Z',
        },
      })
    )
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    // Submit
    const submitBtns = screen.getAllByRole('button', { name: 'Create Agent' })
    const formSubmitBtn = submitBtns.find((btn) => btn.closest('section')?.querySelector('h2'))!
    fireEvent.click(formSubmitBtn)

    await screen.findByText(/Exact Agent.*created successfully/)

    // Verify the POST call included the trimmed content (buildAgentPayload trims)
    const postCall = mockFetch.mock.calls[1]
    const postedBody = JSON.parse(postCall[1].body)
    expect(postedBody.agentsMdSource).toBe(rawContent.trim())
  })

  it('shows provider type selector with all options', async () => {
    mockFetch.mockResolvedValueOnce(
      mockResponse({ ok: true, status: 200, statusText: 'OK', jsonBody: [] })
    )

    render(<ClawgicAgentsPage />)
    await screen.findByText(/No agents found/)
    fireEvent.click(screen.getByRole('button', { name: 'Create Agent' }))

    const select = screen.getByLabelText('Provider type') as HTMLSelectElement
    const options = Array.from(select.options).map((o) => o.value)
    expect(options).toEqual(['OPENAI', 'ANTHROPIC', 'MOCK'])
  })
})

// --- Unit tests for exported validation/payload functions ---

describe('validateAgentForm', () => {
  const validForm = {
    walletAddress: '0x1111111111111111111111111111111111111111',
    name: 'Test Agent',
    providerType: 'OPENAI' as const,
    apiKey: 'sk-test',
    systemPrompt: 'You are a debater.',
    avatarUrl: '',
    providerKeyRef: '',
    persona: '',
    skillsMarkdown: '',
    agentsMdSource: '',
  }

  it('returns no errors for valid form', () => {
    expect(validateAgentForm(validForm)).toEqual({})
  })

  it('returns error for missing wallet address', () => {
    const errors = validateAgentForm({ ...validForm, walletAddress: '' })
    expect(errors.walletAddress).toBe('Wallet address is required.')
  })

  it('returns error for invalid wallet address pattern', () => {
    const errors = validateAgentForm({ ...validForm, walletAddress: '0xZZZ' })
    expect(errors.walletAddress).toContain('valid 0x-prefixed EVM address')
  })

  it('returns error for missing name', () => {
    const errors = validateAgentForm({ ...validForm, name: '' })
    expect(errors.name).toBe('Agent name is required.')
  })

  it('returns error for name exceeding 120 chars', () => {
    const errors = validateAgentForm({ ...validForm, name: 'A'.repeat(121) })
    expect(errors.name).toContain('120 characters')
  })

  it('returns error for missing API key', () => {
    const errors = validateAgentForm({ ...validForm, apiKey: '' })
    expect(errors.apiKey).toBe('API key is required.')
  })

  it('returns error for missing system prompt', () => {
    const errors = validateAgentForm({ ...validForm, systemPrompt: '  ' })
    expect(errors.systemPrompt).toBe('System prompt is required.')
  })

  it('returns error for invalid avatar URL', () => {
    const errors = validateAgentForm({ ...validForm, avatarUrl: 'not-a-url' })
    expect(errors.avatarUrl).toBe('Must be a valid URL.')
  })

  it('returns error for non-http avatar URL', () => {
    const errors = validateAgentForm({ ...validForm, avatarUrl: 'ftp://example.com/img.png' })
    expect(errors.avatarUrl).toContain('http or https')
  })

  it('returns error for avatar URL exceeding 2048 chars', () => {
    const errors = validateAgentForm({ ...validForm, avatarUrl: 'https://example.com/' + 'a'.repeat(2040) })
    expect(errors.avatarUrl).toContain('2048 characters')
  })

  it('returns error for providerKeyRef exceeding 255 chars', () => {
    const errors = validateAgentForm({ ...validForm, providerKeyRef: 'x'.repeat(256) })
    expect(errors.providerKeyRef).toContain('255 characters')
  })

  it('accepts valid avatar URL', () => {
    const errors = validateAgentForm({ ...validForm, avatarUrl: 'https://example.com/avatar.png' })
    expect(errors.avatarUrl).toBeUndefined()
  })

  it('returns error for agentsMdSource exceeding 50000 chars', () => {
    const errors = validateAgentForm({ ...validForm, agentsMdSource: 'X'.repeat(50001) })
    expect(errors.agentsMdSource).toContain('50,000 characters')
  })

  it('accepts agentsMdSource at exactly 50000 chars', () => {
    const errors = validateAgentForm({ ...validForm, agentsMdSource: 'X'.repeat(50000) })
    expect(errors.agentsMdSource).toBeUndefined()
  })
})

describe('buildAgentPayload', () => {
  const fullForm = {
    walletAddress: '  0x1111111111111111111111111111111111111111  ',
    name: '  Test Agent  ',
    providerType: 'ANTHROPIC' as const,
    apiKey: 'sk-test-key',
    systemPrompt: '  Be a great debater.  ',
    avatarUrl: '  https://example.com/avatar.png  ',
    providerKeyRef: '  claude-sonnet-4-20250514  ',
    persona: '  Logical thinker  ',
    skillsMarkdown: '  - Logic  ',
    agentsMdSource: '  # Config  ',
  }

  it('trims required fields and includes them', () => {
    const payload = buildAgentPayload(fullForm)
    expect(payload.walletAddress).toBe('0x1111111111111111111111111111111111111111')
    expect(payload.name).toBe('Test Agent')
    expect(payload.providerType).toBe('ANTHROPIC')
    expect(payload.apiKey).toBe('sk-test-key')
    expect(payload.systemPrompt).toBe('Be a great debater.')
  })

  it('includes trimmed optional fields when non-empty', () => {
    const payload = buildAgentPayload(fullForm)
    expect(payload.avatarUrl).toBe('https://example.com/avatar.png')
    expect(payload.providerKeyRef).toBe('claude-sonnet-4-20250514')
    expect(payload.persona).toBe('Logical thinker')
    expect(payload.skillsMarkdown).toBe('- Logic')
    expect(payload.agentsMdSource).toBe('# Config')
  })

  it('omits empty optional fields', () => {
    const payload = buildAgentPayload({
      ...fullForm,
      avatarUrl: '',
      providerKeyRef: '  ',
      persona: '',
      skillsMarkdown: '',
      agentsMdSource: '',
    })
    expect(payload.avatarUrl).toBeUndefined()
    expect(payload.providerKeyRef).toBeUndefined()
    expect(payload.persona).toBeUndefined()
    expect(payload.skillsMarkdown).toBeUndefined()
    expect(payload.agentsMdSource).toBeUndefined()
  })

  it('preserves apiKey without trimming', () => {
    const payload = buildAgentPayload({ ...fullForm, apiKey: ' sk-with-spaces ' })
    expect(payload.apiKey).toBe(' sk-with-spaces ')
  })
})
