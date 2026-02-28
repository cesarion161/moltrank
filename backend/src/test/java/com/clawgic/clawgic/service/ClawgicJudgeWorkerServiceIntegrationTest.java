package com.clawgic.clawgic.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicMatch;
import com.clawgic.clawgic.model.ClawgicMatchJudgement;
import com.clawgic.clawgic.model.ClawgicMatchJudgementStatus;
import com.clawgic.clawgic.model.ClawgicMatchStatus;
import com.clawgic.clawgic.model.ClawgicProviderType;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.model.ClawgicUser;
import com.clawgic.clawgic.model.DebatePhase;
import com.clawgic.clawgic.model.DebateTranscriptJsonCodec;
import com.clawgic.clawgic.model.DebateTranscriptMessage;
import com.clawgic.clawgic.model.DebateTranscriptRole;
import com.clawgic.clawgic.provider.MockClawgicJudgeProviderClient;
import com.clawgic.clawgic.repository.ClawgicAgentEloRepository;
import com.clawgic.clawgic.repository.ClawgicAgentRepository;
import com.clawgic.clawgic.repository.ClawgicMatchJudgementRepository;
import com.clawgic.clawgic.repository.ClawgicMatchRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentRepository;
import com.clawgic.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/clawgic}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:clawgic}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "clawgic.enabled=true",
        "clawgic.worker.enabled=true",
        "clawgic.worker.queue-mode=in-memory",
        "clawgic.mock-judge=true",
        "clawgic.judge.enabled=true",
        "clawgic.judge.max-retries=1",
        "clawgic.judge.keys[0]=mock-judge-primary"
})
class ClawgicJudgeWorkerServiceIntegrationTest {

    private static final Duration DEFAULT_AWAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private ClawgicJudgeQueue clawgicJudgeQueue;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicAgentEloRepository clawgicAgentEloRepository;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicMatchRepository clawgicMatchRepository;

    @Autowired
    private ClawgicMatchJudgementRepository clawgicMatchJudgementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private MockClawgicJudgeProviderClient mockClawgicJudgeProviderClient;

