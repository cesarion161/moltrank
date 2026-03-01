import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClawgicAgentsPage, { buildAgentPayload, validateAgentForm } from './page'

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
    createdAt: '2026-02-28T11:00:00Z',
    updatedAt: '2026-02-28T11:00:00Z',
  },
]

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

  it('shows error banner when backend returns 400', async () => {
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

    // Mock 400 response
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
