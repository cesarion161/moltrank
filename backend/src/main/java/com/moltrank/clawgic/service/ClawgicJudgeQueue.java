package com.moltrank.clawgic.service;

public interface ClawgicJudgeQueue {

    void enqueue(ClawgicJudgeQueueMessage message);

    void setConsumer(ClawgicJudgeQueueConsumer consumer);
}
