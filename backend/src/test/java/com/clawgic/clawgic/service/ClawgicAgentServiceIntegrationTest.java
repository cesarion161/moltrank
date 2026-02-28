package com.clawgic.clawgic.service;

import com.clawgic.clawgic.dto.ClawgicAgentRequests;
import com.clawgic.clawgic.dto.ClawgicAgentResponses;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicProviderType;
import com.clawgic.clawgic.model.ClawgicUser;
import com.clawgic.clawgic.repository.ClawgicAgentEloRepository;
import com.clawgic.clawgic.repository.ClawgicAgentRepository;
import com.clawgic.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/clawgic}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:clawgic}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
})
@Transactional
class ClawgicAgentServiceIntegrationTest {

    @Autowired
    private ClawgicAgentService clawgicAgentService;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicAgentEloRepository clawgicAgentEloRepository;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Test
    void createAgentPersistsEncryptedRecordAndInitialEloRow() {
        ClawgicAgentResponses.AgentDetail created = clawgicAgentService.createAgent(new ClawgicAgentRequests.CreateAgentRequest(
                "0x1234567890ABCDEF1234567890ABCDEF12345678",
                "C16 Integration Agent",
                "https://example.com/c16-avatar.png",
                "Debate with reproducible structure.",
                "- rebuttal",
                "Analytical",
                "# AGENTS.md\nC16",
                ClawgicProviderType.OPENAI,
                "team/openai/primary",
                "sk-live-c16-integration"
        ));

        assertNotNull(created.agentId());
        assertEquals("OPENAI", created.providerType());
        assertEquals("team/openai/primary", created.providerKeyRef());
        assertTrue(created.apiKeyConfigured());
        assertNotNull(created.elo());
        assertEquals(1000, created.elo().currentElo());

        ClawgicAgent persistedAgent = clawgicAgentRepository.findById(created.agentId()).orElseThrow();
        assertEquals("0x1234567890abcdef1234567890abcdef12345678", persistedAgent.getWalletAddress());
        assertEquals(ClawgicProviderType.OPENAI, persistedAgent.getProviderType());
        assertEquals("team/openai/primary", persistedAgent.getProviderKeyRef());
        assertFalse(persistedAgent.getApiKeyEncrypted().contains("sk-live-c16-integration"));

        ClawgicAgentElo persistedElo = clawgicAgentEloRepository.findById(created.agentId()).orElseThrow();
        assertEquals(1000, persistedElo.getCurrentElo());
        assertEquals(0, persistedElo.getMatchesPlayed());
        assertEquals(0, persistedElo.getMatchesWon());
        assertEquals(0, persistedElo.getMatchesForfeited());

        assertTrue(clawgicUserRepository.existsById("0x1234567890abcdef1234567890abcdef12345678"));
    }

