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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ClawgicDebateExecutionService {

    private static final int TURNS_PER_PHASE = 2;
    private static final String DEFAULT_SYSTEM_PROMPT = "Debate clearly and challenge weak assumptions.";

    private final ClawgicMatchRepository clawgicMatchRepository;
    private final ClawgicTournamentRepository clawgicTournamentRepository;
    private final ClawgicAgentRepository clawgicAgentRepository;
    private final ClawgicDebateProviderGateway clawgicDebateProviderGateway;
    private final ClawgicRuntimeProperties clawgicRuntimeProperties;

    public ClawgicDebateExecutionService(
            ClawgicMatchRepository clawgicMatchRepository,
            ClawgicTournamentRepository clawgicTournamentRepository,
            ClawgicAgentRepository clawgicAgentRepository,
            ClawgicDebateProviderGateway clawgicDebateProviderGateway,
            ClawgicRuntimeProperties clawgicRuntimeProperties
    ) {
        this.clawgicMatchRepository = clawgicMatchRepository;
        this.clawgicTournamentRepository = clawgicTournamentRepository;
        this.clawgicAgentRepository = clawgicAgentRepository;
        this.clawgicDebateProviderGateway = clawgicDebateProviderGateway;
        this.clawgicRuntimeProperties = clawgicRuntimeProperties;
    }

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

        OffsetDateTime now = OffsetDateTime.now();
        match.setStatus(ClawgicMatchStatus.PENDING_JUDGE);
        match.setPhase(DebatePhase.CONCLUSION);
        match.setJudgeRequestedAt(now);
        match.setUpdatedAt(now);
        return clawgicMatchRepository.saveAndFlush(match);
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

        ClawgicProviderTurnResponse turnResponse = clawgicDebateProviderGateway.generateTurn(agent, turnInput);

        List<DebateTranscriptMessage> nextTranscript = new ArrayList<>(transcript);
        nextTranscript.add(new DebateTranscriptMessage(role, phase, turnResponse.content()));

        match.setPhase(phase);
        match.setTranscriptJson(DebateTranscriptJsonCodec.toJson(nextTranscript));
        match.setUpdatedAt(OffsetDateTime.now());
        clawgicMatchRepository.saveAndFlush(match);
        return List.copyOf(nextTranscript);
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

    private long resolveExecutionBudgetSeconds() {
        int providerTimeoutSeconds = clawgicRuntimeProperties.getDebate().getProviderTimeoutSeconds();
        if (providerTimeoutSeconds <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "clawgic.debate.provider-timeout-seconds must be greater than zero"
            );
        }
        return (long) providerTimeoutSeconds * DebatePhase.orderedValues().size() * TURNS_PER_PHASE;
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
}
