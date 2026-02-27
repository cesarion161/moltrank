package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.clawgic.dto.ClawgicTournamentRequests;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicAgentElo;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorization;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.moltrank.clawgic.model.ClawgicProviderType;
import com.moltrank.clawgic.model.ClawgicStakingLedger;
import com.moltrank.clawgic.model.ClawgicStakingLedgerStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentEntry;
import com.moltrank.clawgic.model.ClawgicTournamentEntryStatus;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import com.moltrank.clawgic.repository.ClawgicAgentEloRepository;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicPaymentAuthorizationRepository;
import com.moltrank.clawgic.repository.ClawgicStakingLedgerRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentEntryRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import com.moltrank.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/moltrank}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:moltrank}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "moltrank.ingestion.enabled=false",
        "moltrank.ingestion.run-on-startup=false"
})
@Transactional
class ClawgicTournamentServiceIntegrationTest {

    @Autowired
    private ClawgicTournamentService clawgicTournamentService;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicRuntimeProperties clawgicRuntimeProperties;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicAgentEloRepository clawgicAgentEloRepository;

    @Autowired
    private ClawgicTournamentEntryRepository clawgicTournamentEntryRepository;

    @Autowired
    private ClawgicMatchRepository clawgicMatchRepository;

    @Autowired
    private ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository;

    @Autowired
    private ClawgicStakingLedgerRepository clawgicStakingLedgerRepository;

    @Test
    void createTournamentPersistsScheduledTournamentWithMvpDefaults() {
        OffsetDateTime startTime = OffsetDateTime.now().plusHours(3);
        ClawgicTournamentResponses.TournamentDetail created = clawgicTournamentService.createTournament(
                new ClawgicTournamentRequests.CreateTournamentRequest(
                        "C17 integration tournament",
                        startTime,
                        null,
                        new BigDecimal("7.250000")
                )
        );

        assertEquals(ClawgicTournamentStatus.SCHEDULED, created.status());
        assertEquals(clawgicRuntimeProperties.getTournament().getMvpBracketSize(), created.bracketSize());
        assertEquals(created.bracketSize(), created.maxEntries());
        assertEquals(
                startTime.minusMinutes(clawgicRuntimeProperties.getTournament().getDefaultEntryWindowMinutes()),
                created.entryCloseTime()
        );

        ClawgicTournament persisted = clawgicTournamentRepository.findById(created.tournamentId()).orElseThrow();
        assertEquals(new BigDecimal("7.250000"), persisted.getBaseEntryFeeUsdc());
        assertEquals(ClawgicTournamentStatus.SCHEDULED, persisted.getStatus());
    }

