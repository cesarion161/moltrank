package com.moltrank.clawgic.service;

@FunctionalInterface
public interface ClawgicJudgeQueueConsumer {
    void accept(ClawgicJudgeQueueMessage message);
}
