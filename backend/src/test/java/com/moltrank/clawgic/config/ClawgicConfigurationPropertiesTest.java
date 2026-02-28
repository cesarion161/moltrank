package com.moltrank.clawgic.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClawgicConfigurationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(
                    ClawgicRuntimeProperties.class,
                    ClawgicProviderProperties.class,
                    ClawgicJudgeProperties.class,
                    ClawgicAgentKeyEncryptionProperties.class,
                    X402Properties.class
            );

    @Test
    void contextStartsWithClawgicAndX402PropertyBeans() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("clawgicRuntimeProperties"));
            assertTrue(context.containsBean("clawgicProviderProperties"));
            assertTrue(context.containsBean("clawgicJudgeProperties"));
            assertTrue(context.containsBean("clawgicAgentKeyEncryptionProperties"));
            assertTrue(context.containsBean("x402Properties"));
        });
    }

    @Test
    void bindsDefaultValues() {
        contextRunner.run(context -> {
            ClawgicRuntimeProperties clawgic = context.getBean(ClawgicRuntimeProperties.class);
            ClawgicProviderProperties provider = context.getBean(ClawgicProviderProperties.class);
            ClawgicJudgeProperties judge = context.getBean(ClawgicJudgeProperties.class);
            ClawgicAgentKeyEncryptionProperties apiKeyEncryption =
                    context.getBean(ClawgicAgentKeyEncryptionProperties.class);
            X402Properties x402 = context.getBean(X402Properties.class);

            assertFalse(clawgic.isEnabled());
            assertTrue(clawgic.isMockProvider());
            assertTrue(clawgic.isMockJudge());
            assertEquals(4, clawgic.getTournament().getMvpBracketSize());
            assertEquals(new BigDecimal("0.250000"), clawgic.getTournament().getJudgeFeeUsdcPerCompletedMatch());
            assertEquals(new BigDecimal("0.000000"), clawgic.getTournament().getSystemRetentionRate());
            assertEquals("redis", clawgic.getWorker().getQueueMode());
            assertEquals("clawgic:judge:queue", clawgic.getWorker().getRedisQueueKey());
            assertEquals(1L, clawgic.getWorker().getRedisPopTimeoutSeconds());
            assertEquals(3, clawgic.getDebate().getMaxExchangesPerAgent());
            assertEquals(180, clawgic.getDebate().getMaxResponseWords());
            assertEquals(512, clawgic.getDebate().getMaxResponseTokens());

            assertEquals("gpt-4o-mini", provider.getOpenaiDefaultModel());
            assertEquals("claude-3-5-sonnet-latest", provider.getAnthropicDefaultModel());
            assertEquals("clawgic-mock-v1", provider.getMockModel());
            assertTrue(provider.getKeyRefModels().isEmpty());

            assertTrue(judge.isEnabled());
            assertEquals("gpt-4o", judge.getModel());
            assertTrue(judge.isStrictJson());
            assertEquals(2, judge.getMaxRetries());
            assertEquals(List.of("mock-judge-primary"), judge.getKeys());

            assertEquals("local-dev-v1", apiKeyEncryption.getActiveKeyId());
            assertEquals(4096, apiKeyEncryption.getMaxPlaintextLength());
            assertTrue(apiKeyEncryption.getKeys().containsKey("local-dev-v1"));
            assertEquals(
                    ClawgicAgentKeyEncryptionProperties.DEFAULT_LOCAL_DEV_KEY_BASE64,
                    apiKeyEncryption.getKeys().get("local-dev-v1")
            );

            assertFalse(x402.isEnabled());
            assertTrue(x402.isDevBypassEnabled());
            assertEquals("base-sepolia", x402.getNetwork());
            assertEquals(84532L, x402.getChainId());
            assertEquals(new BigDecimal("5.00"), x402.getDefaultEntryFeeUsdc());
            assertEquals("X-PAYMENT", x402.getPaymentHeaderName());
            assertEquals("USD Coin", x402.getEip3009DomainName());
            assertEquals("2", x402.getEip3009DomainVersion());
            assertEquals(6, x402.getTokenDecimals());
        });
    }

    @Test
    void bindsOverridesFromProperties() {
        contextRunner
                .withPropertyValues(
                        "clawgic.enabled=true",
                        "clawgic.mock-provider=false",
                        "clawgic.mock-judge=false",
                        "clawgic.tournament.judge-fee-usdc-per-completed-match=0.375000",
                        "clawgic.tournament.system-retention-rate=0.125000",
                        "clawgic.worker.enabled=true",
                        "clawgic.worker.queue-mode=redis",
                        "clawgic.worker.redis-queue-key=clawgic:prod:judge:queue",
                        "clawgic.worker.redis-pop-timeout-seconds=4",
                        "clawgic.debate.max-exchanges-per-agent=4",
                        "clawgic.debate.max-response-words=220",
                        "clawgic.debate.provider-timeout-seconds=20",
                        "clawgic.provider.openai-default-model=gpt-4.1-mini",
                        "clawgic.provider.anthropic-default-model=claude-3-7-sonnet-latest",
                        "clawgic.provider.mock-model=clawgic-mock-v2",
                        "clawgic.provider.key-ref-models[team/openai/primary]=gpt-4.1",
                        "clawgic.judge.model=gpt-4.1",
                        "clawgic.judge.max-retries=4",
                        "clawgic.judge.keys[0]=mock-judge-primary",
                        "clawgic.judge.keys[1]=mock-judge-secondary",
                        "clawgic.agent-key-encryption.active-key-id=rotate-v2",
                        "clawgic.agent-key-encryption.keys.rotate-v2=ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=",
                        "clawgic.agent-key-encryption.max-plaintext-length=2048",
                        "x402.enabled=true",
                        "x402.dev-bypass-enabled=false",
                        "x402.network=base-mainnet",
                        "x402.chain-id=8453",
                        "x402.default-entry-fee-usdc=7.25",
                        "x402.eip3009-domain-name=USDC Test",
                        "x402.eip3009-domain-version=3",
                        "x402.token-decimals=8"
                )
                .run(context -> {
                    ClawgicRuntimeProperties clawgic = context.getBean(ClawgicRuntimeProperties.class);
                    ClawgicProviderProperties provider = context.getBean(ClawgicProviderProperties.class);
                    ClawgicJudgeProperties judge = context.getBean(ClawgicJudgeProperties.class);
                    ClawgicAgentKeyEncryptionProperties apiKeyEncryption =
                            context.getBean(ClawgicAgentKeyEncryptionProperties.class);
                    X402Properties x402 = context.getBean(X402Properties.class);

                    assertTrue(clawgic.isEnabled());
                    assertFalse(clawgic.isMockProvider());
                    assertFalse(clawgic.isMockJudge());
                    assertEquals(new BigDecimal("0.375000"), clawgic.getTournament().getJudgeFeeUsdcPerCompletedMatch());
                    assertEquals(new BigDecimal("0.125000"), clawgic.getTournament().getSystemRetentionRate());
                    assertTrue(clawgic.getWorker().isEnabled());
                    assertEquals("redis", clawgic.getWorker().getQueueMode());
                    assertEquals("clawgic:prod:judge:queue", clawgic.getWorker().getRedisQueueKey());
                    assertEquals(4L, clawgic.getWorker().getRedisPopTimeoutSeconds());
                    assertEquals(4, clawgic.getDebate().getMaxExchangesPerAgent());
                    assertEquals(220, clawgic.getDebate().getMaxResponseWords());
                    assertEquals(20, clawgic.getDebate().getProviderTimeoutSeconds());

                    assertEquals("gpt-4.1-mini", provider.getOpenaiDefaultModel());
                    assertEquals("claude-3-7-sonnet-latest", provider.getAnthropicDefaultModel());
                    assertEquals("clawgic-mock-v2", provider.getMockModel());
                    assertEquals("gpt-4.1", provider.getKeyRefModels().get("team/openai/primary"));

                    assertEquals("gpt-4.1", judge.getModel());
                    assertEquals(4, judge.getMaxRetries());
                    assertEquals(List.of("mock-judge-primary", "mock-judge-secondary"), judge.getKeys());

                    assertEquals("rotate-v2", apiKeyEncryption.getActiveKeyId());
                    assertEquals(2048, apiKeyEncryption.getMaxPlaintextLength());
                    assertEquals(
                            "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=",
                            apiKeyEncryption.getKeys().get("rotate-v2")
                    );

                    assertTrue(x402.isEnabled());
                    assertFalse(x402.isDevBypassEnabled());
                    assertEquals("base-mainnet", x402.getNetwork());
                    assertEquals(8453L, x402.getChainId());
                    assertEquals(new BigDecimal("7.25"), x402.getDefaultEntryFeeUsdc());
                    assertEquals("USDC Test", x402.getEip3009DomainName());
                    assertEquals("3", x402.getEip3009DomainVersion());
                    assertEquals(8, x402.getTokenDecimals());
                });
    }
}
