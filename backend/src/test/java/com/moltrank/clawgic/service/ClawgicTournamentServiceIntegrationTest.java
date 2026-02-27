package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.clawgic.dto.ClawgicTournamentRequests;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicAgentElo;
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
