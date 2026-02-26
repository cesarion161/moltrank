package com.moltrank.clawgic.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClawgicConfigurationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(
                    ClawgicRuntimeProperties.class,
                    ClawgicJudgeProperties.class,
                    X402Properties.class
            );

    @Test
    void contextStartsWithClawgicAndX402PropertyBeans() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("clawgicRuntimeProperties"));
            assertTrue(context.containsBean("clawgicJudgeProperties"));
            assertTrue(context.containsBean("x402Properties"));
        });
    }

    @Test
    void bindsDefaultValues() {
        contextRunner.run(context -> {
            ClawgicRuntimeProperties clawgic = context.getBean(ClawgicRuntimeProperties.class);
            ClawgicJudgeProperties judge = context.getBean(ClawgicJudgeProperties.class);
            X402Properties x402 = context.getBean(X402Properties.class);

            assertFalse(clawgic.isEnabled());
            assertTrue(clawgic.isMockProvider());
            assertTrue(clawgic.isMockJudge());
            assertEquals(4, clawgic.getTournament().getMvpBracketSize());
            assertEquals("in_memory", clawgic.getWorker().getQueueMode());
            assertEquals(3, clawgic.getDebate().getMaxExchangesPerAgent());

            assertTrue(judge.isEnabled());
            assertEquals("gpt-4o", judge.getModel());
            assertTrue(judge.isStrictJson());
            assertEquals(2, judge.getMaxRetries());

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
                        "clawgic.debate.provider-timeout-seconds=20",
                        "clawgic.judge.model=gpt-4.1",
                        "clawgic.judge.max-retries=4",
                        "x402.enabled=true",
                        "x402.dev-bypass-enabled=false",
                        "x402.network=base-mainnet",
                        "x402.chain-id=8453",
                        "x402.default-entry-fee-usdc=7.25"
                )
                .run(context -> {
                    ClawgicRuntimeProperties clawgic = context.getBean(ClawgicRuntimeProperties.class);
                    ClawgicJudgeProperties judge = context.getBean(ClawgicJudgeProperties.class);
                    X402Properties x402 = context.getBean(X402Properties.class);

                    assertTrue(clawgic.isEnabled());
                    assertFalse(clawgic.isMockProvider());
                    assertFalse(clawgic.isMockJudge());
                    assertTrue(clawgic.getWorker().isEnabled());
                    assertEquals("redis", clawgic.getWorker().getQueueMode());
                    assertEquals(20, clawgic.getDebate().getProviderTimeoutSeconds());

                    assertEquals("gpt-4.1", judge.getModel());
                    assertEquals(4, judge.getMaxRetries());

                    assertTrue(x402.isEnabled());
                    assertFalse(x402.isDevBypassEnabled());
                    assertEquals("base-mainnet", x402.getNetwork());
                    assertEquals(8453L, x402.getChainId());
                    assertEquals(new BigDecimal("7.25"), x402.getDefaultEntryFeeUsdc());
                });
    }
}
