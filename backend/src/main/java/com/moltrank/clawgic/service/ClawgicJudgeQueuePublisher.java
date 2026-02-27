package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicJudgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClawgicJudgeQueuePublisher {

    private final ClawgicJudgeQueue clawgicJudgeQueue;
    private final ClawgicJudgeProperties clawgicJudgeProperties;

    public void publishMatchReady(UUID matchId) {
        UUID requiredMatchId = Objects.requireNonNull(matchId, "matchId is required");
        List<ClawgicJudgeQueueMessage> queueMessages = resolveQueueMessages(requiredMatchId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishImmediately(queueMessages);
                }
            });
            return;
        }
        publishImmediately(queueMessages);
    }

    private void publishImmediately(List<ClawgicJudgeQueueMessage> queueMessages) {
        for (ClawgicJudgeQueueMessage message : queueMessages) {
            clawgicJudgeQueue.enqueue(message);
        }
    }

    private List<ClawgicJudgeQueueMessage> resolveQueueMessages(UUID matchId) {
        List<String> configuredKeys = clawgicJudgeProperties.getKeys();
        if (configuredKeys == null) {
            throw new IllegalStateException("At least one clawgic.judge.keys entry is required");
        }

        List<ClawgicJudgeQueueMessage> queueMessages = configuredKeys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(key -> !key.isBlank())
                .distinct()
                .map(key -> new ClawgicJudgeQueueMessage(matchId, key))
                .toList();

        if (queueMessages.isEmpty()) {
            throw new IllegalStateException("At least one clawgic.judge.keys entry is required");
        }
        return queueMessages;
    }
}
