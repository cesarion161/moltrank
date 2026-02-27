package com.moltrank.clawgic.service;

import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicProviderType;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import com.moltrank.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/moltrank}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:moltrank}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "moltrank.ingestion.enabled=false",
        "moltrank.ingestion.run-on-startup=false",
        "clawgic.mock-provider=true",
        "clawgic.debate.provider-timeout-seconds=1"
})
@Transactional
class ClawgicMatchLifecycleServiceIntegrationTest {

    @Autowired
    private ClawgicMatchLifecycleService clawgicMatchLifecycleService;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicMatchRepository clawgicMatchRepository;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void isolateClawgicTables() {
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
    void processLifecycleTickActivatesDueLockedTournamentAndExecutesReadySemifinals() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = createTournament(ClawgicTournamentStatus.LOCKED, now.minusMinutes(2));

        UUID agentOne = createUserAndAgent("semi agent one");
        UUID agentTwo = createUserAndAgent("semi agent two");
        UUID agentThree = createUserAndAgent("semi agent three");
        UUID agentFour = createUserAndAgent("semi agent four");

        ClawgicMatch finalMatch = createMatch(
                tournament.getTournamentId(),
                null,
                null,
                2,
                1,
                ClawgicMatchStatus.SCHEDULED
        );

        createMatch(
                tournament.getTournamentId(),
                agentOne,
                agentFour,
                1,
                1,
                ClawgicMatchStatus.SCHEDULED,
                finalMatch.getMatchId(),
                1,
                null
        );
        createMatch(
                tournament.getTournamentId(),
                agentTwo,
                agentThree,
                1,
                2,
                ClawgicMatchStatus.SCHEDULED,
                finalMatch.getMatchId(),
                2,
                null
        );

        ClawgicMatchLifecycleService.TickSummary summary = clawgicMatchLifecycleService.processLifecycleTick();

        assertEquals(1, summary.tournamentsActivated());
        assertEquals(0, summary.winnersPropagated());
        assertEquals(0, summary.tournamentsCompleted());
        assertEquals(2, summary.matchesExecuted());
        assertTrue(summary.hasWork());

        ClawgicTournament persistedTournament =
                clawgicTournamentRepository.findById(tournament.getTournamentId()).orElseThrow();
        assertEquals(ClawgicTournamentStatus.IN_PROGRESS, persistedTournament.getStatus());
        assertNotNull(persistedTournament.getStartedAt());

        ClawgicMatch semifinalOne = clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(
                tournament.getTournamentId()
        ).get(0);
        ClawgicMatch semifinalTwo = clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(
                tournament.getTournamentId()
        ).get(1);
        ClawgicMatch persistedFinal = clawgicMatchRepository.findById(finalMatch.getMatchId()).orElseThrow();

        assertEquals(ClawgicMatchStatus.PENDING_JUDGE, semifinalOne.getStatus());
        assertEquals(ClawgicMatchStatus.PENDING_JUDGE, semifinalTwo.getStatus());
        assertEquals(ClawgicMatchStatus.SCHEDULED, persistedFinal.getStatus());
        assertNull(persistedFinal.getAgent1Id());
        assertNull(persistedFinal.getAgent2Id());
    }

    @Test
    void processLifecycleTickNoOpsWhenTournamentHasNotStarted() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = createTournament(ClawgicTournamentStatus.LOCKED, now.plusHours(2));
        UUID agentOne = createUserAndAgent("future agent one");
        UUID agentTwo = createUserAndAgent("future agent two");
        createMatch(
                tournament.getTournamentId(),
                agentOne,
                agentTwo,
                1,
                1,
                ClawgicMatchStatus.SCHEDULED
        );

        ClawgicMatchLifecycleService.TickSummary summary = clawgicMatchLifecycleService.processLifecycleTick();

