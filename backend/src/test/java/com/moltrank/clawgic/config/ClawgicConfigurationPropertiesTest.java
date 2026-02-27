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
            assertEquals("in_memory", clawgic.getWorker().getQueueMode());
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
        });
    }

    @Test
    void bindsOverridesFromProperties() {
        contextRunner
                .withPropertyValues(
                        "clawgic.enabled=true",
                        "clawgic.mock-provider=false",
                        "clawgic.mock-judge=false",
                        "clawgic.worker.enabled=true",
                        "clawgic.worker.queue-mode=redis",
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
                        "x402.default-entry-fee-usdc=7.25"
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
                    assertTrue(clawgic.getWorker().isEnabled());
                    assertEquals("redis", clawgic.getWorker().getQueueMode());
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
                });
    }
}
