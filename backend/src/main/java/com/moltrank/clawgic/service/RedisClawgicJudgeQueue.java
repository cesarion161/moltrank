package com.moltrank.clawgic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Primary
@ConditionalOnProperty(
        prefix = "clawgic.worker",
        name = "queue-mode",
        havingValue = "redis"
)
public class RedisClawgicJudgeQueue implements ClawgicJudgeQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisClawgicJudgeQueue.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final Duration REDIS_FAILURE_BACKOFF = Duration.ofSeconds(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ClawgicRuntimeProperties clawgicRuntimeProperties;
    private final InMemoryClawgicJudgeQueue fallbackQueue;
    private final Object consumerMonitor = new Object();

    private volatile boolean running = true;
    private volatile ClawgicJudgeQueueConsumer consumer;
    private volatile boolean fallbackMode;
    private volatile long redisRetryNotBeforeNanos;
    private Thread dispatcherThread;

    @PostConstruct
    void startDispatcher() {
        dispatcherThread = Thread.ofVirtual()
                .name("clawgic-judge-redis-queue-dispatcher")
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
        ClawgicJudgeQueueMessage requiredMessage = Objects.requireNonNull(message, "message is required");
        if (!shouldAttemptRedis()) {
            fallbackQueue.enqueue(requiredMessage);
            return;
        }
        String queueKey = resolveRedisQueueKey();
        String payload = serialize(requiredMessage);
        try {
            Long queueDepth = stringRedisTemplate.opsForList().rightPush(
                    queueKey,
                    payload
            );
            if (queueDepth != null) {
                markRedisHealthy();
                return;
            }
            log.warn("Redis queue push returned null, routing message to in-memory fallback queue");
            markRedisFailure(null);
        } catch (RuntimeException ex) {
            markRedisFailure(ex);
        }
        fallbackQueue.enqueue(requiredMessage);
    }

    @Override
    public void setConsumer(ClawgicJudgeQueueConsumer consumer) {
        ClawgicJudgeQueueConsumer requiredConsumer = Objects.requireNonNull(consumer, "consumer is required");
        synchronized (consumerMonitor) {
            this.consumer = requiredConsumer;
            consumerMonitor.notifyAll();
        }
        fallbackQueue.setConsumer(requiredConsumer);
    }

    boolean pollOnce() throws InterruptedException {
        if (!shouldAttemptRedis()) {
            TimeUnit.MILLISECONDS.sleep(200L);
            return false;
        }

        String payload = stringRedisTemplate.opsForList().leftPop(
                resolveRedisQueueKey(),
                resolveRedisPopTimeoutSeconds(),
                TimeUnit.SECONDS
        );
        if (payload == null) {
            return false;
        }
        ClawgicJudgeQueueConsumer queueConsumer = awaitConsumer();
        if (queueConsumer == null) {
            return false;
        }
        queueConsumer.accept(deserialize(payload));
        markRedisHealthy();
        return true;
    }

    private void dispatchLoop() {
        while (running) {
            try {
                pollOnce();
            } catch (InterruptedException ex) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (RuntimeException ex) {
                markRedisFailure(ex);
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

    private String resolveRedisQueueKey() {
        String queueKey = clawgicRuntimeProperties.getWorker().getRedisQueueKey();
        if (queueKey == null || queueKey.isBlank()) {
            throw new IllegalStateException("clawgic.worker.redis-queue-key must not be blank");
        }
        return queueKey.trim();
    }

    private long resolveRedisPopTimeoutSeconds() {
        long timeoutSeconds = clawgicRuntimeProperties.getWorker().getRedisPopTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("clawgic.worker.redis-pop-timeout-seconds must be greater than zero");
        }
        return timeoutSeconds;
    }

    private String serialize(ClawgicJudgeQueueMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize judge queue message payload", ex);
        }
    }

    private ClawgicJudgeQueueMessage deserialize(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, ClawgicJudgeQueueMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize judge queue payload", ex);
        }
    }

    private boolean shouldAttemptRedis() {
        return System.nanoTime() >= redisRetryNotBeforeNanos;
    }

    private void markRedisFailure(RuntimeException ex) {
        redisRetryNotBeforeNanos = System.nanoTime() + REDIS_FAILURE_BACKOFF.toNanos();
        if (!fallbackMode) {
            fallbackMode = true;
            if (ex == null) {
                log.warn("Redis queue is unavailable; switching to in-memory fallback mode");
            } else {
                log.warn(
                        "Redis queue is unavailable ({}); switching to in-memory fallback mode",
                        resolveSafeMessage(ex)
                );
            }
        }
    }

    private void markRedisHealthy() {
        if (fallbackMode) {
            log.info("Redis queue connection restored; leaving in-memory fallback mode");
        }
        fallbackMode = false;
        redisRetryNotBeforeNanos = 0L;
    }

    private String resolveSafeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
