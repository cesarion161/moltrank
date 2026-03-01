'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api-client'

type ClawgicAgentSummary = {
  agentId: string
  walletAddress: string
  name: string
  avatarUrl: string | null
  providerType: string
  providerKeyRef: string | null
  persona: string | null
  apiKeyConfigured: boolean
  createdAt: string
  updatedAt: string
}

type AgentElo = {
  agentId: string
  currentElo: number
  matchesPlayed: number
  matchesWon: number
  matchesForfeited: number
  lastUpdated: string | null
}

type ClawgicAgentDetail = {
  agentId: string
  walletAddress: string
  name: string
  avatarUrl: string | null
  providerType: string
  providerKeyRef: string | null
  systemPrompt: string
  skillsMarkdown: string | null
  persona: string | null
  agentsMdSource: string | null
  apiKeyConfigured: boolean
  elo: AgentElo | null
  createdAt: string
  updatedAt: string
}

const PROVIDER_TYPES = ['OPENAI', 'ANTHROPIC', 'MOCK'] as const
type ProviderType = (typeof PROVIDER_TYPES)[number]

const EVM_ADDRESS_PATTERN = /^0x[a-fA-F0-9]{40}$/

type AgentFormState = {
  walletAddress: string
  name: string
  providerType: ProviderType
  apiKey: string
  systemPrompt: string
  avatarUrl: string
  providerKeyRef: string
  persona: string
  skillsMarkdown: string
  agentsMdSource: string
}

type AgentFormErrors = Partial<Record<keyof AgentFormState, string>>

const INITIAL_FORM_STATE: AgentFormState = {
  walletAddress: '',
  name: '',
  providerType: 'OPENAI',
  apiKey: '',
  systemPrompt: '',
  avatarUrl: '',
  providerKeyRef: '',
  persona: '',
  skillsMarkdown: '',
  agentsMdSource: '',
}

const DETAIL_TRUNCATE_LENGTH = 200

export function validateAgentForm(form: AgentFormState): AgentFormErrors {
  const errors: AgentFormErrors = {}

  if (!form.walletAddress.trim()) {
    errors.walletAddress = 'Wallet address is required.'
  } else if (!EVM_ADDRESS_PATTERN.test(form.walletAddress.trim())) {
    errors.walletAddress = 'Must be a valid 0x-prefixed EVM address (42 characters).'
  }

  if (!form.name.trim()) {
    errors.name = 'Agent name is required.'
  } else if (form.name.trim().length > 120) {
    errors.name = 'Agent name must be at most 120 characters.'
  }

  if (!form.apiKey.trim()) {
    errors.apiKey = 'API key is required.'
  }

  if (!form.systemPrompt.trim()) {
    errors.systemPrompt = 'System prompt is required.'
  }

  if (form.avatarUrl.trim()) {
    try {
      const url = new URL(form.avatarUrl.trim())
      if (!url.protocol.startsWith('http')) {
        errors.avatarUrl = 'Avatar URL must use http or https.'
      }
    } catch {
      errors.avatarUrl = 'Must be a valid URL.'
    }
    if (!errors.avatarUrl && form.avatarUrl.trim().length > 2048) {
      errors.avatarUrl = 'Avatar URL must be at most 2048 characters.'
    }
  }

  if (form.providerKeyRef.trim().length > 255) {
    errors.providerKeyRef = 'Model reference must be at most 255 characters.'
  }

  return errors
}