    @BeforeEach
    void isolateClawgicData() {
        reset(mockClawgicJudgeProviderClient);
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    clawgic_match_judgements,
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
    void queueConsumerCompletesPendingJudgeMatchAndPersistsAcceptedVerdict() throws InterruptedException {
        UUID agent1Id = createUserAndAgent("Judge Worker Agent One");
        UUID agent2Id = createUserAndAgent("Judge Worker Agent Two");
        ClawgicTournament tournament = createTournament("Should queue workers complete matches in mock mode?");
        ClawgicMatch pendingJudgeMatch = createPendingJudgeMatch(tournament.getTournamentId(), agent1Id, agent2Id);

        clawgicJudgeQueue.enqueue(new ClawgicJudgeQueueMessage(pendingJudgeMatch.getMatchId(), "mock-judge-primary"));

        ClawgicMatch completedMatch = awaitMatch(
                pendingJudgeMatch.getMatchId(),
                persisted -> persisted.getStatus() == ClawgicMatchStatus.COMPLETED,
                DEFAULT_AWAIT_TIMEOUT
        );
        assertNotNull(completedMatch.getWinnerAgentId());
        assertNotNull(completedMatch.getJudgeResultJson());
        assertNotNull(completedMatch.getJudgedAt());
        assertNotNull(completedMatch.getCompletedAt());
        assertEquals(0, completedMatch.getJudgeRetryCount());
        assertEquals(1000, completedMatch.getAgent1EloBefore());
        assertEquals(1000, completedMatch.getAgent2EloBefore());
        assertTrue(completedMatch.getAgent1EloAfter() != null && completedMatch.getAgent1EloAfter() > 0);
        assertTrue(completedMatch.getAgent2EloAfter() != null && completedMatch.getAgent2EloAfter() > 0);

        List<ClawgicMatchJudgement> judgements =
                clawgicMatchJudgementRepository.findByMatchIdOrderByCreatedAtAsc(pendingJudgeMatch.getMatchId());
        assertEquals(1, judgements.size());
        ClawgicMatchJudgement accepted = judgements.getFirst();
        assertEquals(ClawgicMatchJudgementStatus.ACCEPTED, accepted.getStatus());
        assertEquals(1, accepted.getAttempt());
        assertEquals("mock-judge-primary", accepted.getJudgeKey());
        assertEquals(completedMatch.getWinnerAgentId(), accepted.getWinnerAgentId());
        assertNotNull(accepted.getResultJson());

        ClawgicAgentElo agentOneElo = clawgicAgentEloRepository.findById(agent1Id).orElseThrow();
        ClawgicAgentElo agentTwoElo = clawgicAgentEloRepository.findById(agent2Id).orElseThrow();
        ClawgicAgentElo winnerElo = completedMatch.getWinnerAgentId().equals(agent1Id) ? agentOneElo : agentTwoElo;
        ClawgicAgentElo loserElo = completedMatch.getWinnerAgentId().equals(agent1Id) ? agentTwoElo : agentOneElo;

        assertEquals(1016, winnerElo.getCurrentElo());
        assertEquals(984, loserElo.getCurrentElo());
        assertEquals(1, winnerElo.getMatchesPlayed());
        assertEquals(1, winnerElo.getMatchesWon());
        assertEquals(0, winnerElo.getMatchesForfeited());
        assertEquals(1, loserElo.getMatchesPlayed());
        assertEquals(0, loserElo.getMatchesWon());
        assertEquals(0, loserElo.getMatchesForfeited());
    }

    @Test
    void invalidJudgeJsonRetriesUntilCapThenStops() throws InterruptedException {
        UUID agent1Id = createUserAndAgent("Judge Retry Agent One");
        UUID agent2Id = createUserAndAgent("Judge Retry Agent Two");
        ClawgicTournament tournament = createTournament("Should invalid judge JSON stop after retry cap?");
        ClawgicMatch pendingJudgeMatch = createPendingJudgeMatch(tournament.getTournamentId(), agent1Id, agent2Id);

        doAnswer(ignored -> JsonNodeFactory.instance.objectNode().put("winner_id", "not-a-valid-uuid"))
                .when(mockClawgicJudgeProviderClient)
                .evaluate(any());

        clawgicJudgeQueue.enqueue(new ClawgicJudgeQueueMessage(pendingJudgeMatch.getMatchId(), "mock-judge-primary"));

        awaitCondition(
                () -> clawgicMatchJudgementRepository.findByMatchIdOrderByCreatedAtAsc(
                        pendingJudgeMatch.getMatchId()
                ).size() == 2,
                DEFAULT_AWAIT_TIMEOUT
        );

        ClawgicMatch persistedMatch = clawgicMatchRepository.findById(pendingJudgeMatch.getMatchId()).orElseThrow();
        assertEquals(ClawgicMatchStatus.PENDING_JUDGE, persistedMatch.getStatus());
        assertEquals(2, persistedMatch.getJudgeRetryCount());
        assertNull(persistedMatch.getWinnerAgentId());

        List<ClawgicMatchJudgement> judgements =
                clawgicMatchJudgementRepository.findByMatchIdOrderByCreatedAtAsc(pendingJudgeMatch.getMatchId());
        assertEquals(2, judgements.size());
        assertEquals(List.of(1, 2), judgements.stream().map(ClawgicMatchJudgement::getAttempt).toList());
        assertTrue(judgements.stream().allMatch(judgement -> judgement.getStatus() == ClawgicMatchJudgementStatus.REJECTED));
        assertTrue(judgements.stream().allMatch(judgement -> judgement.getResultJson().has("error_code")));

        ClawgicAgentElo agentOneElo = clawgicAgentEloRepository.findById(agent1Id).orElseThrow();
        ClawgicAgentElo agentTwoElo = clawgicAgentEloRepository.findById(agent2Id).orElseThrow();
        assertEquals(1000, agentOneElo.getCurrentElo());
        assertEquals(1000, agentTwoElo.getCurrentElo());
        assertEquals(0, agentOneElo.getMatchesPlayed());
        assertEquals(0, agentTwoElo.getMatchesPlayed());

        Thread.sleep(400);
        assertEquals(
                2,
                clawgicMatchJudgementRepository.findByMatchIdOrderByCreatedAtAsc(pendingJudgeMatch.getMatchId()).size()
        );
        verify(mockClawgicJudgeProviderClient, times(2)).evaluate(any());
    }

    @Test
    void queueConsumerCompletesTournamentWhenFinalMatchVerdictIsAccepted() throws InterruptedException {
        UUID semifinalOneWinner = createUserAndAgent("C29 semifinal one winner");
        UUID semifinalOneLoser = createUserAndAgent("C29 semifinal one loser");
        UUID semifinalTwoWinner = createUserAndAgent("C29 semifinal two winner");
        UUID semifinalTwoLoser = createUserAndAgent("C29 semifinal two loser");
        ClawgicTournament tournament = createTournament("Should final judgement complete the full tournament?");

        UUID finalMatchId = UUID.randomUUID();
        ClawgicMatch finalPendingJudgeMatch = createPendingJudgeMatch(
                tournament.getTournamentId(),
                semifinalOneWinner,
                semifinalTwoWinner,
                finalMatchId,
                2,
                1
        );
        createResolvedMatch(
                tournament.getTournamentId(),
                semifinalOneWinner,
                semifinalOneLoser,
                1,
                1,
                ClawgicMatchStatus.COMPLETED,
                finalMatchId,
                1,
                semifinalOneWinner
        );
        createResolvedMatch(
                tournament.getTournamentId(),
                semifinalTwoWinner,
                semifinalTwoLoser,
                1,
                2,
                ClawgicMatchStatus.FORFEITED,
                finalMatchId,
                2,
                semifinalTwoWinner
        );

        clawgicJudgeQueue.enqueue(new ClawgicJudgeQueueMessage(finalPendingJudgeMatch.getMatchId(), "mock-judge-primary"));

        ClawgicTournament completedTournament = awaitTournament(
                tournament.getTournamentId(),
                persisted -> persisted.getStatus() == ClawgicTournamentStatus.COMPLETED,
                DEFAULT_AWAIT_TIMEOUT
        );
        ClawgicMatch completedFinal = clawgicMatchRepository.findById(finalPendingJudgeMatch.getMatchId()).orElseThrow();

        assertEquals(ClawgicTournamentStatus.COMPLETED, completedTournament.getStatus());
        assertNotNull(completedTournament.getCompletedAt());
        assertEquals(completedFinal.getWinnerAgentId(), completedTournament.getWinnerAgentId());
        assertEquals(2, completedTournament.getMatchesCompleted());
        assertEquals(1, completedTournament.getMatchesForfeited());
    }

    private ClawgicMatch awaitMatch(
            UUID matchId,
            Predicate<ClawgicMatch> condition,
            Duration timeout
    ) throws InterruptedException {
        OffsetDateTime deadline = OffsetDateTime.now().plus(timeout);
        while (OffsetDateTime.now().isBefore(deadline)) {
            ClawgicMatch match = clawgicMatchRepository.findById(matchId).orElseThrow();
            if (condition.test(match)) {
                return match;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for match condition to pass for " + matchId);
    }

    private ClawgicTournament awaitTournament(
            UUID tournamentId,
            Predicate<ClawgicTournament> condition,
            Duration timeout
    ) throws InterruptedException {
        OffsetDateTime deadline = OffsetDateTime.now().plus(timeout);
        while (OffsetDateTime.now().isBefore(deadline)) {
            ClawgicTournament tournament = clawgicTournamentRepository.findById(tournamentId).orElseThrow();
            if (condition.test(tournament)) {
                return tournament;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for tournament condition to pass for " + tournamentId);
    }

    private void awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        OffsetDateTime deadline = OffsetDateTime.now().plus(timeout);
        while (OffsetDateTime.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for async condition");
    }

    private UUID createUserAndAgent(String agentName) {
        String walletAddress = randomWalletAddress();
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        clawgicUserRepository.saveAndFlush(user);

        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(UUID.randomUUID());
        agent.setWalletAddress(walletAddress);
        agent.setName(agentName);
        agent.setProviderType(ClawgicProviderType.MOCK);
        agent.setSystemPrompt("Debate with concise and testable claims.");
        agent.setApiKeyEncrypted("enc:test");
        agent.setCreatedAt(OffsetDateTime.now());
        agent.setUpdatedAt(OffsetDateTime.now());
        clawgicAgentRepository.saveAndFlush(agent);

        ClawgicAgentElo agentElo = new ClawgicAgentElo();
        agentElo.setAgentId(agent.getAgentId());
        agentElo.setCurrentElo(1000);
        agentElo.setMatchesPlayed(0);
        agentElo.setMatchesWon(0);
        agentElo.setMatchesForfeited(0);
        agentElo.setLastUpdated(OffsetDateTime.now());
        clawgicAgentEloRepository.saveAndFlush(agentElo);

        return agent.getAgentId();
    }

    private ClawgicTournament createTournament(String topic) {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(topic);
        tournament.setStatus(ClawgicTournamentStatus.IN_PROGRESS);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(4);
        tournament.setStartTime(now.minusMinutes(3));
        tournament.setEntryCloseTime(now.minusMinutes(63));
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setStartedAt(now.minusMinutes(2));
        tournament.setCreatedAt(now.minusHours(1));
        tournament.setUpdatedAt(now.minusHours(1));
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }

    private ClawgicMatch createPendingJudgeMatch(UUID tournamentId, UUID agent1Id, UUID agent2Id) {
        return createPendingJudgeMatch(tournamentId, agent1Id, agent2Id, UUID.randomUUID(), 1, 1);
    }

    private ClawgicMatch createPendingJudgeMatch(
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            UUID matchId,
            int bracketRound,
            int bracketPosition
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicMatch match = new ClawgicMatch();
        match.setMatchId(matchId);
        match.setTournamentId(tournamentId);
        match.setAgent1Id(agent1Id);
        match.setAgent2Id(agent2Id);
        match.setBracketRound(bracketRound);
        match.setBracketPosition(bracketPosition);
        match.setStatus(ClawgicMatchStatus.PENDING_JUDGE);
        match.setPhase(DebatePhase.CONCLUSION);
        match.setTranscriptJson(DebateTranscriptJsonCodec.toJson(List.of(
                new DebateTranscriptMessage(
                        DebateTranscriptRole.AGENT_1,
                        DebatePhase.THESIS_DISCOVERY,
                        "Deterministic systems improve reproducibility and observability."
                ),
                new DebateTranscriptMessage(
                        DebateTranscriptRole.AGENT_2,
                        DebatePhase.THESIS_DISCOVERY,
                        "Live variability can reveal robustness gaps that deterministic fixtures miss."
                )
        )));
        match.setJudgeRetryCount(0);
        match.setJudgeRequestedAt(now.minusSeconds(20));
        match.setCreatedAt(now.minusMinutes(3));
        match.setUpdatedAt(now.minusMinutes(1));
        return clawgicMatchRepository.saveAndFlush(match);
    }

    private ClawgicMatch createResolvedMatch(
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            int bracketRound,
            int bracketPosition,
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
        match.setPhase(DebatePhase.CONCLUSION);
        match.setTranscriptJson(DebateTranscriptJsonCodec.toJson(List.of()));
        match.setJudgeRetryCount(0);
        match.setStartedAt(now.minusMinutes(5));
        match.setCreatedAt(now.minusMinutes(6));
        match.setUpdatedAt(now.minusMinutes(4));

        if (status == ClawgicMatchStatus.COMPLETED) {
            match.setJudgedAt(now.minusMinutes(4));
            match.setCompletedAt(now.minusMinutes(4));
            match.setJudgeResultJson(JsonNodeFactory.instance.objectNode().put("winner_id", winnerAgentId.toString()));
        } else if (status == ClawgicMatchStatus.FORFEITED) {
            match.setForfeitReason("TEST_FORFEIT");
            match.setForfeitedAt(now.minusMinutes(4));
        }

        return clawgicMatchRepository.saveAndFlush(match);
    }

    private static String randomWalletAddress() {
        String hex = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        return "0x" + hex.substring(0, 40);
    }
}
