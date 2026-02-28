package com.clawgic.clawgic.controller;

import com.clawgic.clawgic.dto.ClawgicAgentResponses;
import com.clawgic.clawgic.service.ClawgicAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClawgicAgentController.class)
class ClawgicAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClawgicAgentService clawgicAgentService;

    @Test
    void createAgentReturnsCreatedPayload() throws Exception {
        UUID agentId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        when(clawgicAgentService.createAgent(any())).thenReturn(sampleDetail(agentId, 1000));

        mockMvc.perform(post("/api/clawgic/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "walletAddress": "0x1234567890abcdef1234567890abcdef12345678",
                                  "name": "Precision Debater",
                                  "avatarUrl": "https://example.com/avatar.png",
                                  "systemPrompt": "Argue with concise evidence.",
                                  "skillsMarkdown": "- rebuttal\\n- synthesis",
                                  "persona": "Analytical",
                                  "agentsMdSource": "# AGENTS.md",
                                  "providerType": "OPENAI",
                                  "providerKeyRef": "team/openai/primary",
                                  "apiKey": "sk-live-c16-create"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentId").value(agentId.toString()))
                .andExpect(jsonPath("$.providerType").value("OPENAI"))
                .andExpect(jsonPath("$.providerKeyRef").value("team/openai/primary"))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.elo.currentElo").value(1000));
    }

    @Test
    void createAgentValidationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/clawgic/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "walletAddress": "bad-wallet",
                                  "name": "",
                                  "systemPrompt": "",
                                  "providerType": null,
                                  "apiKey": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(clawgicAgentService, never()).createAgent(any());
    }

    @Test
    void getAgentReturnsDetailPayload() throws Exception {
        UUID agentId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        when(clawgicAgentService.getAgent(eq(agentId))).thenReturn(sampleDetail(agentId, 1032));

        mockMvc.perform(get("/api/clawgic/agents/{agentId}", agentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(agentId.toString()))
                .andExpect(jsonPath("$.name").value("Precision Debater"))
                .andExpect(jsonPath("$.elo.currentElo").value(1032));
    }

    @Test
    void listAgentsReturnsSummaryCollection() throws Exception {
        UUID firstAgentId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID secondAgentId = UUID.fromString("00000000-0000-0000-0000-000000000302");

        when(clawgicAgentService.listAgents(eq("0x1234567890abcdef1234567890abcdef12345678")))
                .thenReturn(List.of(
                        sampleSummary(firstAgentId, "First Agent"),
                        sampleSummary(secondAgentId, "Second Agent")
                ));

        mockMvc.perform(get("/api/clawgic/agents")
                        .param("walletAddress", "0x1234567890abcdef1234567890abcdef12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].agentId").value(firstAgentId.toString()))
                .andExpect(jsonPath("$[0].providerType").value("OPENAI"))
                .andExpect(jsonPath("$[1].agentId").value(secondAgentId.toString()))
                .andExpect(jsonPath("$[1].providerType").value("OPENAI"));
    }

    @Test
    void getLeaderboardReturnsRankedPaginatedPayload() throws Exception {
        UUID firstAgentId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID secondAgentId = UUID.fromString("00000000-0000-0000-0000-000000000402");
        when(clawgicAgentService.getLeaderboard(eq(5), eq(2)))
                .thenReturn(sampleLeaderboardPage(firstAgentId, secondAgentId));

        mockMvc.perform(get("/api/clawgic/agents/leaderboard")
                        .param("offset", "5")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(5))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].rank").value(6))
                .andExpect(jsonPath("$.entries[0].agentId").value(firstAgentId.toString()))
                .andExpect(jsonPath("$.entries[0].currentElo").value(1240))
                .andExpect(jsonPath("$.entries[1].rank").value(7))
                .andExpect(jsonPath("$.entries[1].agentId").value(secondAgentId.toString()));
    }

    private static ClawgicAgentResponses.AgentDetail sampleDetail(UUID agentId, int elo) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-02-27T12:00:00Z");
        return new ClawgicAgentResponses.AgentDetail(
                agentId,
                "0x1234567890abcdef1234567890abcdef12345678",
                "Precision Debater",
                "https://example.com/avatar.png",
                "Argue with concise evidence.",
                "- rebuttal",
                "Analytical",
                "# AGENTS.md",
                "OPENAI",
                "team/openai/primary",
                true,
                new ClawgicAgentResponses.AgentElo(agentId, elo, 0, 0, 0, timestamp),
                timestamp,
                timestamp
        );
    }

    private static ClawgicAgentResponses.AgentSummary sampleSummary(UUID agentId, String name) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-02-27T12:00:00Z");
        return new ClawgicAgentResponses.AgentSummary(
                agentId,
                "0x1234567890abcdef1234567890abcdef12345678",
                name,
                null,
                "OPENAI",
                "team/openai/primary",
                "Analytical",
                timestamp,
                timestamp
        );
    }

    private static ClawgicAgentResponses.AgentLeaderboardPage sampleLeaderboardPage(
            UUID firstAgentId,
            UUID secondAgentId
    ) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-02-28T12:00:00Z");
        return new ClawgicAgentResponses.AgentLeaderboardPage(
                List.of(
                        new ClawgicAgentResponses.AgentLeaderboardEntry(
                                6,
                                null,
                                null,
                                firstAgentId,
                                "0x1234567890abcdef1234567890abcdef12345678",
                                "First Ranker",
                                null,
                                1240,
                                22,
                                14,
                                1,
                                timestamp
                        ),
                        new ClawgicAgentResponses.AgentLeaderboardEntry(
                                7,
                                null,
                                null,
                                secondAgentId,
                                "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
                                "Second Ranker",
                                null,
                                1230,
                                24,
                                13,
                                2,
                                timestamp
                        )
                ),
                5,
                2,
                12,
                true
        );
    }
}
