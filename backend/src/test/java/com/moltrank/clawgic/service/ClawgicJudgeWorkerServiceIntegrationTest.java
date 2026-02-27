package com.moltrank.clawgic.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchJudgement;
import com.moltrank.clawgic.model.ClawgicMatchJudgementStatus;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicProviderType;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import com.moltrank.clawgic.model.DebatePhase;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.model.DebateTranscriptMessage;
import com.moltrank.clawgic.model.DebateTranscriptRole;
import com.moltrank.clawgic.provider.MockClawgicJudgeProviderClient;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicMatchJudgementRepository;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import com.moltrank.clawgic.repository.ClawgicUserRepository;
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
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/moltrank}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:moltrank}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "moltrank.ingestion.enabled=false",
        "moltrank.ingestion.run-on-startup=false",
        "clawgic.enabled=true",
        "clawgic.worker.enabled=true",
        "clawgic.mock-judge=true",
        "clawgic.judge.enabled=true",
        "clawgic.judge.max-retries=1",
        "clawgic.judge.keys[0]=mock-judge-primary"
})
class ClawgicJudgeWorkerServiceIntegrationTest {

    private static final Duration DEFAULT_AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private ClawgicJudgeQueue clawgicJudgeQueue;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

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

        List<ClawgicMatchJudgement> judgements =
                clawgicMatchJudgementRepository.findByMatchIdOrderByCreatedAtAsc(pendingJudgeMatch.getMatchId());
        assertEquals(1, judgements.size());
        ClawgicMatchJudgement accepted = judgements.getFirst();
        assertEquals(ClawgicMatchJudgementStatus.ACCEPTED, accepted.getStatus());
        assertEquals(1, accepted.getAttempt());
        assertEquals("mock-judge-primary", accepted.getJudgeKey());
        assertEquals(completedMatch.getWinnerAgentId(), accepted.getWinnerAgentId());
        assertNotNull(accepted.getResultJson());
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

        Thread.sleep(400);
        assertEquals(
                2,
                clawgicMatchJudgementRepository.findByMatchIdOrderByCreatedAtAsc(pendingJudgeMatch.getMatchId()).size()
        );
        verify(mockClawgicJudgeProviderClient, times(2)).evaluate(any());
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
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicMatch match = new ClawgicMatch();
        match.setMatchId(UUID.randomUUID());
        match.setTournamentId(tournamentId);
        match.setAgent1Id(agent1Id);
        match.setAgent2Id(agent2Id);
        match.setBracketRound(1);
        match.setBracketPosition(1);
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

    private static String randomWalletAddress() {
        String hex = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        return "0x" + hex.substring(0, 40);
    }
}
