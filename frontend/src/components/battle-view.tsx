'use client'

import { useMemo, useRef, useEffect, useState } from 'react'
import CountdownTimer from './countdown-timer'

export type TranscriptMessage = {
  role: string
  phase: string
  content: string
}

export type BattleViewAgentInfo = {
  name: string
  providerType: string
  elo: number | null
}

export type BattleViewMatchDetail = {
  matchId: string
  agent1Id: string | null
  agent2Id: string | null
  status: string
  phase: string | null
  transcriptJson: unknown
  winnerAgentId: string | null
  forfeitReason: string | null
  executionDeadlineAt: string | null
  startedAt: string | null
}

export type BattleViewProps = {
  matchDetail: BattleViewMatchDetail
  agent1Info: BattleViewAgentInfo
  agent2Info: BattleViewAgentInfo
}

export const DEBATE_PHASES = ['THESIS_DISCOVERY', 'ARGUMENTATION', 'COUNTER_ARGUMENTATION', 'CONCLUSION'] as const

export function formatPhase(phase: string): string {
  return phase.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}

export function parseTranscriptMessages(value: unknown): TranscriptMessage[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!item || typeof item !== 'object') return []
    const candidate = item as Record<string, unknown>
    if (
      typeof candidate.role !== 'string' ||
      typeof candidate.phase !== 'string' ||
      typeof candidate.content !== 'string'
    ) {
      return []
    }
    return [{ role: candidate.role, phase: candidate.phase, content: candidate.content }]
  })
}

export function determineActiveAgent(transcriptLength: number, matchStatus: string): 'agent_1' | 'agent_2' | null {
  if (matchStatus !== 'IN_PROGRESS') return null
  if (transcriptLength >= 8) return null
  return transcriptLength % 2 === 0 ? 'agent_1' : 'agent_2'
}

function providerBadge(providerType: string): { label: string; className: string } {
  switch (providerType) {
    case 'OPENAI':
      return { label: 'OpenAI', className: 'border-emerald-400/40 bg-emerald-50 text-emerald-800' }
    case 'ANTHROPIC':
      return { label: 'Anthropic', className: 'border-orange-400/40 bg-orange-50 text-orange-800' }
    case 'MOCK':
      return { label: 'Mock', className: 'border-slate-400/40 bg-slate-50 text-slate-700' }
    default:
      return { label: providerType, className: 'border-slate-400/40 bg-slate-50 text-slate-700' }
  }
}

type PhaseContentEntry = {
  phase: string
  agent1Msg: TranscriptMessage | null
  agent2Msg: TranscriptMessage | null
}

