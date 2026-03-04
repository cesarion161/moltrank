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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C61 regression pack: verifies that the lobby listing endpoint returns correct
 * eligibility fields for each tournament state, and that those fields are consistent
 * with what the POST /enter endpoint would return.
 */
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
class ClawgicTournamentLobbyEligibilityWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Test
    void lobbyListShowsOpenForEnterableTournament() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertTournament(
                "Lobby open test",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topic == 'Lobby open test')].canEnter").value(true))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby open test')].entryState").value("OPEN"))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby open test')].currentEntries").value(0));
    }

    @Test
    void lobbyListShowsEntryWindowClosedForExpiredWindow() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertTournament(
                "Lobby closed test",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.minusMinutes(5),
                4
        );

        mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topic == 'Lobby closed test')].canEnter").value(false))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby closed test')].entryState").value("ENTRY_WINDOW_CLOSED"));
    }

    @Test
    void lobbyListShowsCapacityReachedForFullTournament() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = insertTournament(
                "Lobby full test",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                2
        );

        // Fill tournament to capacity
        String wallet1 = "0xc61f000000000000000000000000000000000001";
        String wallet2 = "0xc61f000000000000000000000000000000000002";
        createUser(wallet1);
        createUser(wallet2);
        UUID agentA = createAgent(wallet1, "lobby-full-a");
        UUID agentB = createAgent(wallet2, "lobby-full-b");

        postEntry(tournament.getTournamentId(), agentA).andExpect(status().isCreated());
        postEntry(tournament.getTournamentId(), agentB).andExpect(status().isCreated());

        mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topic == 'Lobby full test')].canEnter").value(false))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby full test')].entryState").value("CAPACITY_REACHED"))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby full test')].currentEntries").value(2));
    }

    @Test
    void lobbyListIncludesAllLifecycleStateTournaments() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertTournament(
                "Lobby locked test",
                ClawgicTournamentStatus.LOCKED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );
        insertTournament(
                "Lobby in-progress test",
                ClawgicTournamentStatus.IN_PROGRESS,
                now.minusHours(1),
                now.minusHours(2),
                4
        );

        // LOCKED and IN_PROGRESS tournaments are now returned in the lobby listing
        // so users can navigate to the live battle arena
        String body = mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).contains("Lobby locked test");
        org.assertj.core.api.Assertions.assertThat(body).contains("Lobby in-progress test");
    }

    @Test
    void lobbyEligibilityIsConsistentWithEntryEndpoint() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();

        // Create an open tournament
        ClawgicTournament openTournament = insertTournament(
                "Lobby consistency open",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        // Create a closed-window tournament
        insertTournament(
                "Lobby consistency closed",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.minusMinutes(1),
                4
        );

        // Verify lobby says open for the open tournament
        mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topic == 'Lobby consistency open')].canEnter").value(true))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby consistency open')].entryState").value("OPEN"))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby consistency closed')].canEnter").value(false))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby consistency closed')].entryState").value("ENTRY_WINDOW_CLOSED"));

        // Verify entry endpoint agrees for the open tournament
        String wallet = "0xc61c000000000000000000000000000000000001";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "lobby-consistency-agent");

        postEntry(openTournament.getTournamentId(), agentId)
                .andExpect(status().isCreated());
    }

    @Test
    void lobbyCurrentEntriesUpdatesAfterEntry() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = insertTournament(
                "Lobby entries count test",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        // Before entry: 0 entries
        mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topic == 'Lobby entries count test')].currentEntries").value(0));

        // Enter one agent
        String wallet = "0xc61e000000000000000000000000000000000001";
        createUser(wallet);
        UUID agentId = createAgent(wallet, "lobby-entries-count-agent");
        postEntry(tournament.getTournamentId(), agentId).andExpect(status().isCreated());

        // After entry: 1 entry
        mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topic == 'Lobby entries count test')].currentEntries").value(1))
                .andExpect(jsonPath("$[?(@.topic == 'Lobby entries count test')].canEnter").value(true));
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
        agent.setSystemPrompt("C61 lobby regression test prompt.");
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
