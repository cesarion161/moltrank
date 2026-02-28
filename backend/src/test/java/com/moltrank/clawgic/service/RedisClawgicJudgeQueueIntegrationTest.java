package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisClawgicJudgeQueueIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory lettuceConnectionFactory;
    private StringRedisTemplate stringRedisTemplate;
    private InMemoryClawgicJudgeQueue fallbackQueue;
    private RedisClawgicJudgeQueue redisQueue;
    private String queueKey;

    @BeforeEach
    void setUp() {
        queueKey = "clawgic:test:judge:queue:" + UUID.randomUUID();
        lettuceConnectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379))
        );
        lettuceConnectionFactory.afterPropertiesSet();
        lettuceConnectionFactory.start();

        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        ClawgicRuntimeProperties runtimeProperties = new ClawgicRuntimeProperties();
        runtimeProperties.getWorker().setRedisQueueKey(queueKey);
        runtimeProperties.getWorker().setRedisPopTimeoutSeconds(1);

        fallbackQueue = new InMemoryClawgicJudgeQueue();
        fallbackQueue.startDispatcher();

        redisQueue = new RedisClawgicJudgeQueue(
                stringRedisTemplate,
                runtimeProperties,
                fallbackQueue
        );
        redisQueue.startDispatcher();
    }

    @AfterEach
    void tearDown() {
        if (redisQueue != null) {
            redisQueue.stopDispatcher();
        }
        if (fallbackQueue != null) {
            fallbackQueue.stopDispatcher();
        }
        if (stringRedisTemplate != null && queueKey != null) {
            stringRedisTemplate.delete(queueKey);
        }
        if (lettuceConnectionFactory != null) {
            lettuceConnectionFactory.destroy();
        }
    }

    @Test
    void enqueueAndConsumeRoundTripsThroughRedis() throws InterruptedException {
        CountDownLatch consumedLatch = new CountDownLatch(1);
        AtomicReference<ClawgicJudgeQueueMessage> consumedMessage = new AtomicReference<>();
        redisQueue.setConsumer(message -> {
            consumedMessage.set(message);
            consumedLatch.countDown();
        });

        ClawgicJudgeQueueMessage expected = new ClawgicJudgeQueueMessage(UUID.randomUUID(), "mock-judge-primary");
        redisQueue.enqueue(expected);

        assertTrue(consumedLatch.await(5, TimeUnit.SECONDS), "Timed out waiting for Redis queue consumer callback");
        assertEquals(expected, consumedMessage.get());
        assertEquals(0L, stringRedisTemplate.opsForList().size(queueKey));
    }
}
