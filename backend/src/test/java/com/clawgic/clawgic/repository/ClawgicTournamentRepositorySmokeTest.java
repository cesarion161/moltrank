package com.clawgic.clawgic.repository;

import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentEntry;
import com.clawgic.clawgic.model.ClawgicTournamentEntryStatus;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.model.ClawgicUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class ClawgicTournamentRepositorySmokeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicTournamentEntryRepository clawgicTournamentEntryRepository;

    @BeforeEach
    void isolateClawgicTournamentTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    clawgic_matches,
                    clawgic_tournament_entries,
                    clawgic_payment_authorizations,
                    clawgic_staking_ledger,
                    clawgic_tournaments,
                    clawgic_agent_elo,
                    clawgic_agents,
                    clawgic_users
                CASCADE
                """);
    }

    @Test
    void flywayCreatesTournamentTablesAndRepositoriesSupportUpcomingAndEntryQueries() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('clawgic_tournaments', 'clawgic_tournament_entries')
                        """,
                Integer.class
        );
        assertEquals(2, tableCount);

        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x2222222222222222222222222222222222222222";
        createUser(walletAddress);

        UUID agentOneId = createAgent(walletAddress, "Agent One");
        UUID agentTwoId = createAgent(walletAddress, "Agent Two");
        UUID agentThreeId = createAgent(walletAddress, "Agent Three");

        ClawgicTournament futureEarly = createTournament(
                "Debate: Is determinism required?",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1)
        );
        ClawgicTournament futureLate = createTournament(
                "Debate: Are judges agents too?",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(3),
                now.plusHours(2)
        );
        createTournament(
                "Past tournament",
                ClawgicTournamentStatus.SCHEDULED,
                now.minusHours(1),
                now.minusHours(2)
        );
        createTournament(
                "Locked future tournament",
                ClawgicTournamentStatus.LOCKED,
                now.plusHours(4),
                now.plusHours(3)
        );

        List<ClawgicTournament> upcoming =
                clawgicTournamentRepository.findByStatusAndStartTimeAfterOrderByStartTimeAsc(
                        ClawgicTournamentStatus.SCHEDULED,
                        now
                );

        assertEquals(2, upcoming.size());
        assertEquals(futureEarly.getTournamentId(), upcoming.get(0).getTournamentId());
        assertEquals(futureLate.getTournamentId(), upcoming.get(1).getTournamentId());

        createEntry(futureEarly.getTournamentId(), agentOneId, walletAddress, 1, 1120, now.plusMinutes(1));
        createEntry(futureEarly.getTournamentId(), agentTwoId, walletAddress, 2, 1050, now.plusMinutes(2));
        createEntry(futureEarly.getTournamentId(), agentThreeId, walletAddress, null, 990, now.plusMinutes(3));

        List<ClawgicTournamentEntry> entries =
                clawgicTournamentEntryRepository.findByTournamentIdOrderByCreatedAtAsc(futureEarly.getTournamentId());

        assertEquals(3, entries.size());
        assertEquals(agentOneId, entries.get(0).getAgentId());
        assertEquals(agentTwoId, entries.get(1).getAgentId());
        assertEquals(agentThreeId, entries.get(2).getAgentId());
        assertTrue(clawgicTournamentEntryRepository.existsByTournamentIdAndAgentId(
                futureEarly.getTournamentId(),
                agentTwoId
        ));
    }

    @Test
    void tournamentEntriesEnforceOneAgentPerTournamentUniqueness() {
        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x3333333333333333333333333333333333333333";
        createUser(walletAddress);
        UUID agentId = createAgent(walletAddress, "Duplicate Guard Agent");
        ClawgicTournament tournament = createTournament(
                "Uniqueness guard tournament",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1)
        );

        createEntry(tournament.getTournamentId(), agentId, walletAddress, 1, 1000, now);

        assertThrows(DataIntegrityViolationException.class, () -> {
            createEntry(tournament.getTournamentId(), agentId, walletAddress, 2, 1000, now.plusMinutes(1));
            clawgicTournamentEntryRepository.flush();
        });
    }

    private void createUser(String walletAddress) {
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        clawgicUserRepository.saveAndFlush(user);
    }

    private UUID createAgent(String walletAddress, String name) {
        ClawgicAgent agent = new ClawgicAgent();
        UUID agentId = UUID.randomUUID();
        agent.setAgentId(agentId);
        agent.setWalletAddress(walletAddress);
        agent.setName(name);
        agent.setSystemPrompt("Debate precisely and cite your assumptions.");
        agent.setApiKeyEncrypted("enc:test");
        clawgicAgentRepository.saveAndFlush(agent);
        return agentId;
    }

    private ClawgicTournament createTournament(
            String topic,
            ClawgicTournamentStatus status,
            OffsetDateTime startTime,
            OffsetDateTime entryCloseTime
    ) {
        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(topic);
        tournament.setStatus(status);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(4);
        tournament.setStartTime(startTime);
        tournament.setEntryCloseTime(entryCloseTime);
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setCreatedAt(entryCloseTime.minusHours(1));
        tournament.setUpdatedAt(entryCloseTime.minusHours(1));
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }

    private ClawgicTournamentEntry createEntry(
            UUID tournamentId,
            UUID agentId,
            String walletAddress,
            Integer seedPosition,
            int seedSnapshotElo,
            OffsetDateTime createdAt
    ) {
        ClawgicTournamentEntry entry = new ClawgicTournamentEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setTournamentId(tournamentId);
        entry.setAgentId(agentId);
        entry.setWalletAddress(walletAddress);
        entry.setStatus(ClawgicTournamentEntryStatus.CONFIRMED);
        entry.setSeedPosition(seedPosition);
        entry.setSeedSnapshotElo(seedSnapshotElo);
        entry.setCreatedAt(createdAt);
        entry.setUpdatedAt(createdAt);
        return clawgicTournamentEntryRepository.saveAndFlush(entry);
    }
}
