package com.clawgic.clawgic.web;

import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicProviderType;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.model.ClawgicUser;
import com.clawgic.clawgic.repository.ClawgicAgentRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentRepository;
import com.clawgic.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/clawgic}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:clawgic}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "x402.enabled=false",
        "x402.dev-bypass-enabled=true",
})
@AutoConfigureMockMvc
@Transactional
class ClawgicTournamentEntryConflictMatrixWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Test
    void scheduledAndEntryOpenReturnsCreatedEntry() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String wallet = "0x1000000000000000000000000000000000000001";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "matrix-open");
        ClawgicTournament tournament = insertTournament(
                "C56 matrix open",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournament.getTournamentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tournamentId").value(tournament.getTournamentId().toString()))
                .andExpect(jsonPath("$.agentId").value(agentId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void scheduledButEntryClosedReturnsConflictWithCode() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String wallet = "0x1000000000000000000000000000000000000002";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "matrix-entry-closed");
        ClawgicTournament tournament = insertTournament(
                "C56 matrix closed",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.minusMinutes(1),
                4
        );

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournament.getTournamentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("entry_window_closed"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void lockedTournamentReturnsConflictWithCode() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String wallet = "0x1000000000000000000000000000000000000003";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "matrix-locked");
        ClawgicTournament tournament = insertTournament(
                "C56 matrix locked",
                ClawgicTournamentStatus.LOCKED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournament.getTournamentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("tournament_not_open"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void fullTournamentReturnsConflictWithCode() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String walletOne = "0x1000000000000000000000000000000000000004";
        String walletTwo = "0x1000000000000000000000000000000000000005";
        createUser(walletOne);
        createUser(walletTwo);
        UUID agentOneId = createAgent(walletOne, "matrix-full-a");
        UUID agentTwoId = createAgent(walletTwo, "matrix-full-b");
        ClawgicTournament tournament = insertTournament(
                "C56 matrix full",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                2
        );

        postEntry(tournament.getTournamentId(), agentOneId)
                .andExpect(status().isCreated());
        postEntry(tournament.getTournamentId(), agentTwoId)
                .andExpect(status().isCreated());

        String walletThree = "0x1000000000000000000000000000000000000007";
        createUser(walletThree);
        UUID agentThreeId = createAgent(walletThree, "matrix-full-c");

        postEntry(tournament.getTournamentId(), agentThreeId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("capacity_reached"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void duplicateEntryReturnsConflictWithCode() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String wallet = "0x1000000000000000000000000000000000000006";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "matrix-duplicate");
        ClawgicTournament tournament = insertTournament(
                "C56 matrix duplicate",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        postEntry(tournament.getTournamentId(), agentId)
                .andExpect(status().isCreated());

        postEntry(tournament.getTournamentId(), agentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_entered"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalidAgentReturnsNotFoundWithCode() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String wallet = "0x1000000000000000000000000000000000000008";
        createUser(wallet);
        ClawgicTournament tournament = insertTournament(
                "C58 invalid agent",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );
        UUID nonExistentAgentId = UUID.randomUUID();

        postEntry(tournament.getTournamentId(), nonExistentAgentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("invalid_agent"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void completedTournamentReturnsConflictWithCode() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String wallet = "0x1000000000000000000000000000000000000009";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "matrix-completed");
        ClawgicTournament tournament = insertTournament(
                "C58 completed tournament",
                ClawgicTournamentStatus.COMPLETED,
                now.minusHours(2),
                now.minusHours(3),
                4
        );

        postEntry(tournament.getTournamentId(), agentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("tournament_not_open"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void inProgressTournamentReturnsConflictWithCode() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String wallet = "0x100000000000000000000000000000000000000a";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "matrix-in-progress");
        ClawgicTournament tournament = insertTournament(
                "C58 in-progress tournament",
                ClawgicTournamentStatus.IN_PROGRESS,
                now.minusHours(1),
                now.minusHours(2),
                4
        );

        postEntry(tournament.getTournamentId(), agentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("tournament_not_open"))
                .andExpect(jsonPath("$.message").exists());
    }

    private org.springframework.test.web.servlet.ResultActions postEntry(UUID tournamentId, UUID agentId)
            throws Exception {
        return mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournamentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "agentId": "%s"
                        }
                        """.formatted(agentId)));
    }

    private void createUser(String walletAddress) {
        if (clawgicUserRepository.existsById(walletAddress)) {
            return;
        }
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        clawgicUserRepository.saveAndFlush(user);
    }

    private UUID createAgent(String walletAddress, String name) {
        UUID agentId = UUID.randomUUID();
        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(agentId);
        agent.setWalletAddress(walletAddress);
        agent.setName(name);
        agent.setSystemPrompt("Matrix test prompt.");
        agent.setApiKeyEncrypted("enc:test-key");
        agent.setProviderType(ClawgicProviderType.OPENAI);
        agent.setCreatedAt(OffsetDateTime.now());
        agent.setUpdatedAt(OffsetDateTime.now());
        clawgicAgentRepository.saveAndFlush(agent);
        return agentId;
    }

    private ClawgicTournament insertTournament(
            String topic,
            ClawgicTournamentStatus status,
            OffsetDateTime startTime,
            OffsetDateTime entryCloseTime,
            int maxEntries
    ) {
        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(topic);
        tournament.setStatus(status);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(maxEntries);
        tournament.setStartTime(startTime);
        tournament.setEntryCloseTime(entryCloseTime);
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setCreatedAt(entryCloseTime.minusMinutes(10));
        tournament.setUpdatedAt(entryCloseTime.minusMinutes(10));
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }
}
