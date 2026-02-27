package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.DebatePhase;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.model.DebateTranscriptMessage;
import com.moltrank.clawgic.model.DebateTranscriptRole;
import com.moltrank.clawgic.provider.ClawgicProviderTurnInput;
import com.moltrank.clawgic.provider.ClawgicProviderTurnResponse;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class ClawgicDebateExecutionService {

    private static final int TURNS_PER_PHASE = 2;
    private static final String DEFAULT_SYSTEM_PROMPT = "Debate clearly and challenge weak assumptions.";
    private static final String FORFEIT_REASON_TIMEOUT = "PROVIDER_TIMEOUT";
    private static final String FORFEIT_REASON_AUTH_FAILURE = "PROVIDER_AUTH_FAILURE";
    private static final String FORFEIT_REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
    private static final List<String> AUTH_FAILURE_SIGNALS = List.of(
            "unauthorized",
            "forbidden",
            "authentication",
            "invalid api key",
            "invalid_api_key",
            "401",
            "403"
    );

    private final ClawgicMatchRepository clawgicMatchRepository;
    private final ClawgicTournamentRepository clawgicTournamentRepository;
    private final ClawgicAgentRepository clawgicAgentRepository;
    private final ClawgicDebateProviderGateway clawgicDebateProviderGateway;
    private final ClawgicJudgeQueuePublisher clawgicJudgeQueuePublisher;
    private final ClawgicRuntimeProperties clawgicRuntimeProperties;

    public ClawgicMatch executeMatch(UUID matchId) {
        ClawgicMatch match = clawgicMatchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic match not found: " + matchId
                ));
        if (match.getStatus() != ClawgicMatchStatus.SCHEDULED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only SCHEDULED matches can be executed: " + matchId
            );
        }
        if (match.getAgent1Id() == null || match.getAgent2Id() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Match requires both agents before execution: " + matchId
            );
        }

        ClawgicTournament tournament = clawgicTournamentRepository.findById(match.getTournamentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic tournament not found for match: " + match.getTournamentId()
                ));
        ClawgicAgent agent1 = loadAgent(match.getAgent1Id());
        ClawgicAgent agent2 = loadAgent(match.getAgent2Id());

        List<DebateTranscriptMessage> transcript = decodeTranscript(match);
        markExecutionStarted(match, transcript);

        try {
            for (DebatePhase phase : DebatePhase.orderedValues()) {
                transcript = executeTurn(
                        match,
                        tournament,
                        agent1,
                        DebateTranscriptRole.AGENT_1,
                        phase,
                        transcript
                );
                transcript = executeTurn(
                        match,
                        tournament,
                        agent2,
                        DebateTranscriptRole.AGENT_2,
                        phase,
                        transcript
                );
            }
        } catch (ProviderTurnFailureException ex) {
            return markForfeited(match, ex);
        }

        OffsetDateTime now = OffsetDateTime.now();
        match.setStatus(ClawgicMatchStatus.PENDING_JUDGE);
        match.setPhase(DebatePhase.CONCLUSION);
        match.setJudgeRequestedAt(now);
        match.setUpdatedAt(now);
        ClawgicMatch pendingJudgeMatch = clawgicMatchRepository.saveAndFlush(match);
        clawgicJudgeQueuePublisher.publishMatchReady(pendingJudgeMatch.getMatchId());
        return pendingJudgeMatch;
    }

    private void markExecutionStarted(ClawgicMatch match, List<DebateTranscriptMessage> transcript) {
        OffsetDateTime now = OffsetDateTime.now();
        match.setStatus(ClawgicMatchStatus.IN_PROGRESS);
        match.setPhase(DebatePhase.THESIS_DISCOVERY);
        if (match.getStartedAt() == null) {
            match.setStartedAt(now);
        }
        if (match.getExecutionDeadlineAt() == null) {
            match.setExecutionDeadlineAt(match.getStartedAt().plusSeconds(resolveExecutionBudgetSeconds()));
        }
        match.setTranscriptJson(DebateTranscriptJsonCodec.toJson(transcript));
        match.setUpdatedAt(now);
        clawgicMatchRepository.saveAndFlush(match);
    }

    private List<DebateTranscriptMessage> executeTurn(
            ClawgicMatch match,
            ClawgicTournament tournament,
            ClawgicAgent agent,
            DebateTranscriptRole role,
            DebatePhase phase,
            List<DebateTranscriptMessage> transcript
    ) {
        ClawgicProviderTurnInput turnInput = new ClawgicProviderTurnInput(
                match.getMatchId(),
                agent.getAgentId(),
                phase,
                tournament.getTopic(),
                buildSystemPrompt(agent, phase),
                transcript,
                resolveMaxResponseWords()
        );

        ClawgicProviderTurnResponse turnResponse = generateTurnWithTimeout(agent, role, phase, turnInput);

        List<DebateTranscriptMessage> nextTranscript = new ArrayList<>(transcript);
        nextTranscript.add(new DebateTranscriptMessage(role, phase, turnResponse.content()));

        match.setPhase(phase);
        match.setTranscriptJson(DebateTranscriptJsonCodec.toJson(nextTranscript));
        match.setUpdatedAt(OffsetDateTime.now());
        clawgicMatchRepository.saveAndFlush(match);
        return List.copyOf(nextTranscript);
    }

    private ClawgicProviderTurnResponse generateTurnWithTimeout(
            ClawgicAgent agent,
            DebateTranscriptRole role,
            DebatePhase phase,
            ClawgicProviderTurnInput turnInput
    ) {
        int timeoutSeconds = resolveProviderTimeoutSeconds();
        FutureTask<ClawgicProviderTurnResponse> providerTask =
                new FutureTask<>(() -> clawgicDebateProviderGateway.generateTurn(agent, turnInput));
        Thread.ofVirtual().name("clawgic-provider-turn-", 0).start(providerTask);

        try {
            return providerTask.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            providerTask.cancel(true);
            throw new ProviderTurnFailureException(
                    agent.getAgentId(),
                    role,
                    phase,
                    FORFEIT_REASON_TIMEOUT,
                    ex
            );
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new ProviderTurnFailureException(
                    agent.getAgentId(),
                    role,
                    phase,
                    resolveForfeitReasonCode(cause),
                    cause
            );
        } catch (InterruptedException ex) {
            providerTask.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Execution thread interrupted while waiting for provider turn",
                    ex
            );
        }
    }

    private ClawgicMatch markForfeited(ClawgicMatch match, ProviderTurnFailureException failure) {
        OffsetDateTime now = OffsetDateTime.now();
        match.setStatus(ClawgicMatchStatus.FORFEITED);
        match.setPhase(failure.phase());
        match.setWinnerAgentId(resolveForfeitWinner(match, failure.failingAgentId()));
        match.setForfeitReason(buildForfeitReason(failure));
        match.setForfeitedAt(now);
        match.setUpdatedAt(now);
        match.setJudgeRequestedAt(null);
        return clawgicMatchRepository.saveAndFlush(match);
    }

    private UUID resolveForfeitWinner(ClawgicMatch match, UUID failingAgentId) {
        if (failingAgentId == null) {
            return null;
        }
        if (failingAgentId.equals(match.getAgent1Id())) {
            return match.getAgent2Id();
        }
        if (failingAgentId.equals(match.getAgent2Id())) {
            return match.getAgent1Id();
        }
        return null;
    }

    private String buildForfeitReason(ProviderTurnFailureException failure) {
        return failure.reasonCode()
                + ": failing_agent_id="
                + failure.failingAgentId()
                + ", role="
                + failure.role()
                + ", phase="
                + failure.phase();
    }

    private String resolveForfeitReasonCode(Throwable failure) {
        String message = failure == null ? "" : String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        for (String signal : AUTH_FAILURE_SIGNALS) {
            if (message.contains(signal)) {
                return FORFEIT_REASON_AUTH_FAILURE;
            }
        }
        return FORFEIT_REASON_PROVIDER_ERROR;
    }

    private ClawgicAgent loadAgent(UUID agentId) {
        return clawgicAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic agent not found: " + agentId
                ));
    }

    private List<DebateTranscriptMessage> decodeTranscript(ClawgicMatch match) {
        try {
            return DebateTranscriptJsonCodec.fromJson(match.getTranscriptJson());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Match transcript is invalid and cannot be executed: " + match.getMatchId(),
                    ex
            );
        }
    }

    private int resolveMaxResponseWords() {
        int maxWords = clawgicRuntimeProperties.getDebate().getMaxResponseWords();
        if (maxWords <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "clawgic.debate.max-response-words must be greater than zero"
            );
        }
        return maxWords;
    }

    private int resolveProviderTimeoutSeconds() {
        int timeoutSeconds = clawgicRuntimeProperties.getDebate().getProviderTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "clawgic.debate.provider-timeout-seconds must be greater than zero"
            );
        }
        return timeoutSeconds;
    }

    private long resolveExecutionBudgetSeconds() {
        return (long) resolveProviderTimeoutSeconds() * DebatePhase.orderedValues().size() * TURNS_PER_PHASE;
    }

    private String buildSystemPrompt(ClawgicAgent agent, DebatePhase phase) {
        String basePrompt = StringUtils.hasText(agent.getSystemPrompt())
                ? agent.getSystemPrompt().trim()
                : DEFAULT_SYSTEM_PROMPT;

        StringBuilder promptBuilder = new StringBuilder(basePrompt)
                .append("\nCurrent phase: ")
                .append(phase.name())
                .append(". ")
                .append(phase.promptInstruction());

        if (StringUtils.hasText(agent.getPersona())) {
            promptBuilder.append("\nPersona constraints: ").append(agent.getPersona().trim());
        }
        return promptBuilder.toString();
    }

    private static final class ProviderTurnFailureException extends RuntimeException {

        private final UUID failingAgentId;
        private final DebateTranscriptRole role;
        private final DebatePhase phase;
        private final String reasonCode;

        private ProviderTurnFailureException(
                UUID failingAgentId,
                DebateTranscriptRole role,
                DebatePhase phase,
                String reasonCode,
                Throwable cause
        ) {
            super(reasonCode, cause);
            this.failingAgentId = failingAgentId;
            this.role = role;
            this.phase = phase;
            this.reasonCode = reasonCode;
        }

        private UUID failingAgentId() {
            return failingAgentId;
        }

        private DebateTranscriptRole role() {
            return role;
        }

        private DebatePhase phase() {
            return phase;
        }

        private String reasonCode() {
            return reasonCode;
        }
    }
}
