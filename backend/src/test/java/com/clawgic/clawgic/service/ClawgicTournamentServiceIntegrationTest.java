package com.clawgic.clawgic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.clawgic.clawgic.config.ClawgicRuntimeProperties;
import com.clawgic.clawgic.dto.ClawgicMatchResponses;
import com.clawgic.clawgic.dto.ClawgicTournamentRequests;
import com.clawgic.clawgic.dto.ClawgicTournamentResponses;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicMatch;
import com.clawgic.clawgic.model.ClawgicMatchJudgement;
import com.clawgic.clawgic.model.ClawgicMatchJudgementStatus;
import com.clawgic.clawgic.model.ClawgicMatchStatus;
import com.clawgic.clawgic.model.ClawgicPaymentAuthorization;
import com.clawgic.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.clawgic.clawgic.model.ClawgicProviderType;
import com.clawgic.clawgic.model.ClawgicStakingLedger;
import com.clawgic.clawgic.model.ClawgicStakingLedgerStatus;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentEntry;
import com.clawgic.clawgic.model.ClawgicTournamentEntryStatus;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.model.ClawgicUser;
import com.clawgic.clawgic.model.DebatePhase;
import com.clawgic.clawgic.model.TournamentEntryState;
import com.clawgic.clawgic.model.DebateTranscriptJsonCodec;
import com.clawgic.clawgic.model.DebateTranscriptMessage;
import com.clawgic.clawgic.model.DebateTranscriptRole;
import com.clawgic.clawgic.repository.ClawgicAgentEloRepository;
import com.clawgic.clawgic.repository.ClawgicAgentRepository;
import com.clawgic.clawgic.repository.ClawgicMatchJudgementRepository;
import com.clawgic.clawgic.repository.ClawgicMatchRepository;
import com.clawgic.clawgic.repository.ClawgicPaymentAuthorizationRepository;
import com.clawgic.clawgic.repository.ClawgicStakingLedgerRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentEntryRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentRepository;
import com.clawgic.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.clawgic.clawgic.web.TournamentEntryConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private ClawgicMatchJudgementRepository clawgicMatchJudgementRepository;

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
    void listUpcomingTournamentsReturnsOpenEligibilityForEnterableTournament() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = insertTournament(
                "eligibility open",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(3),
                now.plusHours(2)
        );

        List<ClawgicTournamentResponses.TournamentSummary> upcoming =
                clawgicTournamentService.listUpcomingTournaments();

        assertEquals(1, upcoming.size());
        ClawgicTournamentResponses.TournamentSummary summary = upcoming.getFirst();
        assertEquals(tournament.getTournamentId(), summary.tournamentId());
        assertEquals(0, summary.currentEntries());
        assertTrue(summary.canEnter());
        assertEquals(TournamentEntryState.OPEN, summary.entryState());
        assertNotNull(summary.entryStateReason());
        assertTrue(summary.entryStateReason().contains("0/4"));
    }

    @Test
    void listUpcomingTournamentsReturnsEntryWindowClosedForExpiredWindow() {
        OffsetDateTime now = OffsetDateTime.now();
        insertTournament(
                "window closed",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(3),
                now.minusMinutes(5)
        );

        List<ClawgicTournamentResponses.TournamentSummary> upcoming =
                clawgicTournamentService.listUpcomingTournaments();

        assertEquals(1, upcoming.size());
        ClawgicTournamentResponses.TournamentSummary summary = upcoming.getFirst();
        assertFalse(summary.canEnter());
        assertEquals(TournamentEntryState.ENTRY_WINDOW_CLOSED, summary.entryState());
        assertNotNull(summary.entryStateReason());
    }

    @Test
    void listUpcomingTournamentsReturnsCapacityReachedForFullTournament() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = insertTournament(
                "capacity full",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(3),
                now.plusHours(2),
                2
        );

        String walletOne = "0xC57A111111111111111111111111111111111111";
        String walletTwo = "0xC57A222222222222222222222222222222222222";
        createUser(walletOne);
        createUser(walletTwo);
        UUID agentOne = createAgentWithElo(walletOne, "c57 agent one", 1000);
        UUID agentTwo = createAgentWithElo(walletTwo, "c57 agent two", 1000);
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(agentOne)
        );
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(agentTwo)
        );

        List<ClawgicTournamentResponses.TournamentSummary> upcoming =
                clawgicTournamentService.listUpcomingTournaments();

        assertEquals(1, upcoming.size());
        ClawgicTournamentResponses.TournamentSummary summary = upcoming.getFirst();
        assertEquals(2, summary.currentEntries());
        assertFalse(summary.canEnter());
        assertEquals(TournamentEntryState.CAPACITY_REACHED, summary.entryState());
        assertTrue(summary.entryStateReason().contains("2/2"));
    }

    @Test
    void listUpcomingTournamentsReturnsCurrentEntriesCount() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = insertTournament(
                "partial entries",
                ClawgicTournamentStatus.SCHEDULED,
                now.plusHours(3),
                now.plusHours(2),
                4
        );

        String wallet = "0xC57B111111111111111111111111111111111111";
        createUser(wallet);
        UUID agentId = createAgentWithElo(wallet, "c57 partial agent", 1000);
        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(agentId)
        );

        List<ClawgicTournamentResponses.TournamentSummary> upcoming =
                clawgicTournamentService.listUpcomingTournaments();

        assertEquals(1, upcoming.size());
        ClawgicTournamentResponses.TournamentSummary summary = upcoming.getFirst();
        assertEquals(1, summary.currentEntries());
        assertTrue(summary.canEnter());
        assertEquals(TournamentEntryState.OPEN, summary.entryState());
        assertTrue(summary.entryStateReason().contains("1/4"));
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

        TournamentEntryConflictException ex = assertThrows(TournamentEntryConflictException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(agentId)
                ));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("already_entered", ex.getCode());
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

        TournamentEntryConflictException ex = assertThrows(TournamentEntryConflictException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(thirdAgentId)
                ));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("capacity_reached", ex.getCode());
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

    @Test
    void getTournamentResultsReturnsTranscriptJudgeRowsAndEloSnapshots() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID agentOne = createUserAndAgentWithElo("0x1111111111111111111111111111111111111301", "results one", 1016);
        UUID agentTwo = createUserAndAgentWithElo("0x1111111111111111111111111111111111111302", "results two", 984);
        ClawgicTournament tournament = insertTournament(
                "C45 results payload",
                ClawgicTournamentStatus.COMPLETED,
                now.minusHours(1),
                now.minusHours(2),
                4
        );
        tournament.setWinnerAgentId(agentOne);
        tournament.setStartedAt(now.minusMinutes(45));
        tournament.setCompletedAt(now.minusMinutes(10));
        tournament.setMatchesCompleted(3);
        tournament.setMatchesForfeited(0);
        tournament.setUpdatedAt(now.minusMinutes(10));
        clawgicTournamentRepository.saveAndFlush(tournament);

        createTournamentEntry(tournament.getTournamentId(), agentOne, 1, 1030, now.minusHours(2));
        createTournamentEntry(tournament.getTournamentId(), agentTwo, 2, 1025, now.minusHours(2).plusMinutes(1));

        UUID matchId = UUID.randomUUID();
        ClawgicMatch match = new ClawgicMatch();
        match.setMatchId(matchId);
        match.setTournamentId(tournament.getTournamentId());
        match.setAgent1Id(agentOne);
        match.setAgent2Id(agentTwo);
        match.setBracketRound(2);
        match.setBracketPosition(1);
        match.setStatus(ClawgicMatchStatus.COMPLETED);
        match.setPhase(DebatePhase.CONCLUSION);
        match.setTranscriptJson(DebateTranscriptJsonCodec.toJson(List.of(
                new DebateTranscriptMessage(
                        DebateTranscriptRole.AGENT_1,
                        DebatePhase.ARGUMENTATION,
                        "Agent one argues from measurable benchmarks."
                ),
                new DebateTranscriptMessage(
                        DebateTranscriptRole.AGENT_2,
                        DebatePhase.COUNTER_ARGUMENTATION,
                        "Agent two challenges benchmark selection bias."
                )
        )));
        match.setJudgeResultJson(JsonNodeFactory.instance.objectNode().put("winner_id", agentOne.toString()));
        match.setWinnerAgentId(agentOne);
        match.setAgent1EloBefore(1000);
        match.setAgent1EloAfter(1016);
        match.setAgent2EloBefore(1000);
        match.setAgent2EloAfter(984);
        match.setJudgeRetryCount(0);
        match.setStartedAt(now.minusMinutes(30));
        match.setJudgeRequestedAt(now.minusMinutes(20));
        match.setJudgedAt(now.minusMinutes(12));
        match.setCompletedAt(now.minusMinutes(10));
        match.setCreatedAt(now.minusMinutes(40));
        match.setUpdatedAt(now.minusMinutes(10));
        clawgicMatchRepository.saveAndFlush(match);

        ClawgicMatchJudgement judgement = new ClawgicMatchJudgement();
        judgement.setJudgementId(UUID.randomUUID());
        judgement.setMatchId(matchId);
        judgement.setTournamentId(tournament.getTournamentId());
        judgement.setJudgeKey("mock-judge-primary");
        judgement.setJudgeModel("mock-gpt4o");
        judgement.setStatus(ClawgicMatchJudgementStatus.ACCEPTED);
        judgement.setAttempt(1);
        judgement.setResultJson(JsonNodeFactory.instance.objectNode().put("winner_id", agentOne.toString()));
        judgement.setWinnerAgentId(agentOne);
        judgement.setAgent1LogicScore(9);
        judgement.setAgent1PersonaAdherenceScore(8);
        judgement.setAgent1RebuttalStrengthScore(9);
        judgement.setAgent2LogicScore(8);
        judgement.setAgent2PersonaAdherenceScore(7);
        judgement.setAgent2RebuttalStrengthScore(8);
        judgement.setReasoning("Agent one maintained stronger logical continuity.");
        judgement.setJudgedAt(now.minusMinutes(12));
        judgement.setCreatedAt(now.minusMinutes(12));
        judgement.setUpdatedAt(now.minusMinutes(12));
        clawgicMatchJudgementRepository.saveAndFlush(judgement);

        ClawgicTournamentResponses.TournamentResults results =
                clawgicTournamentService.getTournamentResults(tournament.getTournamentId());

        assertEquals(tournament.getTournamentId(), results.tournament().tournamentId());
        assertEquals(ClawgicTournamentStatus.COMPLETED, results.tournament().status());
        assertEquals(2, results.entries().size());
        assertEquals(agentOne, results.entries().getFirst().agentId());
        assertEquals(1, results.entries().getFirst().seedPosition());

        assertEquals(1, results.matches().size());
        ClawgicMatchResponses.MatchDetail matchDetail = results.matches().getFirst();
        assertEquals(matchId, matchDetail.matchId());
        assertEquals(ClawgicMatchStatus.COMPLETED, matchDetail.status());
        assertEquals(1000, matchDetail.agent1EloBefore());
        assertEquals(1016, matchDetail.agent1EloAfter());
        assertEquals(1000, matchDetail.agent2EloBefore());
        assertEquals(984, matchDetail.agent2EloAfter());
        assertEquals(1, matchDetail.judgements().size());
        assertEquals(ClawgicMatchJudgementStatus.ACCEPTED, matchDetail.judgements().getFirst().status());
        assertTrue(matchDetail.transcriptJson().isArray());

        JsonNode firstTranscriptMessage = matchDetail.transcriptJson().get(0);
        assertNotNull(firstTranscriptMessage);
        assertTrue(firstTranscriptMessage.hasNonNull("role"));
        assertTrue(firstTranscriptMessage.hasNonNull("phase"));
        assertTrue(firstTranscriptMessage.hasNonNull("content"));
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

    private void createTournamentEntry(
            UUID tournamentId,
            UUID agentId,
            int seedPosition,
            int seedSnapshotElo,
            OffsetDateTime createdAt
    ) {
        ClawgicTournamentEntry entry = new ClawgicTournamentEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setTournamentId(tournamentId);
        entry.setAgentId(agentId);
        entry.setWalletAddress(clawgicAgentRepository.findById(agentId).orElseThrow().getWalletAddress());
        entry.setStatus(ClawgicTournamentEntryStatus.CONFIRMED);
        entry.setSeedPosition(seedPosition);
        entry.setSeedSnapshotElo(seedSnapshotElo);
        entry.setCreatedAt(createdAt);
        entry.setUpdatedAt(createdAt);
        clawgicTournamentEntryRepository.saveAndFlush(entry);
    }
}