    @Test
    void listAgentsFiltersByWalletAddress() {
        clawgicAgentService.createAgent(new ClawgicAgentRequests.CreateAgentRequest(
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "Wallet A Agent",
                null,
                "Prompt A",
                null,
                null,
                null,
                ClawgicProviderType.OPENAI,
                null,
                "sk-live-wallet-a"
        ));
        clawgicAgentService.createAgent(new ClawgicAgentRequests.CreateAgentRequest(
                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "Wallet B Agent",
                null,
                "Prompt B",
                null,
                null,
                null,
                ClawgicProviderType.MOCK,
                null,
                "sk-live-wallet-b"
        ));

        List<ClawgicAgentResponses.AgentSummary> walletAAgents =
                clawgicAgentService.listAgents("0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        assertEquals(1, walletAAgents.size());
        assertEquals("Wallet A Agent", walletAAgents.getFirst().name());
        assertEquals("OPENAI", walletAAgents.getFirst().providerType());
    }

    @Test
    void getLeaderboardReturnsDeterministicRankedOrderWithTieBreaks() {
        OffsetDateTime baseTime = OffsetDateTime.parse("2026-02-28T10:00:00Z");

        UUID highestElo = createAgentWithEloStats(
                "0x1000000000000000000000000000000000000001",
                "Top Seed",
                baseTime.minusHours(4),
                9200,
                17,
                12,
                1
        );
        UUID tieEarlierCreated = createAgentWithEloStats(
                "0x1000000000000000000000000000000000000002",
                "Tie Earlier",
                baseTime.minusHours(3),
                9100,
                9,
                6,
                0
        );
        UUID tieLaterCreated = createAgentWithEloStats(
                "0x1000000000000000000000000000000000000003",
                "Tie Later",
                baseTime.minusHours(2),
                9100,
                9,
                5,
                0
        );
        UUID lowerElo = createAgentWithEloStats(
                "0x1000000000000000000000000000000000000004",
                "Lower Seed",
                baseTime.minusHours(1),
                9000,
                22,
                13,
                3
        );

        ClawgicAgentResponses.AgentLeaderboardPage leaderboard = clawgicAgentService.getLeaderboard(0, 10);

        assertTrue(leaderboard.total() >= 4);
        assertTrue(leaderboard.entries().size() >= 4);

        assertEquals(1, leaderboard.entries().get(0).rank());
        assertEquals(highestElo, leaderboard.entries().get(0).agentId());
        assertEquals(9200, leaderboard.entries().get(0).currentElo());

        assertEquals(2, leaderboard.entries().get(1).rank());
        assertEquals(tieEarlierCreated, leaderboard.entries().get(1).agentId());
        assertEquals(9100, leaderboard.entries().get(1).currentElo());
        assertEquals(9, leaderboard.entries().get(1).matchesPlayed());

        assertEquals(3, leaderboard.entries().get(2).rank());
        assertEquals(tieLaterCreated, leaderboard.entries().get(2).agentId());

        assertEquals(4, leaderboard.entries().get(3).rank());
        assertEquals(lowerElo, leaderboard.entries().get(3).agentId());
    }

    @Test
    void getLeaderboardAppliesPaginationOffsetAndLimit() {
        OffsetDateTime baseTime = OffsetDateTime.parse("2026-02-28T12:00:00Z");
        createAgentWithEloStats(
                "0x2000000000000000000000000000000000000001",
                "Rank 1",
                baseTime.minusMinutes(5),
                8800,
                20,
                15,
                0
        );
        UUID secondRankId = createAgentWithEloStats(
                "0x2000000000000000000000000000000000000002",
                "Rank 2",
                baseTime.minusMinutes(4),
                8700,
                14,
                8,
                1
        );
        UUID thirdRankId = createAgentWithEloStats(
                "0x2000000000000000000000000000000000000003",
                "Rank 3",
                baseTime.minusMinutes(3),
                8600,
                13,
                7,
                1
        );
        createAgentWithEloStats(
                "0x2000000000000000000000000000000000000004",
                "Rank 4",
                baseTime.minusMinutes(2),
                8500,
                12,
                6,
                2
        );
        createAgentWithEloStats(
                "0x2000000000000000000000000000000000000005",
                "Rank 5",
                baseTime.minusMinutes(1),
                8400,
                11,
                5,
                2
        );

        ClawgicAgentResponses.AgentLeaderboardPage leaderboard = clawgicAgentService.getLeaderboard(1, 2);

        assertEquals(1, leaderboard.offset());
        assertEquals(2, leaderboard.limit());
        assertTrue(leaderboard.total() >= 5);
        assertTrue(leaderboard.hasMore());
        assertEquals(2, leaderboard.entries().size());

        assertEquals(2, leaderboard.entries().get(0).rank());
        assertEquals(secondRankId, leaderboard.entries().get(0).agentId());
        assertEquals(3, leaderboard.entries().get(1).rank());
        assertEquals(thirdRankId, leaderboard.entries().get(1).agentId());
    }

    @Test
    void getLeaderboardRejectsInvalidPaginationParams() {
        ResponseStatusException negativeOffset = assertThrows(
                ResponseStatusException.class,
                () -> clawgicAgentService.getLeaderboard(-1, 25)
        );
        assertEquals(HttpStatus.BAD_REQUEST, negativeOffset.getStatusCode());

        ResponseStatusException tooLargeLimit = assertThrows(
                ResponseStatusException.class,
                () -> clawgicAgentService.getLeaderboard(0, 101)
        );
        assertEquals(HttpStatus.BAD_REQUEST, tooLargeLimit.getStatusCode());
    }

    private UUID createAgentWithEloStats(
            String walletAddress,
            String name,
            OffsetDateTime createdAt,
            int currentElo,
            int matchesPlayed,
            int matchesWon,
            int matchesForfeited
    ) {
        String normalizedWallet = walletAddress.toLowerCase();
        if (!clawgicUserRepository.existsById(normalizedWallet)) {
            ClawgicUser user = new ClawgicUser();
            user.setWalletAddress(normalizedWallet);
            user.setCreatedAt(createdAt.minusMinutes(1));
            user.setUpdatedAt(createdAt.minusMinutes(1));
            clawgicUserRepository.saveAndFlush(user);
        }

        UUID agentId = UUID.randomUUID();
        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(agentId);
        agent.setWalletAddress(normalizedWallet);
        agent.setName(name);
        agent.setSystemPrompt("Leaderboard integration prompt");
        agent.setApiKeyEncrypted("enc:leaderboard");
        agent.setProviderType(ClawgicProviderType.OPENAI);
        agent.setCreatedAt(createdAt);
        agent.setUpdatedAt(createdAt);
        clawgicAgentRepository.saveAndFlush(agent);

        ClawgicAgentElo agentElo = new ClawgicAgentElo();
        agentElo.setAgentId(agentId);
        agentElo.setCurrentElo(currentElo);
        agentElo.setMatchesPlayed(matchesPlayed);
        agentElo.setMatchesWon(matchesWon);
        agentElo.setMatchesForfeited(matchesForfeited);
        agentElo.setLastUpdated(createdAt.plusMinutes(5));
        clawgicAgentEloRepository.saveAndFlush(agentElo);

        return agentId;
    }
}
