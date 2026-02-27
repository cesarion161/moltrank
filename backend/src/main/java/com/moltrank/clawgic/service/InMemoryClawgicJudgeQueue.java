package com.moltrank.clawgic.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@ConditionalOnProperty(
        prefix = "clawgic.worker",
        name = "queue-mode",
        havingValue = "in_memory",
        matchIfMissing = true
)
public class InMemoryClawgicJudgeQueue implements ClawgicJudgeQueue {

    private static final Logger log = LoggerFactory.getLogger(InMemoryClawgicJudgeQueue.class);

    private final BlockingQueue<ClawgicJudgeQueueMessage> queue = new LinkedBlockingQueue<>();
    private final Object consumerMonitor = new Object();

    private volatile boolean running = true;
    private volatile ClawgicJudgeQueueConsumer consumer;
    private Thread dispatcherThread;

    @PostConstruct
    void startDispatcher() {
        dispatcherThread = Thread.ofVirtual()
                .name("clawgic-judge-queue-dispatcher")
                .start(this::dispatchLoop);
    }

    @PreDestroy
    void stopDispatcher() {
        running = false;
        synchronized (consumerMonitor) {
            consumerMonitor.notifyAll();
        }
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
    }

    @Override
    public void enqueue(ClawgicJudgeQueueMessage message) {
        if (!running) {
            throw new IllegalStateException("Judge queue is not running");
        }
        queue.offer(Objects.requireNonNull(message, "message is required"));
    }

    @Override
    public void setConsumer(ClawgicJudgeQueueConsumer consumer) {
        synchronized (consumerMonitor) {
            this.consumer = Objects.requireNonNull(consumer, "consumer is required");
            consumerMonitor.notifyAll();
        }
    }

    private void dispatchLoop() {
        while (running) {
            try {
                ClawgicJudgeQueueMessage message = queue.take();
                ClawgicJudgeQueueConsumer queueConsumer = awaitConsumer();
                if (queueConsumer == null) {
                    return;
                }
                queueConsumer.accept(message);
            } catch (InterruptedException ex) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (RuntimeException ex) {
                log.error("Judge queue consumer failed while dispatching queued message", ex);
            }
        }
    }

    private ClawgicJudgeQueueConsumer awaitConsumer() throws InterruptedException {
        synchronized (consumerMonitor) {
            while (running && consumer == null) {
                consumerMonitor.wait();
            }
            return consumer;
        }
    }
}