export default function BattleView({ matchDetail, agent1Info, agent2Info }: BattleViewProps) {
  const transcript = useMemo(() => parseTranscriptMessages(matchDetail.transcriptJson), [matchDetail.transcriptJson])
  const prevLengthRef = useRef(transcript.length)
  const agent1ScrollRef = useRef<HTMLDivElement>(null)
  const agent2ScrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (transcript.length > prevLengthRef.current) {
      const lastMsg = transcript[transcript.length - 1]
      const ref = lastMsg?.role === 'agent_1' ? agent1ScrollRef : agent2ScrollRef
      ref.current?.scrollTo({ top: ref.current.scrollHeight, behavior: 'smooth' })
    }
    prevLengthRef.current = transcript.length
  }, [transcript])

  const activeAgent = determineActiveAgent(transcript.length, matchDetail.status)
  const isCompleted = matchDetail.status === 'COMPLETED'
  const isPendingJudge = matchDetail.status === 'PENDING_JUDGE'
  const isInProgress = matchDetail.status === 'IN_PROGRESS'
  const isForfeited = matchDetail.status === 'FORFEITED'
  const isScheduled = matchDetail.status === 'SCHEDULED'

  const isAgent1Winner = isCompleted && matchDetail.winnerAgentId === matchDetail.agent1Id && matchDetail.agent1Id != null
  const isAgent2Winner = isCompleted && matchDetail.winnerAgentId === matchDetail.agent2Id && matchDetail.agent2Id != null
  const agent1Forfeited = isForfeited && matchDetail.agent1Id != null && (matchDetail.forfeitReason?.includes(matchDetail.agent1Id) ?? false)
  const agent2Forfeited = isForfeited && matchDetail.agent2Id != null && (matchDetail.forfeitReason?.includes(matchDetail.agent2Id) ?? false)

  const currentPhaseIdx = matchDetail.phase ? (DEBATE_PHASES as readonly string[]).indexOf(matchDetail.phase) : -1

  const phaseContent = useMemo<PhaseContentEntry[]>(() => {
    return DEBATE_PHASES.map((phase) => ({
      phase,
      agent1Msg: transcript.find((m) => m.phase === phase && m.role === 'agent_1') ?? null,
      agent2Msg: transcript.find((m) => m.phase === phase && m.role === 'agent_2') ?? null,
    }))
  }, [transcript])

  const generatingPhaseIdx = isInProgress && activeAgent ? Math.floor(transcript.length / 2) : -1

  return (
    <div data-testid="battle-view">
      {/* Phase Progress Stepper */}
      <div className="mb-5" data-testid="phase-stepper">
        <div className="flex items-center gap-1">
          {DEBATE_PHASES.map((p, i) => {
            const isDone = i < currentPhaseIdx || isCompleted || isPendingJudge
            const isCurrent = i === currentPhaseIdx && isInProgress
            return (
              <div key={p} className="flex flex-1 flex-col items-center gap-1">
                <div
                  className={`h-2.5 w-full rounded-full transition-colors duration-300 ${
                    isDone ? 'bg-emerald-500' : isCurrent ? 'bg-primary animate-pulse' : 'bg-slate-200'
                  }`}
                  data-testid={`phase-bar-${p}`}
                />
                <span
                  className={`text-[10px] hidden sm:block ${
                    isCurrent ? 'font-semibold text-primary' : 'text-muted-foreground'
                  }`}
                >
                  {formatPhase(p)}
                </span>
              </div>
            )
          })}
        </div>
      </div>

      {/* Turn Timer */}
      {isInProgress && activeAgent && matchDetail.executionDeadlineAt && matchDetail.startedAt && (
        <TurnTimerBar
          executionDeadlineAt={matchDetail.executionDeadlineAt}
          startedAt={matchDetail.startedAt}
          turnsCompleted={transcript.length}
          activeAgent={activeAgent}
          agent1Name={agent1Info.name}
          agent2Name={agent2Info.name}
        />
      )}

      {/* Side-by-Side Agent Panels */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <AgentPanel
          side="agent_1"
          agentInfo={agent1Info}
          isWinner={isAgent1Winner}
          isForfeited={agent1Forfeited}
          isActive={activeAgent === 'agent_1'}
          forfeitReason={agent1Forfeited ? matchDetail.forfeitReason : null}
          phaseContent={phaseContent}
          currentPhaseIdx={currentPhaseIdx}
          generatingPhaseIdx={generatingPhaseIdx}
          activeAgent={activeAgent}
          matchStatus={matchDetail.status}
          scrollRef={agent1ScrollRef}
        />
        <AgentPanel
          side="agent_2"
          agentInfo={agent2Info}
          isWinner={isAgent2Winner}
          isForfeited={agent2Forfeited}
          isActive={activeAgent === 'agent_2'}
          forfeitReason={agent2Forfeited ? matchDetail.forfeitReason : null}
          phaseContent={phaseContent}
          currentPhaseIdx={currentPhaseIdx}
          generatingPhaseIdx={generatingPhaseIdx}
          activeAgent={activeAgent}
          matchStatus={matchDetail.status}
          scrollRef={agent2ScrollRef}
        />
      </div>

      {/* Pending Judge Overlay */}
      {isPendingJudge && (
        <div
          className="mt-4 rounded-xl border border-amber-400/30 bg-amber-50 px-4 py-3 text-sm text-amber-800 flex items-center justify-center gap-2"
          data-testid="pending-judge"
        >
          <span className="inline-block h-3 w-3 rounded-full border-2 border-amber-500 border-t-transparent animate-spin" />
          Awaiting judge verdict...
        </div>
      )}

      {/* Winner Banner */}
      {isCompleted && matchDetail.winnerAgentId && (
        <div
          className="mt-4 rounded-xl border border-emerald-400/30 bg-emerald-50 px-4 py-3 text-center text-sm text-emerald-800 font-semibold"
          data-testid="winner-banner"
        >
          {`Winner: ${matchDetail.winnerAgentId === matchDetail.agent1Id ? agent1Info.name : agent2Info.name}`}
        </div>
      )}

      {/* Scheduled / Not Started */}
      {isScheduled && transcript.length === 0 && (
        <div className="mt-4 text-center text-sm text-muted-foreground py-8" data-testid="not-started">
          Match has not started yet.
        </div>
      )}
    </div>
  )
}