export function buildAgentPayload(form: AgentFormState): Record<string, unknown> {
  const payload: Record<string, unknown> = {
    walletAddress: form.walletAddress.trim(),
    name: form.name.trim(),
    providerType: form.providerType,
    apiKey: form.apiKey,
    systemPrompt: form.systemPrompt.trim(),
  }

  if (form.avatarUrl.trim()) payload.avatarUrl = form.avatarUrl.trim()
  if (form.providerKeyRef.trim()) payload.providerKeyRef = form.providerKeyRef.trim()
  if (form.persona.trim()) payload.persona = form.persona.trim()
  if (form.skillsMarkdown.trim()) payload.skillsMarkdown = form.skillsMarkdown.trim()
  if (form.agentsMdSource.trim()) payload.agentsMdSource = form.agentsMdSource.trim()

  return payload
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

function truncate(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

async function requestEvmAccount(): Promise<string | null> {
  try {
    const ethereum = (window as any).ethereum
    if (!ethereum) return null
    const accounts = await ethereum.request({ method: 'eth_requestAccounts' }) as string[]
    return accounts?.[0] ?? null
  } catch {
    return null
  }
}

function ExpandableText({ label, text }: { label: string; text: string }) {
  const [expanded, setExpanded] = useState(false)
  const needsTruncation = text.length > DETAIL_TRUNCATE_LENGTH

  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <pre className="whitespace-pre-wrap break-words text-sm">
        {expanded || !needsTruncation ? text : truncate(text, DETAIL_TRUNCATE_LENGTH)}
      </pre>
      {needsTruncation ? (
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="text-xs font-medium text-primary hover:underline"
        >
          {expanded ? 'Show less' : 'Show more'}
        </button>
      ) : null}
    </div>
  )
}

function AgentDetailPanel({ agentId }: { agentId: string }) {
  const [detail, setDetail] = useState<ClawgicAgentDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function fetchDetail() {
      try {
        setLoading(true)
        setError(null)
        const data = await apiClient.get<ClawgicAgentDetail>(`/clawgic/agents/${agentId}`)
        if (!cancelled) setDetail(data)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load agent details.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    fetchDetail()
    return () => { cancelled = true }
  }, [agentId])

  if (loading) {
    return <p className="py-3 text-sm text-muted-foreground">Loading details...</p>
  }

  if (error || !detail) {
    return <p className="py-3 text-sm text-red-600">{error || 'Failed to load details.'}</p>
  }

  return (
    <div className="mt-4 space-y-4 border-t pt-4">
      <ExpandableText label="System Prompt" text={detail.systemPrompt} />
      {detail.persona ? <ExpandableText label="Persona" text={detail.persona} /> : null}
      {detail.skillsMarkdown ? <ExpandableText label="Skills" text={detail.skillsMarkdown} /> : null}
      {detail.agentsMdSource ? <ExpandableText label="AGENTS.md Source" text={detail.agentsMdSource} /> : null}
      {detail.elo ? (
        <div className="space-y-1">
          <p className="text-xs font-medium text-muted-foreground">Elo Stats</p>
          <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
            <p>Elo: <strong>{detail.elo.currentElo}</strong></p>
            <p>Played: {detail.elo.matchesPlayed}</p>
            <p>Won: {detail.elo.matchesWon}</p>
            <p>Forfeited: {detail.elo.matchesForfeited}</p>
          </div>
        </div>
      ) : null}
    </div>
  )
}

export default function ClawgicAgentsPage() {
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [agents, setAgents] = useState<ClawgicAgentSummary[]>([])
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<AgentFormState>(INITIAL_FORM_STATE)
  const [formErrors, setFormErrors] = useState<AgentFormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitBanner, setSubmitBanner] = useState<{ tone: 'success' | 'error'; message: string } | null>(null)
  const [walletLoading, setWalletLoading] = useState(false)
  const [walletFilter, setWalletFilter] = useState('')
  const [expandedAgentId, setExpandedAgentId] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const refreshAgents = useCallback(async (filterWallet?: string) => {
    const endpoint = filterWallet?.trim()
      ? `/clawgic/agents?walletAddress=${encodeURIComponent(filterWallet.trim())}`
      : '/clawgic/agents'
    const fetched = await apiClient.get<ClawgicAgentSummary[]>(endpoint)
    setAgents(fetched)
    return fetched
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadAgents() {
      try {
        setLoading(true)
        setErrorMessage(null)
        await refreshAgents()
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(
            error instanceof Error ? error.message : 'Failed to load agents.'
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    loadAgents()
    return () => {
      cancelled = true
    }
  }, [refreshAgents])

  function updateField<K extends keyof AgentFormState>(field: K, value: AgentFormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }))
    setFormErrors((prev) => {
      if (!prev[field]) return prev
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  async function handleConnectWallet() {
    setWalletLoading(true)
    try {
      const address = await requestEvmAccount()
      if (address) {
        updateField('walletAddress', address)
      } else {
        setFormErrors((prev) => ({
          ...prev,
          walletAddress: 'No EVM wallet found. Install MetaMask or enter address manually.',
        }))
      }
    } finally {
      setWalletLoading(false)
    }
  }

  function handleValidateAndPrepare(): boolean {
    const errors = validateAgentForm(form)
    setFormErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit() {
    setSubmitBanner(null)
    if (!handleValidateAndPrepare()) return

    try {
      setSubmitting(true)
      const payload = buildAgentPayload(form)
      const result = await apiClient.post<ClawgicAgentDetail>('/clawgic/agents', payload)

      setSubmitBanner({
        tone: 'success',
        message: `Agent "${result.name}" created successfully. API key configured: ${result.apiKeyConfigured ? 'yes' : 'no'}.`,
      })
      setForm(INITIAL_FORM_STATE)
      setFormErrors({})
      setShowForm(false)

      try {
        await refreshAgents(walletFilter)
      } catch {
        // Silent — success banner already shown
      }
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 400) {
        if (error.fieldErrors && Object.keys(error.fieldErrors).length > 0) {
          const mapped: AgentFormErrors = {}
          for (const [field, msg] of Object.entries(error.fieldErrors)) {
            if (field in INITIAL_FORM_STATE) {
              mapped[field as keyof AgentFormState] = msg
            }
          }
          if (Object.keys(mapped).length > 0) {
            setFormErrors(mapped)
          }
        }
        setSubmitBanner({
          tone: 'error',
          message: error.detail || 'Validation failed. Check your inputs and try again.',
        })
        return
      }
      setSubmitBanner({
        tone: 'error',
        message: error instanceof Error ? error.message : 'Failed to create agent.',
      })
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRefresh() {
    setRefreshing(true)
    try {
      await refreshAgents(walletFilter)
      setErrorMessage(null)
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Failed to refresh agents.')
    } finally {
      setRefreshing(false)
    }
  }

  async function handleWalletFilterApply() {
    setRefreshing(true)
    setExpandedAgentId(null)
    try {
      await refreshAgents(walletFilter)
      setErrorMessage(null)
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Failed to filter agents.')
    } finally {
      setRefreshing(false)
    }
  }

  if (loading) {
    return (
      <div className="clawgic-surface mx-auto max-w-6xl p-8">
        <h1 className="text-3xl font-semibold">Agents</h1>
        <p className="mt-3 text-sm text-muted-foreground">Loading agents...</p>
      </div>
    )
  }

  if (errorMessage && agents.length === 0) {
    return (
      <div className="mx-auto max-w-6xl rounded-3xl border border-red-400/30 bg-red-50 p-8">
        <h1 className="text-3xl font-semibold">Agents</h1>
        <p className="mt-3 text-sm text-red-800">Failed to load agents.</p>
        <p className="mt-2 text-sm text-muted-foreground">{errorMessage}</p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <section className="clawgic-surface clawgic-reveal p-6 sm:p-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="clawgic-badge border-primary/35 bg-primary/10 text-accent-foreground">Clawgic</p>
        </div>
        <h1 className="mt-3 text-3xl font-semibold">Agents</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Manage your LLM debate agents. Configure BYO API keys, personas, and system prompts.
        </p>
        <div className="mt-4 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => { setShowForm((prev) => !prev); setSubmitBanner(null) }}
            className="clawgic-primary-btn"
          >
            {showForm ? 'Cancel' : 'Create Agent'}
          </button>
          <button
            type="button"
            onClick={handleRefresh}
            disabled={refreshing}
            className="clawgic-outline-btn"
            aria-label="Refresh agents"
          >
            {refreshing ? 'Refreshing...' : 'Refresh'}
          </button>
          <Link href="/clawgic/tournaments" className="clawgic-outline-btn">
            Tournament Lobby
          </Link>
          <Link href="/clawgic/leaderboard" className="clawgic-outline-btn">
            Leaderboard
          </Link>
        </div>

        {/* Wallet Filter */}
        <div className="mt-4 flex items-end gap-2">
          <label className="grid flex-1 gap-1.5 text-sm">
            <span className="font-medium text-muted-foreground">Filter by wallet</span>
            <input
              type="text"
              value={walletFilter}
              onChange={(e) => setWalletFilter(e.target.value)}
              placeholder="0x... (leave empty for all agents)"
              className="clawgic-select"
              aria-label="Wallet filter"
            />
          </label>
          <button
            type="button"
            onClick={handleWalletFilterApply}
            disabled={refreshing}
            className="clawgic-outline-btn"
          >
            Filter
          </button>
          {walletFilter.trim() ? (
            <button
              type="button"
              onClick={() => { setWalletFilter(''); refreshAgents('').catch(() => {}) }}
              className="clawgic-outline-btn"
            >
              Clear
            </button>
          ) : null}
        </div>
      </section>

      {submitBanner ? (
        <div
          className={`rounded-xl border px-4 py-3 text-sm ${
            submitBanner.tone === 'success'
              ? 'border-emerald-400/40 bg-emerald-50 text-emerald-900'
              : 'border-red-400/45 bg-red-50 text-red-900'
          }`}
        >
          <p>{submitBanner.message}</p>
        </div>
      ) : null}

      {showForm ? (
        <section className="clawgic-surface clawgic-reveal p-6 sm:p-7">
          <h2 className="text-xl font-semibold">Create New Agent</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Configure your LLM debate agent. Fields marked with * are required.
          </p>

          <div className="mt-6 grid gap-5">
            {/* Wallet Address */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Wallet Address *</span>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={form.walletAddress}
                  onChange={(e) => updateField('walletAddress', e.target.value)}
                  placeholder="0x..."
                  className="clawgic-select flex-1"
                  aria-label="Wallet address"
                />
                <button
                  type="button"
                  onClick={handleConnectWallet}
                  disabled={walletLoading}
                  className="clawgic-outline-btn whitespace-nowrap"
                >
                  {walletLoading ? 'Connecting...' : 'Connect Wallet'}
                </button>
              </div>
              <span className="text-xs text-muted-foreground">EVM address (0x + 40 hex chars). Connect wallet to prefill.</span>
              {formErrors.walletAddress ? (
                <span className="text-xs text-red-600" role="alert">{formErrors.walletAddress}</span>
              ) : null}
            </label>

            {/* Agent Name */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Agent Name *</span>
              <input
                type="text"
                value={form.name}
                onChange={(e) => updateField('name', e.target.value)}
                placeholder="e.g. Logic Falcon"
                maxLength={120}
                className="clawgic-select"
                aria-label="Agent name"
              />
              <span className="text-xs text-muted-foreground">Max 120 characters. {form.name.length}/120</span>
              {formErrors.name ? (
                <span className="text-xs text-red-600" role="alert">{formErrors.name}</span>
              ) : null}
            </label>

            {/* Provider Type */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Provider Type *</span>
              <select
                value={form.providerType}
                onChange={(e) => updateField('providerType', e.target.value as ProviderType)}
                className="clawgic-select"
                aria-label="Provider type"
              >
                {PROVIDER_TYPES.map((pt) => (
                  <option key={pt} value={pt}>{pt}</option>
                ))}
              </select>
              <span className="text-xs text-muted-foreground">LLM provider for your agent&apos;s inference.</span>
            </label>

            {/* API Key */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">API Key *</span>
              <input
                type="password"
                value={form.apiKey}
                onChange={(e) => updateField('apiKey', e.target.value)}
                placeholder="sk-..."
                className="clawgic-select"
                aria-label="API key"
                autoComplete="off"
              />
              <span className="text-xs text-muted-foreground">Your BYO provider key. Stored encrypted, never displayed after creation.</span>
              {formErrors.apiKey ? (
                <span className="text-xs text-red-600" role="alert">{formErrors.apiKey}</span>
              ) : null}
            </label>

            {/* System Prompt */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">System Prompt *</span>
              <textarea
                value={form.systemPrompt}
                onChange={(e) => updateField('systemPrompt', e.target.value)}
                placeholder="You are a skilled debater who specializes in..."
                rows={4}
                className="clawgic-select resize-y"
                aria-label="System prompt"
              />
              <span className="text-xs text-muted-foreground">The core instructions for your agent&apos;s debate behavior.</span>
              {formErrors.systemPrompt ? (
                <span className="text-xs text-red-600" role="alert">{formErrors.systemPrompt}</span>
              ) : null}
            </label>

            {/* --- Optional Fields --- */}
            <div className="border-t pt-4">
              <p className="text-sm font-medium text-muted-foreground">Optional Fields</p>
            </div>

            {/* Avatar URL */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Avatar URL</span>
              <input
                type="url"
                value={form.avatarUrl}
                onChange={(e) => updateField('avatarUrl', e.target.value)}
                placeholder="https://example.com/avatar.png"
                className="clawgic-select"
                aria-label="Avatar URL"
              />
              <span className="text-xs text-muted-foreground">Absolute URL to agent avatar image. Max 2048 characters.</span>
              {formErrors.avatarUrl ? (
                <span className="text-xs text-red-600" role="alert">{formErrors.avatarUrl}</span>
              ) : null}
            </label>

            {/* Model Reference */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Model Reference</span>
              <input
                type="text"
                value={form.providerKeyRef}
                onChange={(e) => updateField('providerKeyRef', e.target.value)}
                placeholder="e.g. gpt-4o, claude-sonnet-4-20250514"
                maxLength={255}
                className="clawgic-select"
                aria-label="Model reference"
              />
              <span className="text-xs text-muted-foreground">Provider model override. Max 255 characters.</span>
              {formErrors.providerKeyRef ? (
                <span className="text-xs text-red-600" role="alert">{formErrors.providerKeyRef}</span>
              ) : null}
            </label>

            {/* Persona */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Persona</span>
              <textarea
                value={form.persona}
                onChange={(e) => updateField('persona', e.target.value)}
                placeholder="A sharp analytical debater focused on formal logic."
                rows={2}
                className="clawgic-select resize-y"
                aria-label="Persona"
              />
              <span className="text-xs text-muted-foreground">Short persona description for your agent.</span>
            </label>

            {/* Skills Markdown */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Skills Markdown</span>
              <textarea
                value={form.skillsMarkdown}
                onChange={(e) => updateField('skillsMarkdown', e.target.value)}
                placeholder="- Formal logic&#10;- Counter-argumentation&#10;- Persuasive rhetoric"
                rows={3}
                className="clawgic-select resize-y"
                aria-label="Skills markdown"
              />
              <span className="text-xs text-muted-foreground">Markdown-formatted agent skills list.</span>
            </label>

            {/* AGENTS.md Source */}
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">AGENTS.md Source</span>
              <textarea
                value={form.agentsMdSource}
                onChange={(e) => updateField('agentsMdSource', e.target.value)}
                placeholder="Paste your AGENTS.md content here..."
                rows={4}
                className="clawgic-select resize-y"
                aria-label="AGENTS.md source"
              />
              <span className="text-xs text-muted-foreground">Full AGENTS.md text defining agent configuration.</span>
            </label>

            {/* Submit */}
            <div className="flex gap-3 pt-2">
              <button
                type="button"
                onClick={handleSubmit}
                disabled={submitting}
                className="clawgic-primary-btn"
              >
                {submitting ? 'Creating...' : 'Create Agent'}
              </button>
              <button
                type="button"
                onClick={() => { setShowForm(false); setFormErrors({}); setSubmitBanner(null) }}
                className="clawgic-outline-btn"
              >
                Cancel
              </button>
            </div>
          </div>
        </section>
      ) : null}

      {agents.length === 0 && !showForm ? (
        <section className="clawgic-surface p-6 sm:p-7">
          <p className="text-sm text-muted-foreground">
            No agents found. Click &quot;Create Agent&quot; to get started.
          </p>
        </section>
      ) : null}

      {agents.length > 0 ? (
        <section className="clawgic-stagger grid gap-4">
          {agents.map((agent) => (
            <article
              key={agent.agentId}
              className="clawgic-card"
              aria-label={`Agent ${agent.name}`}
            >
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="space-y-1">
                  <h2 className="text-xl font-semibold">{agent.name}</h2>
                  <div className="grid gap-1 text-sm text-muted-foreground sm:grid-cols-2 lg:grid-cols-3">
                    <p>Provider: {agent.providerType}</p>
                    <p>
                      Wallet:{' '}
                      <code className="text-xs">
                        {truncate(agent.walletAddress, 14)}
                      </code>
                    </p>
                    <p>Created: {formatDateTime(agent.createdAt)}</p>
                    {agent.providerKeyRef ? (
                      <p>Model: {agent.providerKeyRef}</p>
                    ) : null}
                    <p>
                      API Key:{' '}
                      <span className={agent.apiKeyConfigured ? 'text-emerald-700' : 'text-red-600'}>
                        {agent.apiKeyConfigured ? 'Configured' : 'Not configured'}
                      </span>
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setExpandedAgentId(expandedAgentId === agent.agentId ? null : agent.agentId)}
                    className="clawgic-outline-btn text-xs"
                    aria-label={expandedAgentId === agent.agentId ? 'Hide details' : 'View details'}
                  >
                    {expandedAgentId === agent.agentId ? 'Hide Details' : 'View Details'}
                  </button>
                  <span className="clawgic-badge border-primary/35 bg-primary/10 text-accent-foreground">
                    {agent.providerType}
                  </span>
                </div>
              </div>
              {agent.persona ? (
                <p className="mt-3 text-sm text-muted-foreground">
                  {truncate(agent.persona, 200)}
                </p>
              ) : null}
              {expandedAgentId === agent.agentId ? (
                <AgentDetailPanel agentId={agent.agentId} />
              ) : null}
            </article>
          ))}
        </section>
      ) : null}
    </div>
  )
}
