package com.moltrank.clawgic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.config.ClawgicJudgeProperties;
import com.moltrank.clawgic.dto.JudgeResult;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchJudgement;
import com.moltrank.clawgic.model.ClawgicMatchJudgementStatus;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.model.DebateTranscriptMessage;
import com.moltrank.clawgic.model.JudgeResultJsonCodec;
import com.moltrank.clawgic.repository.ClawgicMatchJudgementRepository;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "clawgic",
        name = {"enabled", "worker.enabled", "judge.enabled"},
        havingValue = "true"
)
public class ClawgicJudgeWorkerService {

    private static final Logger log = LoggerFactory.getLogger(ClawgicJudgeWorkerService.class);
    private static final String ERROR_CODE_INVALID_JUDGE_JSON = "INVALID_JUDGE_JSON";
    private static final String ERROR_CODE_JUDGE_PROVIDER_FAILURE = "JUDGE_PROVIDER_FAILURE";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    private final ClawgicJudgeQueue clawgicJudgeQueue;
    private final ClawgicMatchRepository clawgicMatchRepository;
    private final ClawgicTournamentRepository clawgicTournamentRepository;
    private final ClawgicMatchJudgementRepository clawgicMatchJudgementRepository;
    private final ClawgicJudgeProviderGateway clawgicJudgeProviderGateway;
    private final ClawgicAgentEloService clawgicAgentEloService;
    private final ClawgicTournamentProgressionService clawgicTournamentProgressionService;
    private final ClawgicJudgeProperties clawgicJudgeProperties;
    private final TransactionTemplate transactionTemplate;

    @PostConstruct
    void registerQueueConsumer() {
        clawgicJudgeQueue.setConsumer(this::consumeQueueMessage);
    }

    private void consumeQueueMessage(ClawgicJudgeQueueMessage queueMessage) {
        try {
            transactionTemplate.executeWithoutResult(ignored -> processQueueMessage(queueMessage));
        } catch (RuntimeException ex) {
            log.error("Judge queue message processing failed for match {}", queueMessage.matchId(), ex);
        }
    }

    private void processQueueMessage(ClawgicJudgeQueueMessage queueMessage) {
        ClawgicMatch match = clawgicMatchRepository.findByMatchIdForUpdate(queueMessage.matchId()).orElse(null);
        if (match == null) {
            log.warn("Skipping judge queue message because match {} no longer exists", queueMessage.matchId());
            return;
        }
        if (match.getStatus() != ClawgicMatchStatus.PENDING_JUDGE) {
            log.debug(
                    "Skipping judge queue message for match {} because status is {}",
                    match.getMatchId(),
                    match.getStatus()
            );
            return;
        }

        int attempt = clawgicMatchJudgementRepository.findMaxAttemptByMatchIdAndJudgeKey(
                match.getMatchId(),
                queueMessage.judgeKey()
        ) + 1;
        OffsetDateTime now = OffsetDateTime.now();

        ClawgicJudgeProviderGateway.JudgeEvaluation judgeEvaluation;
        try {
            ClawgicTournament tournament = clawgicTournamentRepository.findById(match.getTournamentId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Clawgic tournament not found for match: " + match.getTournamentId()
                    ));
            List<DebateTranscriptMessage> transcript = DebateTranscriptJsonCodec.fromJson(match.getTranscriptJson());
            judgeEvaluation = clawgicJudgeProviderGateway.evaluate(
                    match,
                    tournament,
                    queueMessage.judgeKey(),
                    transcript
            );
        } catch (RuntimeException ex) {
            persistFailureAndRetry(
                    match,
                    queueMessage,
                    attempt,
                    now,
                    null,
                    ClawgicMatchJudgementStatus.ERROR,
                    ERROR_CODE_JUDGE_PROVIDER_FAILURE,
                    ex
            );
            return;
        }