function AgentPanel({
  side,
  agentInfo,
  isWinner,
  isForfeited,
  isActive,
  forfeitReason,
  phaseContent,
  currentPhaseIdx,
  generatingPhaseIdx,
  activeAgent,
  matchStatus,
  scrollRef,
}: {
  side: 'agent_1' | 'agent_2'
  agentInfo: BattleViewAgentInfo
  isWinner: boolean
  isForfeited: boolean
  isActive: boolean
  forfeitReason: string | null
  phaseContent: PhaseContentEntry[]
  currentPhaseIdx: number
  generatingPhaseIdx: number
  activeAgent: 'agent_1' | 'agent_2' | null
  matchStatus: string
  scrollRef: React.RefObject<HTMLDivElement | null>
}) {
  const isAgent1 = side === 'agent_1'
  const badge = providerBadge(agentInfo.providerType)
  const isCompleted = matchStatus === 'COMPLETED'
  const isPendingJudge = matchStatus === 'PENDING_JUDGE'
  const isInProgress = matchStatus === 'IN_PROGRESS'
  const isForfeitedMatch = matchStatus === 'FORFEITED'

  return (
    <div
      className={`rounded-2xl border-2 transition-all duration-300 ${
        isWinner
          ? 'border-emerald-400 bg-emerald-50/30 shadow-lg shadow-emerald-100'
          : isForfeited
            ? 'border-red-300 bg-red-50/30'
            : isAgent1
              ? 'border-primary/30 bg-primary/5'
              : 'border-blue-300/40 bg-blue-50/30'
      }`}
      data-testid={`${side}-panel`}
    >
      {/* Agent Header */}
      <div className="flex items-center gap-3 border-b border-inherit px-4 py-3">
        <div
          className={`flex h-9 w-9 items-center justify-center rounded-full text-sm font-bold ${
            isAgent1 ? 'bg-primary/20 text-primary' : 'bg-blue-100 text-blue-700'
          }`}
        >
          {isAgent1 ? 'A1' : 'A2'}
        </div>
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm truncate" data-testid={`${side}-name`}>
            {agentInfo.name}
          </p>
          <div className="flex items-center gap-2 mt-0.5">
            <span
              className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${badge.className}`}
              data-testid={`${side}-provider`}
            >
              {badge.label}
            </span>
            {agentInfo.elo != null && (
              <span className="text-xs text-muted-foreground" data-testid={`${side}-elo`}>
                {`Elo: ${agentInfo.elo}`}
              </span>
            )}
          </div>
        </div>
        {isWinner && (
          <span className="text-xs font-bold text-emerald-700 uppercase" data-testid={`${side}-winner-badge`}>
            Winner
          </span>
        )}
        {isActive && (
          <span
            className={`flex items-center gap-1 text-xs font-medium ${isAgent1 ? 'text-primary' : 'text-blue-700'}`}
            data-testid={`${side}-active`}
          >
            <span
              className={`inline-block h-2 w-2 rounded-full animate-pulse ${isAgent1 ? 'bg-primary' : 'bg-blue-600'}`}
            />
            Active
          </span>
        )}
      </div>

      {/* Agent Responses */}
      <div className="p-4 space-y-3 max-h-[500px] overflow-y-auto" ref={scrollRef}>
        {phaseContent.map(({ phase, agent1Msg, agent2Msg }) => {
          const msg = isAgent1 ? agent1Msg : agent2Msg
          const phaseIdx = (DEBATE_PHASES as readonly string[]).indexOf(phase)
          const phaseReached = phaseIdx <= currentPhaseIdx || isCompleted || isPendingJudge || isForfeitedMatch
          if (!phaseReached && !msg) return null

          const isGeneratingHere = isInProgress && activeAgent === side && phaseIdx === generatingPhaseIdx && !msg

          return (
            <div key={phase}>
              <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground mb-1.5">
                {formatPhase(phase)}
              </p>
              {msg ? (
                <div
                  className={`rounded-xl bg-white/70 border px-3 py-2.5 text-sm text-foreground whitespace-pre-wrap battle-message-enter ${
                    isAgent1 ? 'border-primary/15' : 'border-blue-200/40'
                  }`}
                  data-testid={`${side}-${phase}`}
                >
                  {msg.content}
                </div>
              ) : isGeneratingHere ? (
                <div
                  className={`flex items-center gap-2 rounded-xl bg-white/50 border border-dashed px-3 py-3 text-sm text-muted-foreground ${
                    isAgent1 ? 'border-primary/20' : 'border-blue-200/30'
                  }`}
                  data-testid={`${side}-generating`}
                >
                  <span
                    className={`inline-block h-2 w-2 rounded-full animate-pulse ${
                      isAgent1 ? 'bg-primary' : 'bg-blue-600'
                    }`}
                  />
                  Generating...
                </div>
              ) : phaseReached ? (
                <div className="rounded-xl bg-white/30 border border-dashed border-slate-200 px-3 py-2.5 text-sm text-muted-foreground italic">
                  Waiting...
                </div>
              ) : null}
            </div>
          )
        })}
        {isForfeited && forfeitReason && (
          <div
            className="rounded-xl border border-red-300 bg-red-50 px-3 py-2 text-xs text-red-800"
            data-testid={`${side}-forfeit`}
          >
            {`Forfeited: ${forfeitReason}`}
          </div>
        )}
      </div>
    </div>
  )
}

function TurnTimerBar({
  executionDeadlineAt,
  startedAt,
  turnsCompleted,
  activeAgent,
  agent1Name,
  agent2Name,
}: {
  executionDeadlineAt: string
  startedAt: string
  turnsCompleted: number
  activeAgent: 'agent_1' | 'agent_2'
  agent1Name: string
  agent2Name: string
}) {
  const turnDeadlineIso = useMemo(() => {
    const deadlineMs = new Date(executionDeadlineAt).getTime()
    const startMs = new Date(startedAt).getTime()
    if (Number.isNaN(deadlineMs) || Number.isNaN(startMs)) return null
    const perTurnMs = (deadlineMs - startMs) / 8
    const turnMs = Math.min(startMs + (turnsCompleted + 1) * perTurnMs, deadlineMs)
    return new Date(turnMs).toISOString()
  }, [executionDeadlineAt, startedAt, turnsCompleted])

  const [urgent, setUrgent] = useState(false)

  useEffect(() => {
    if (!turnDeadlineIso) return
    const targetMs = new Date(turnDeadlineIso).getTime()
    const check = () => setUrgent(targetMs - Date.now() < 10_000)
    check()
    const interval = setInterval(check, 1000)
    return () => clearInterval(interval)
  }, [turnDeadlineIso])

  if (!turnDeadlineIso) return null

  const agentName = activeAgent === 'agent_1' ? agent1Name : agent2Name

  return (
    <div
      className={`mb-4 flex items-center justify-center gap-3 rounded-xl border px-4 py-2 transition-colors ${
        urgent ? 'border-red-400/50 bg-red-50/80' : 'border-border/60 bg-white/80'
      }`}
      data-testid="turn-timer"
    >
      <span
        className={`inline-block h-2.5 w-2.5 rounded-full animate-pulse ${
          activeAgent === 'agent_1' ? 'bg-primary' : 'bg-blue-600'
        }`}
      />
      <span className={`text-sm font-medium ${urgent ? 'text-red-800' : ''}`}>
        {`${agentName}'s turn`}
      </span>
      <CountdownTimer
        targetTime={turnDeadlineIso}
        className={`text-sm ${urgent ? 'font-bold' : ''}`}
      />
    </div>
  )
}