        assertFalse(summary.hasWork());
        assertEquals(0, summary.tournamentsActivated());
        assertEquals(0, summary.winnersPropagated());
        assertEquals(0, summary.tournamentsCompleted());
        assertEquals(0, summary.matchesExecuted());

        ClawgicTournament persistedTournament =
                clawgicTournamentRepository.findById(tournament.getTournamentId()).orElseThrow();
        assertEquals(ClawgicTournamentStatus.LOCKED, persistedTournament.getStatus());

        ClawgicMatch persistedMatch = clawgicMatchRepository.findByTournamentIdOrderByCreatedAtAsc(
                tournament.getTournamentId()
        ).getFirst();
        assertEquals(ClawgicMatchStatus.SCHEDULED, persistedMatch.getStatus());
    }

    @Test
    void processLifecycleTickWaitsForBothSemifinalWinnersBeforeExecutingFinal() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = createTournament(ClawgicTournamentStatus.IN_PROGRESS, now.minusMinutes(5));

        UUID semifinalOneWinner = createUserAndAgent("final slot one winner");
        UUID semifinalOneLoser = createUserAndAgent("final slot one loser");
        UUID semifinalTwoWinner = createUserAndAgent("final slot two winner");
        UUID semifinalTwoLoser = createUserAndAgent("final slot two loser");

        ClawgicMatch finalMatch = createMatch(
                tournament.getTournamentId(),
                null,
                null,
                2,
                1,
                ClawgicMatchStatus.SCHEDULED
        );

        createMatch(
                tournament.getTournamentId(),
                semifinalOneWinner,
                semifinalOneLoser,
                1,
                1,
                ClawgicMatchStatus.FORFEITED,
                finalMatch.getMatchId(),
                1,
                semifinalOneWinner
        );

        ClawgicMatch unresolvedSemifinal = createMatch(
                tournament.getTournamentId(),
                semifinalTwoWinner,
                semifinalTwoLoser,
                1,
                2,
                ClawgicMatchStatus.COMPLETED,
                finalMatch.getMatchId(),
                2,
                null
        );

        ClawgicMatchLifecycleService.TickSummary firstTick = clawgicMatchLifecycleService.processLifecycleTick();

        assertEquals(0, firstTick.matchesExecuted());
        assertEquals(1, firstTick.winnersPropagated());
        assertEquals(0, firstTick.tournamentsCompleted());

        ClawgicMatch afterFirstTickFinal = clawgicMatchRepository.findById(finalMatch.getMatchId()).orElseThrow();
        assertEquals(semifinalOneWinner, afterFirstTickFinal.getAgent1Id());
        assertNull(afterFirstTickFinal.getAgent2Id());
        assertEquals(ClawgicMatchStatus.SCHEDULED, afterFirstTickFinal.getStatus());

        unresolvedSemifinal.setStatus(ClawgicMatchStatus.FORFEITED);
        unresolvedSemifinal.setWinnerAgentId(semifinalTwoWinner);
        unresolvedSemifinal.setForfeitReason("TEST_FORFEIT");
        unresolvedSemifinal.setForfeitedAt(OffsetDateTime.now());
        unresolvedSemifinal.setUpdatedAt(OffsetDateTime.now());
        clawgicMatchRepository.saveAndFlush(unresolvedSemifinal);

        ClawgicMatchLifecycleService.TickSummary secondTick = clawgicMatchLifecycleService.processLifecycleTick();

        assertEquals(1, secondTick.matchesExecuted());
        assertTrue(secondTick.winnersPropagated() >= 1);
        assertEquals(0, secondTick.tournamentsCompleted());

        ClawgicMatch afterSecondTickFinal = clawgicMatchRepository.findById(finalMatch.getMatchId()).orElseThrow();
        assertEquals(semifinalOneWinner, afterSecondTickFinal.getAgent1Id());
        assertEquals(semifinalTwoWinner, afterSecondTickFinal.getAgent2Id());
        assertEquals(ClawgicMatchStatus.PENDING_JUDGE, afterSecondTickFinal.getStatus());
        assertNotNull(afterSecondTickFinal.getJudgeRequestedAt());
    }

    @Test
    void processLifecycleTickCompletesTournamentWhenFinalMatchIsCompleted() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = createTournament(ClawgicTournamentStatus.IN_PROGRESS, now.minusMinutes(5));

        UUID semifinalOneWinner = createUserAndAgent("c29 complete semifinal one winner");
        UUID semifinalOneLoser = createUserAndAgent("c29 complete semifinal one loser");
        UUID semifinalTwoWinner = createUserAndAgent("c29 complete semifinal two winner");
        UUID semifinalTwoLoser = createUserAndAgent("c29 complete semifinal two loser");

        ClawgicMatch finalMatch = createMatch(
                tournament.getTournamentId(),
                semifinalOneWinner,
                semifinalTwoWinner,
                2,
                1,
                ClawgicMatchStatus.COMPLETED,
                null,
                null,
                semifinalOneWinner
        );

        createMatch(
                tournament.getTournamentId(),
                semifinalOneWinner,
                semifinalOneLoser,
                1,
                1,
                ClawgicMatchStatus.COMPLETED,
                finalMatch.getMatchId(),
                1,
                semifinalOneWinner
        );
        createMatch(
                tournament.getTournamentId(),
                semifinalTwoWinner,
                semifinalTwoLoser,
                1,
                2,
                ClawgicMatchStatus.FORFEITED,
                finalMatch.getMatchId(),
                2,
                semifinalTwoWinner
        );

        ClawgicMatchLifecycleService.TickSummary summary = clawgicMatchLifecycleService.processLifecycleTick();

        assertEquals(0, summary.matchesExecuted());
        assertEquals(0, summary.winnersPropagated());
        assertEquals(1, summary.tournamentsCompleted());

        ClawgicTournament persistedTournament =
                clawgicTournamentRepository.findById(tournament.getTournamentId()).orElseThrow();
        assertEquals(ClawgicTournamentStatus.COMPLETED, persistedTournament.getStatus());
        assertEquals(semifinalOneWinner, persistedTournament.getWinnerAgentId());
        assertEquals(2, persistedTournament.getMatchesCompleted());
        assertEquals(1, persistedTournament.getMatchesForfeited());
        assertNotNull(persistedTournament.getCompletedAt());
    }

    @Test
    void processLifecycleTickCompletesTournamentWhenFinalMatchIsForfeited() {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = createTournament(ClawgicTournamentStatus.IN_PROGRESS, now.minusMinutes(5));

        UUID semifinalOneWinner = createUserAndAgent("c29 forfeit semifinal one winner");
        UUID semifinalOneLoser = createUserAndAgent("c29 forfeit semifinal one loser");
        UUID semifinalTwoWinner = createUserAndAgent("c29 forfeit semifinal two winner");
        UUID semifinalTwoLoser = createUserAndAgent("c29 forfeit semifinal two loser");

        ClawgicMatch finalMatch = createMatch(
                tournament.getTournamentId(),
                semifinalOneWinner,
                semifinalTwoWinner,
                2,
                1,
                ClawgicMatchStatus.FORFEITED,
                null,
                null,
                semifinalTwoWinner
        );

        createMatch(
                tournament.getTournamentId(),
                semifinalOneWinner,
                semifinalOneLoser,
                1,
                1,
                ClawgicMatchStatus.COMPLETED,
                finalMatch.getMatchId(),
                1,
                semifinalOneWinner
        );
        createMatch(
                tournament.getTournamentId(),
                semifinalTwoWinner,
                semifinalTwoLoser,
                1,
                2,
                ClawgicMatchStatus.COMPLETED,
                finalMatch.getMatchId(),
                2,
                semifinalTwoWinner
        );

        ClawgicMatchLifecycleService.TickSummary summary = clawgicMatchLifecycleService.processLifecycleTick();

        assertEquals(0, summary.matchesExecuted());
        assertEquals(0, summary.winnersPropagated());
        assertEquals(1, summary.tournamentsCompleted());

        ClawgicTournament persistedTournament =
                clawgicTournamentRepository.findById(tournament.getTournamentId()).orElseThrow();
        assertEquals(ClawgicTournamentStatus.COMPLETED, persistedTournament.getStatus());
        assertEquals(semifinalTwoWinner, persistedTournament.getWinnerAgentId());
        assertEquals(2, persistedTournament.getMatchesCompleted());
        assertEquals(1, persistedTournament.getMatchesForfeited());
        assertNotNull(persistedTournament.getCompletedAt());
    }

    private ClawgicTournament createTournament(ClawgicTournamentStatus status, OffsetDateTime startTime) {
        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic("C24 lifecycle integration topic");
        tournament.setStatus(status);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(4);
        tournament.setStartTime(startTime);
        tournament.setEntryCloseTime(startTime.minusMinutes(30));
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setCreatedAt(startTime.minusHours(1));
        tournament.setUpdatedAt(startTime.minusHours(1));
        if (status == ClawgicTournamentStatus.IN_PROGRESS) {
            tournament.setStartedAt(startTime);
        }
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }

    private UUID createUserAndAgent(String nameSeed) {
        String walletAddress = randomWalletAddress();
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        clawgicUserRepository.saveAndFlush(user);

        UUID agentId = UUID.randomUUID();
        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(agentId);
        agent.setWalletAddress(walletAddress);
        agent.setName("agent-" + nameSeed);
        agent.setSystemPrompt("Debate using deterministic structure and concise claims.");
        agent.setPersona("Prioritize objective evidence over style.");
        agent.setProviderType(ClawgicProviderType.MOCK);
        agent.setApiKeyEncrypted("enc:test");
        agent.setCreatedAt(OffsetDateTime.now());
        agent.setUpdatedAt(OffsetDateTime.now());
        clawgicAgentRepository.saveAndFlush(agent);
        return agentId;
    }

    private ClawgicMatch createMatch(
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            Integer bracketRound,
            Integer bracketPosition,
            ClawgicMatchStatus status
    ) {
        return createMatch(
                tournamentId,
                agent1Id,
                agent2Id,
                bracketRound,
                bracketPosition,
                status,
                null,
                null,
                null
        );
    }

    private ClawgicMatch createMatch(
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            Integer bracketRound,
            Integer bracketPosition,
            ClawgicMatchStatus status,
            UUID nextMatchId,
            Integer nextMatchAgentSlot,
            UUID winnerAgentId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicMatch match = new ClawgicMatch();
        match.setMatchId(UUID.randomUUID());
        match.setTournamentId(tournamentId);
        match.setAgent1Id(agent1Id);
        match.setAgent2Id(agent2Id);
        match.setBracketRound(bracketRound);
        match.setBracketPosition(bracketPosition);
        match.setNextMatchId(nextMatchId);
        match.setNextMatchAgentSlot(nextMatchAgentSlot);
        match.setStatus(status);
        match.setWinnerAgentId(winnerAgentId);
        match.setTranscriptJson(DebateTranscriptJsonCodec.emptyTranscript());
        match.setJudgeRetryCount(0);
        match.setCreatedAt(now.minusMinutes(2));
        match.setUpdatedAt(now.minusMinutes(2));

        if (status == ClawgicMatchStatus.FORFEITED) {
            match.setForfeitReason("TEST_FORFEIT");
            match.setForfeitedAt(now.minusMinutes(1));
        }
        if (status == ClawgicMatchStatus.COMPLETED) {
            match.setCompletedAt(now.minusMinutes(1));
            match.setJudgedAt(now.minusMinutes(1));
        }

        return clawgicMatchRepository.saveAndFlush(match);
    }

    private static String randomWalletAddress() {
        String hex = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        return "0x" + hex.substring(0, 40);
    }
}
