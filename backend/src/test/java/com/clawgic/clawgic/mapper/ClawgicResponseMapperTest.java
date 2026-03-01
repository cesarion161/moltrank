package com.clawgic.clawgic.mapper;

import com.clawgic.clawgic.dto.ClawgicAgentResponses;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicProviderType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClawgicResponseMapperTest {

    private final ClawgicResponseMapper mapper = new ClawgicResponseMapper();

    @Test
    void agentSummaryAndDetailMappingAvoidPlaintextApiKeyFields() {
        UUID agentId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-02-26T10:15:30Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-02-26T10:20:30Z");

        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(agentId);
        agent.setWalletAddress("0xabc123");
        agent.setName("Mapper Test Agent");
        agent.setAvatarUrl("https://example.com/avatar.png");
        agent.setSystemPrompt("Debate with precise logic.");
        agent.setSkillsMarkdown("- rebuttal\n- synthesis");
        agent.setPersona("Analytical and concise");
        agent.setAgentsMdSource("# AGENTS.md\n...");
        agent.setProviderType(ClawgicProviderType.OPENAI);
        agent.setProviderKeyRef("team/openai/primary");
        agent.setApiKeyEncrypted("enc:secret");
        agent.setApiKeyEncryptionKeyId("kms-key-1");
        agent.setCreatedAt(createdAt);
        agent.setUpdatedAt(updatedAt);

        ClawgicAgentElo elo = new ClawgicAgentElo();
        elo.setAgentId(agentId);
        elo.setCurrentElo(1088);
        elo.setMatchesPlayed(4);
        elo.setMatchesWon(3);
        elo.setMatchesForfeited(1);
        elo.setLastUpdated(updatedAt);

        ClawgicAgentResponses.AgentSummary summary = mapper.toAgentSummaryResponse(agent);
        ClawgicAgentResponses.AgentDetail detail = mapper.toAgentDetailResponse(agent, elo);

        assertEquals(agentId, summary.agentId());
        assertEquals("Mapper Test Agent", summary.name());
        assertEquals("0xabc123", summary.walletAddress());
        assertEquals("OPENAI", summary.providerType());
        assertEquals("team/openai/primary", summary.providerKeyRef());
        assertEquals(createdAt, summary.createdAt());
        assertTrue(summary.apiKeyConfigured());

        assertEquals(agentId, detail.agentId());
        assertEquals("Debate with precise logic.", detail.systemPrompt());
        assertEquals("OPENAI", detail.providerType());
        assertEquals("team/openai/primary", detail.providerKeyRef());
        assertTrue(detail.apiKeyConfigured());
        assertNotNull(detail.elo());
        assertEquals(1088, detail.elo().currentElo());
        assertEquals(3, detail.elo().matchesWon());
        assertFalse(summary.toString().contains("enc:secret"));
        assertFalse(detail.toString().contains("enc:secret"));
        assertFalse(detail.toString().contains("kms-key-1"));

        assertFalse(hasRecordComponent(detail.getClass(), "apiKeyEncrypted"));
        assertFalse(hasRecordComponent(detail.getClass(), "apiKeyEncryptionKeyId"));
        assertTrue(hasRecordComponent(detail.getClass(), "apiKeyConfigured"));
    }

    private static boolean hasRecordComponent(Class<?> recordType, String componentName) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            if (componentName.equals(component.getName())) {
                return true;
            }
        }
        return false;
    }
}