    @Test
    void createTournamentRejectsEntryCloseAfterStartTime() {
        OffsetDateTime startTime = OffsetDateTime.now().plusHours(4);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                clawgicTournamentService.createTournament(new ClawgicTournamentRequests.CreateTournamentRequest(
                        "Invalid entry window",
                        startTime,
                        startTime.plusMinutes(1),
                        new BigDecimal("5.000000")
                )));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("400 BAD_REQUEST \"entryCloseTime must be on or before startTime\"", ex.getMessage());
    }

    @Test
    void listUpcomingTournamentsReturnsOnlyScheduledFutureTournamentsInOrder() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament futureEarly = insertTournament(
                "future early",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1)
        );
        ClawgicTournament futureLate = insertTournament(
                "future late",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(4),
                now.plusHours(3)
        );
        insertTournament("past", ClawgicTournamentStatus.SCHEDULED, now.minusHours(1), now.minusHours(2));
        insertTournament("locked", ClawgicTournamentStatus.LOCKED, now.plusHours(5), now.plusHours(4));

        List<ClawgicTournamentResponses.TournamentSummary> upcoming =
                clawgicTournamentService.listUpcomingTournaments();

        assertEquals(2, upcoming.size());
        assertEquals(futureEarly.getTournamentId(), upcoming.get(0).tournamentId());
        assertEquals(futureLate.getTournamentId(), upcoming.get(1).tournamentId());
    }

    @Test
    void enterTournamentDevBypassCreatesEntryLedgerAndBypassedAuthorization() {
        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x1111111111111111111111111111111111111111";
        createUser(walletAddress);
        UUID agentId = createAgentWithElo(walletAddress, "C18 entrant", 1032);
        ClawgicTournament tournament = insertTournament(
                "C18 entry success",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        ClawgicTournamentResponses.TournamentEntry createdEntry = clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(agentId)
        );

        assertEquals(tournament.getTournamentId(), createdEntry.tournamentId());
        assertEquals(agentId, createdEntry.agentId());
        assertEquals(ClawgicTournamentEntryStatus.CONFIRMED, createdEntry.status());
        assertEquals(1032, createdEntry.seedSnapshotElo());

        ClawgicTournamentEntry persistedEntry =
                clawgicTournamentEntryRepository.findById(createdEntry.entryId()).orElseThrow();
        assertEquals(ClawgicTournamentEntryStatus.CONFIRMED, persistedEntry.getStatus());
        assertEquals(1032, persistedEntry.getSeedSnapshotElo());

        List<ClawgicPaymentAuthorization> authorizations =
                clawgicPaymentAuthorizationRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, authorizations.size());
        ClawgicPaymentAuthorization authorization = authorizations.getFirst();
        assertEquals(ClawgicPaymentAuthorizationStatus.BYPASSED, authorization.getStatus());
        assertEquals(createdEntry.entryId(), authorization.getEntryId());
        assertEquals(new BigDecimal("5.000000"), authorization.getAmountAuthorizedUsdc());

        List<ClawgicStakingLedger> ledgers =
                clawgicStakingLedgerRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, ledgers.size());
        ClawgicStakingLedger ledger = ledgers.getFirst();
        assertEquals(createdEntry.entryId(), ledger.getEntryId());
        assertEquals(ClawgicStakingLedgerStatus.ENTERED, ledger.getStatus());
        assertEquals(new BigDecimal("5.000000"), ledger.getAmountStaked());
        assertNotNull(ledger.getEnteredAt());
    }

    @Test
    void enterTournamentRejectsDuplicateAgentEntry() {
        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x2222222222222222222222222222222222222222";
        createUser(walletAddress);
        UUID agentId = createAgentWithElo(walletAddress, "duplicate agent", 1000);
        ClawgicTournament tournament = insertTournament(
                "C18 duplicate guard",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(agentId)
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(agentId)
                ));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(
                "409 CONFLICT \"Agent is already entered in tournament: " + tournament.getTournamentId() + "\"",
                ex.getMessage()
        );
    }

    @Test
    void enterTournamentRejectsWhenCapacityIsReached() {
        OffsetDateTime now = OffsetDateTime.now();
        String walletOne = "0x3333333333333333333333333333333333333333";
        String walletTwo = "0x4444444444444444444444444444444444444444";
        String walletThree = "0x5555555555555555555555555555555555555555";
        createUser(walletOne);
        createUser(walletTwo);
        createUser(walletThree);
        UUID firstAgentId = createAgentWithElo(walletOne, "agent one", 1010);
        UUID secondAgentId = createAgentWithElo(walletTwo, "agent two", 1005);
        UUID thirdAgentId = createAgentWithElo(walletThree, "agent three", 990);
        ClawgicTournament tournament = insertTournament(
                "C18 capacity guard",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                2
        );

        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(firstAgentId)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(secondAgentId)
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(thirdAgentId)
                ));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(
                "409 CONFLICT \"Tournament entry capacity reached: " + tournament.getTournamentId() + "\"",
                ex.getMessage()
        );
    }

    @Test
    void createMvpBracketBuildsThreeLinkedMatchesWithDeterministicSeeding() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = insertTournament(
                "C19 deterministic bracket",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        UUID seedOne = createUserAndAgentWithElo("0x1111111111111111111111111111111111111101", "seed one", 1310);
        UUID seedTwo = createUserAndAgentWithElo("0x1111111111111111111111111111111111111102", "seed two", 1270);
        UUID seedThree = createUserAndAgentWithElo("0x1111111111111111111111111111111111111103", "seed three", 1120);
        UUID seedFour = createUserAndAgentWithElo("0x1111111111111111111111111111111111111104", "seed four", 980);

        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(seedThree)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(seedOne)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(seedFour)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(seedTwo)
        );

        var createdMatches = clawgicTournamentService.createMvpBracket(tournament.getTournamentId());

        assertEquals(3, createdMatches.size());
        assertEquals(1, createdMatches.get(0).bracketRound());
        assertEquals(1, createdMatches.get(0).bracketPosition());
        assertEquals(seedOne, createdMatches.get(0).agent1Id());
        assertEquals(seedFour, createdMatches.get(0).agent2Id());
        assertEquals(1, createdMatches.get(0).nextMatchAgentSlot());

        assertEquals(1, createdMatches.get(1).bracketRound());
        assertEquals(2, createdMatches.get(1).bracketPosition());
        assertEquals(seedTwo, createdMatches.get(1).agent1Id());
        assertEquals(seedThree, createdMatches.get(1).agent2Id());
        assertEquals(2, createdMatches.get(1).nextMatchAgentSlot());

        assertEquals(2, createdMatches.get(2).bracketRound());
        assertEquals(1, createdMatches.get(2).bracketPosition());
        assertNull(createdMatches.get(2).agent1Id());
        assertNull(createdMatches.get(2).agent2Id());
        assertNull(createdMatches.get(2).nextMatchId());
        assertNull(createdMatches.get(2).nextMatchAgentSlot());

        UUID finalMatchId = createdMatches.get(2).matchId();
        assertEquals(finalMatchId, createdMatches.get(0).nextMatchId());
        assertEquals(finalMatchId, createdMatches.get(1).nextMatchId());

        ClawgicTournament persistedTournament =
                clawgicTournamentRepository.findById(tournament.getTournamentId()).orElseThrow();
        assertEquals(ClawgicTournamentStatus.LOCKED, persistedTournament.getStatus());

        List<ClawgicTournamentEntry> seededEntries =
                clawgicTournamentEntryRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(4, seededEntries.size());
        assertEquals(seedThree, seededEntries.get(0).getAgentId());
        assertEquals(3, seededEntries.get(0).getSeedPosition());
        assertEquals(seedOne, seededEntries.get(1).getAgentId());
        assertEquals(1, seededEntries.get(1).getSeedPosition());
        assertEquals(seedFour, seededEntries.get(2).getAgentId());
        assertEquals(4, seededEntries.get(2).getSeedPosition());
        assertEquals(seedTwo, seededEntries.get(3).getAgentId());
        assertEquals(2, seededEntries.get(3).getSeedPosition());

        List<ClawgicMatch> persistedMatches =
                clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(
                        tournament.getTournamentId()
                );
        assertEquals(3, persistedMatches.size());
        assertEquals(ClawgicMatchStatus.SCHEDULED, persistedMatches.get(0).getStatus());
        assertEquals(finalMatchId, persistedMatches.get(0).getNextMatchId());
        assertEquals(1, persistedMatches.get(0).getNextMatchAgentSlot());
    }

    @Test
    void createMvpBracketRejectsDuplicateGeneration() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = insertTournament(
                "C19 duplicate bracket guard",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(2),
                now.plusHours(1),
                4
        );

        UUID first = createUserAndAgentWithElo("0x1111111111111111111111111111111111111201", "agent one", 1001);
        UUID second = createUserAndAgentWithElo("0x1111111111111111111111111111111111111202", "agent two", 1002);
        UUID third = createUserAndAgentWithElo("0x1111111111111111111111111111111111111203", "agent three", 1003);
        UUID fourth = createUserAndAgentWithElo("0x1111111111111111111111111111111111111204", "agent four", 1004);

        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(first)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(second)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(third)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(fourth)
        );

        clawgicTournamentService.createMvpBracket(tournament.getTournamentId());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                clawgicTournamentService.createMvpBracket(tournament.getTournamentId())
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(
                "409 CONFLICT \"Tournament is not ready for bracket generation: " + tournament.getTournamentId() + "\"",
                ex.getMessage()
        );
    }

    private ClawgicTournament insertTournament(
            String topic,
            ClawgicTournamentStatus status,
            OffsetDateTime startTime,
            OffsetDateTime entryCloseTime
    ) {
        return insertTournament(topic, status, startTime, entryCloseTime, 4);
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

    private void createUser(String walletAddress) {
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        clawgicUserRepository.saveAndFlush(user);
    }

    private UUID createUserAndAgentWithElo(String walletAddress, String name, int elo) {
        createUser(walletAddress);
        return createAgentWithElo(walletAddress, name, elo);
    }

    private UUID createAgentWithElo(String walletAddress, String name, int elo) {
        UUID agentId = UUID.randomUUID();

        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(agentId);
        agent.setWalletAddress(walletAddress);
        agent.setName(name);
        agent.setSystemPrompt("Debate with deterministic structure.");
        agent.setApiKeyEncrypted("enc:test-key");
        agent.setProviderType(ClawgicProviderType.OPENAI);
        agent.setCreatedAt(OffsetDateTime.now());
        agent.setUpdatedAt(OffsetDateTime.now());
        clawgicAgentRepository.saveAndFlush(agent);

        ClawgicAgentElo agentElo = new ClawgicAgentElo();
        agentElo.setAgentId(agentId);
        agentElo.setCurrentElo(elo);
        agentElo.setMatchesPlayed(0);
        agentElo.setMatchesWon(0);
        agentElo.setMatchesForfeited(0);
        agentElo.setLastUpdated(OffsetDateTime.now());
        clawgicAgentEloRepository.saveAndFlush(agentElo);

        return agentId;
    }
}