        try {
            JudgeResult parsedJudgeResult = JudgeResultJsonCodec.fromJson(
                    judgeEvaluation.resultJson(),
                    match.getAgent1Id(),
                    match.getAgent2Id()
            );
            ClawgicMatchJudgement acceptedJudgement = initializeJudgement(
                    match,
                    queueMessage.judgeKey(),
                    judgeEvaluation.model(),
                    attempt,
                    now
            );
            acceptedJudgement.setStatus(ClawgicMatchJudgementStatus.ACCEPTED);
            acceptedJudgement.setJudgedAt(now);
            JudgeResultJsonCodec.applyToMatchJudgement(
                    acceptedJudgement,
                    judgeEvaluation.resultJson(),
                    parsedJudgeResult
            );
            clawgicMatchJudgementRepository.save(acceptedJudgement);

            applyMvpMergePolicy(match, now);
            clawgicMatchRepository.saveAndFlush(match);
            clawgicTournamentProgressionService.completeTournamentIfResolved(match.getTournamentId(), now);
        } catch (IllegalArgumentException ex) {
            persistFailureAndRetry(
                    match,
                    queueMessage,
                    attempt,
                    now,
                    judgeEvaluation.resultJson(),
                    ClawgicMatchJudgementStatus.REJECTED,
                    ERROR_CODE_INVALID_JUDGE_JSON,
                    ex
            );
        }
    }

    private void persistFailureAndRetry(
            ClawgicMatch match,
            ClawgicJudgeQueueMessage queueMessage,
            int attempt,
            OffsetDateTime now,
            JsonNode rawJudgeResult,
            ClawgicMatchJudgementStatus judgementStatus,
            String errorCode,
            Throwable failure
    ) {
        ClawgicMatchJudgement failedJudgement = initializeJudgement(
                match,
                queueMessage.judgeKey(),
                resolveJudgeModel(),
                attempt,
                now
        );
        failedJudgement.setStatus(judgementStatus);
        failedJudgement.setJudgedAt(now);
        failedJudgement.setResultJson(buildFailureResultJson(rawJudgeResult, errorCode, failure));
        failedJudgement.setReasoning(errorCode + ": " + truncateMessage(failure.getMessage()));
        clawgicMatchJudgementRepository.save(failedJudgement);

        int nextRetryCount = resolveRetryCount(match) + 1;
        match.setJudgeRetryCount(nextRetryCount);
        match.setUpdatedAt(now);
        clawgicMatchRepository.save(match);

        if (nextRetryCount <= resolveMaxRetries()) {
            clawgicJudgeQueue.enqueue(queueMessage);
            return;
        }

        log.warn(
                "Judge retries exhausted for match {} and judge key {} after {} failures",
                match.getMatchId(),
                queueMessage.judgeKey(),
                nextRetryCount
        );
    }

    private void applyMvpMergePolicy(ClawgicMatch match, OffsetDateTime now) {
        List<ClawgicMatchJudgement> acceptedJudgements =
                clawgicMatchJudgementRepository.findByMatchIdAndStatusOrderByCreatedAtAsc(
                        match.getMatchId(),
                        ClawgicMatchJudgementStatus.ACCEPTED
                );
        if (acceptedJudgements.isEmpty()) {
            throw new IllegalStateException("Expected at least one accepted judge verdict for match " + match.getMatchId());
        }
        ClawgicMatchJudgement selectedVerdict = acceptedJudgements.getFirst();
        if (selectedVerdict.getWinnerAgentId() == null) {
            throw new IllegalStateException(
                    "Accepted judge verdict is missing winner for match " + match.getMatchId()
            );
        }

        clawgicAgentEloService.applyJudgedMatchResult(
                match.getMatchId(),
                match.getAgent1Id(),
                match.getAgent2Id(),
                selectedVerdict.getWinnerAgentId()
        );
        match.setWinnerAgentId(selectedVerdict.getWinnerAgentId());
        match.setJudgeResultJson(selectedVerdict.getResultJson());
        match.setStatus(ClawgicMatchStatus.COMPLETED);
        match.setJudgedAt(now);
        match.setCompletedAt(now);
        match.setUpdatedAt(now);
    }

    private ClawgicMatchJudgement initializeJudgement(
            ClawgicMatch match,
            String judgeKey,
            String judgeModel,
            int attempt,
            OffsetDateTime now
    ) {
        ClawgicMatchJudgement judgement = new ClawgicMatchJudgement();
        judgement.setJudgementId(UUID.randomUUID());
        judgement.setMatchId(match.getMatchId());
        judgement.setTournamentId(match.getTournamentId());
        judgement.setJudgeKey(judgeKey);
        judgement.setJudgeModel(judgeModel);
        judgement.setAttempt(attempt);
        judgement.setResultJson(JsonNodeFactory.instance.objectNode());
        judgement.setCreatedAt(now);
        judgement.setUpdatedAt(now);
        return judgement;
    }

    private ObjectNode buildFailureResultJson(JsonNode rawJudgeResult, String errorCode, Throwable failure) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("error_code", errorCode);
        payload.put("error_message", truncateMessage(failure.getMessage()));
        if (rawJudgeResult != null) {
            payload.set("raw_result", rawJudgeResult.deepCopy());
        }
        return payload;
    }

    private String resolveJudgeModel() {
        String model = clawgicJudgeProperties.getModel();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("clawgic.judge.model must not be blank");
        }
        return model.trim();
    }

    private int resolveMaxRetries() {
        return Math.max(0, clawgicJudgeProperties.getMaxRetries());
    }

    private static int resolveRetryCount(ClawgicMatch match) {
        Integer currentRetryCount = match.getJudgeRetryCount();
        return currentRetryCount == null ? 0 : Math.max(0, currentRetryCount);
    }

    private static String truncateMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String normalized = message.trim();
        if (normalized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
