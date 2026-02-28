package com.clawgic.clawgic.service;

import com.clawgic.clawgic.dto.ClawgicAgentRequests;
import com.clawgic.clawgic.dto.ClawgicAgentResponses;
import com.clawgic.clawgic.dto.ClawgicTournamentRequests;
import com.clawgic.clawgic.dto.ClawgicTournamentResponses;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicMatch;
import com.clawgic.clawgic.model.ClawgicMatchJudgement;
import com.clawgic.clawgic.model.ClawgicMatchJudgementStatus;
import com.clawgic.clawgic.model.ClawgicMatchStatus;
import com.clawgic.clawgic.model.ClawgicProviderType;
import com.clawgic.clawgic.model.ClawgicStakingLedger;
import com.clawgic.clawgic.model.ClawgicStakingLedgerStatus;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.provider.ClawgicProviderTurnRequest;
import com.clawgic.clawgic.provider.MockClawgicDebateProviderClient;
import com.clawgic.clawgic.repository.ClawgicAgentEloRepository;
import com.clawgic.clawgic.repository.ClawgicMatchJudgementRepository;
import com.clawgic.clawgic.repository.ClawgicMatchRepository;
import com.clawgic.clawgic.repository.ClawgicStakingLedgerRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

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
        "clawgic.worker.queue-mode=redis",
        "clawgic.worker.redis-queue-key=clawgic:test:c54:e2e",
        "clawgic.worker.redis-pop-timeout-seconds=1",
        "clawgic.worker.poll-interval-ms=600000",
        "clawgic.worker.initial-delay-ms=600000",
        "clawgic.mock-provider=true",
        "clawgic.mock-judge=true",
        "clawgic.judge.enabled=true",
        "clawgic.judge.max-retries=1",
        "clawgic.judge.keys[0]=mock-judge-primary",
        "x402.enabled=false",
        "x402.dev-bypass-enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
class ClawgicMvpEndToEndIntegrationTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final String REDIS_QUEUE_KEY = "clawgic:test:c54:e2e";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private ClawgicAgentService clawgicAgentService;

    @Autowired
    private ClawgicTournamentService clawgicTournamentService;

    @Autowired
    private ClawgicMatchLifecycleService clawgicMatchLifecycleService;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicMatchRepository clawgicMatchRepository;

    @Autowired
    private ClawgicMatchJudgementRepository clawgicMatchJudgementRepository;

    @Autowired
    private ClawgicAgentEloRepository clawgicAgentEloRepository;

    @Autowired
    private ClawgicStakingLedgerRepository clawgicStakingLedgerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClawgicJudgeQueue clawgicJudgeQueue;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockitoSpyBean
    private MockClawgicDebateProviderClient mockClawgicDebateProviderClient;

    @BeforeEach
    void isolateClawgicData() {
        assertInstanceOf(RedisClawgicJudgeQueue.class, clawgicJudgeQueue);
        reset(mockClawgicDebateProviderClient);
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
        stringRedisTemplate.delete(REDIS_QUEUE_KEY);
        assertEquals(0L, resolveRedisQueueDepth());
        assertFalse(resolveRedisFallbackMode(), "Redis queue unexpectedly entered fallback mode before test start");
    }

    @Test
    void fullLoopCompletesTournamentWithJudgementsEloAndSettlement() throws InterruptedException {
        TournamentFixture fixture = createTournamentFixture(
                "C54 full-loop success path",
                List.of(1260, 1180, 1090, 980)
        );

        ClawgicTournament completedTournament = awaitTournamentCompletion(fixture.tournamentId(), DEFAULT_TIMEOUT);
        List<ClawgicMatch> persistedMatches =
                clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(
                        fixture.tournamentId()
                );
        List<ClawgicMatchJudgement> persistedJudgements =
                clawgicMatchJudgementRepository.findByTournamentIdOrderByMatchIdAscAttemptAscCreatedAtAsc(
                        fixture.tournamentId()
                );
        List<ClawgicAgentElo> persistedElos = clawgicAgentEloRepository.findAllById(fixture.agentIds());
        List<ClawgicStakingLedger> persistedLedgers =
                clawgicStakingLedgerRepository.findByTournamentIdOrderByCreatedAtAsc(fixture.tournamentId());

        assertEquals(ClawgicTournamentStatus.COMPLETED, completedTournament.getStatus());
        assertNotNull(completedTournament.getWinnerAgentId());
        assertNotNull(completedTournament.getCompletedAt());
        assertEquals(3, persistedMatches.size());
        assertTrue(persistedMatches.stream().allMatch(match -> match.getStatus() == ClawgicMatchStatus.COMPLETED));
        assertTrue(persistedMatches.stream().allMatch(match -> match.getWinnerAgentId() != null));
        assertEquals(3, persistedJudgements.size());
        assertTrue(persistedJudgements.stream().allMatch(judgement ->
                judgement.getStatus() == ClawgicMatchJudgementStatus.ACCEPTED
                        && judgement.getWinnerAgentId() != null
                        && judgement.getResultJson() != null
        ));

        assertEquals(4, persistedElos.size());
        int totalMatchesPlayed = persistedElos.stream().mapToInt(elo -> safeInt(elo.getMatchesPlayed())).sum();
        int totalMatchesWon = persistedElos.stream().mapToInt(elo -> safeInt(elo.getMatchesWon())).sum();
        assertEquals(6, totalMatchesPlayed);
        assertEquals(3, totalMatchesWon);
        assertTrue(persistedElos.stream().allMatch(elo -> elo.getCurrentElo() != null && elo.getCurrentElo() > 0));

        assertEquals(4, persistedLedgers.size());
        assertTrue(persistedLedgers.stream().allMatch(ledger -> ledger.getSettledAt() != null));
        assertTrue(persistedLedgers.stream().allMatch(ledger -> ledger.getStatus() == ClawgicStakingLedgerStatus.SETTLED));

        List<ClawgicStakingLedger> payoutLedgers = persistedLedgers.stream()
                .filter(ledger -> ledger.getRewardPayout().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        assertEquals(1, payoutLedgers.size());
        assertEquals(completedTournament.getWinnerAgentId(), payoutLedgers.getFirst().getAgentId());
        assertEquals(sumBy(persistedLedgers, ClawgicStakingLedger::getAmountStaked), sumSettlementColumns(persistedLedgers));
        assertFalse(resolveRedisFallbackMode(), "Redis queue unexpectedly fell back to in-memory mode");
        assertEquals(0L, resolveRedisQueueDepth());
    }

    @Test
    void fullLoopCompletesWhenOneSemifinalForfeitsOnMockAuthFailure() throws InterruptedException {
        TournamentFixture fixture = createTournamentFixture(
                "C54 full-loop forfeit path",
                List.of(1320, 1200, 1080, 990)
        );
        UUID failingAgentId = fixture.agentIds().getFirst();

        doAnswer(invocation -> {
            ClawgicProviderTurnRequest request = invocation.getArgument(0);
            if (failingAgentId.equals(request.agentId())) {
                throw new IllegalStateException("401 unauthorized: invalid api key");
            }
            return invocation.callRealMethod();
        }).when(mockClawgicDebateProviderClient).generateTurn(any());

        ClawgicTournament completedTournament = awaitTournamentCompletion(fixture.tournamentId(), DEFAULT_TIMEOUT);
        List<ClawgicMatch> persistedMatches =
                clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(
                        fixture.tournamentId()
                );
        List<ClawgicMatchJudgement> persistedJudgements =
                clawgicMatchJudgementRepository.findByTournamentIdOrderByMatchIdAscAttemptAscCreatedAtAsc(
                        fixture.tournamentId()
                );
        List<ClawgicStakingLedger> persistedLedgers =
                clawgicStakingLedgerRepository.findByTournamentIdOrderByCreatedAtAsc(fixture.tournamentId());

        assertEquals(ClawgicTournamentStatus.COMPLETED, completedTournament.getStatus());
        assertNotNull(completedTournament.getWinnerAgentId());
        assertEquals(3, persistedMatches.size());

        List<ClawgicMatch> forfeitedMatches = persistedMatches.stream()
                .filter(match -> match.getStatus() == ClawgicMatchStatus.FORFEITED)
                .toList();
        assertEquals(1, forfeitedMatches.size());
        ClawgicMatch forfeited = forfeitedMatches.getFirst();
        assertTrue(forfeited.getForfeitReason().contains("PROVIDER_AUTH_FAILURE"));
        assertTrue(forfeited.getForfeitReason().contains(failingAgentId.toString()));
        assertEquals(2, completedTournament.getMatchesCompleted());
        assertEquals(1, completedTournament.getMatchesForfeited());

        assertEquals(2, persistedJudgements.size());
        assertTrue(persistedJudgements.stream().allMatch(judgement ->
                judgement.getStatus() == ClawgicMatchJudgementStatus.ACCEPTED
        ));

        assertEquals(4, persistedLedgers.size());
        ClawgicStakingLedger forfeitedLedger = persistedLedgers.stream()
                .filter(ledger -> failingAgentId.equals(ledger.getAgentId()))
                .findFirst()
                .orElseThrow();
        assertEquals(ClawgicStakingLedgerStatus.FORFEITED, forfeitedLedger.getStatus());
        assertEquals(new BigDecimal("0.000000"), forfeitedLedger.getRewardPayout());
        assertNotNull(forfeitedLedger.getForfeitedAt());
        assertFalse(resolveRedisFallbackMode(), "Redis queue unexpectedly fell back to in-memory mode");
        assertEquals(0L, resolveRedisQueueDepth());
    }

    private TournamentFixture createTournamentFixture(String topic, List<Integer> seedElos) {
        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournamentResponses.TournamentDetail tournament = clawgicTournamentService.createTournament(
                new ClawgicTournamentRequests.CreateTournamentRequest(
                        topic,
                        now.plusMinutes(15),
                        now.plusMinutes(10),
                        new BigDecimal("5.000000")
                )
        );

        List<UUID> agentIds = new ArrayList<>();
        for (int i = 0; i < seedElos.size(); i++) {
            UUID agentId = createAgent("c54-agent-" + (i + 1), i + 1);
            updateAgentElo(agentId, seedElos.get(i));
            clawgicTournamentService.enterTournament(
                    tournament.tournamentId(),
                    new ClawgicTournamentRequests.EnterTournamentRequest(agentId)
            );
            agentIds.add(agentId);
        }
        clawgicTournamentService.createMvpBracket(tournament.tournamentId());
        forceTournamentDue(tournament.tournamentId());
        return new TournamentFixture(tournament.tournamentId(), List.copyOf(agentIds));
    }

    private UUID createAgent(String agentName, int walletSuffix) {
        ClawgicAgentResponses.AgentDetail agent = clawgicAgentService.createAgent(
                new ClawgicAgentRequests.CreateAgentRequest(
                        walletAddress(walletSuffix),
                        agentName,
                        null,
                        "Debate in concise, falsifiable claims.",
                        null,
                        "Prioritize reproducibility over style.",
                        null,
                        ClawgicProviderType.MOCK,
                        null,
                        "api-key-" + walletSuffix
                )
        );
        return agent.agentId();
    }

    private void updateAgentElo(UUID agentId, int elo) {
        ClawgicAgentElo agentElo = clawgicAgentEloRepository.findById(agentId).orElseThrow();
        agentElo.setCurrentElo(elo);
        agentElo.setLastUpdated(OffsetDateTime.now());
        clawgicAgentEloRepository.saveAndFlush(agentElo);
    }

    private void forceTournamentDue(UUID tournamentId) {
        ClawgicTournament tournament = clawgicTournamentRepository.findById(tournamentId).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        tournament.setStartTime(now.minusMinutes(1));
        tournament.setEntryCloseTime(now.minusMinutes(2));
        tournament.setUpdatedAt(now);
        clawgicTournamentRepository.saveAndFlush(tournament);
    }

    private ClawgicTournament awaitTournamentCompletion(UUID tournamentId, Duration timeout) throws InterruptedException {
        OffsetDateTime deadline = OffsetDateTime.now().plus(timeout);
        while (OffsetDateTime.now().isBefore(deadline)) {
            clawgicMatchLifecycleService.processLifecycleTick();
            ClawgicTournament tournament = clawgicTournamentRepository.findById(tournamentId).orElseThrow();
            if (tournament.getStatus() == ClawgicTournamentStatus.COMPLETED) {
                return tournament;
            }
            Thread.sleep(50);
        }
        ClawgicTournament persisted = clawgicTournamentRepository.findById(tournamentId).orElseThrow();
        List<ClawgicMatch> matches =
                clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(tournamentId);
        String statusSummary = matches.stream()
                .sorted(Comparator.comparing(ClawgicMatch::getBracketRound).thenComparing(ClawgicMatch::getBracketPosition))
                .map(match -> match.getMatchId() + ":" + match.getStatus())
                .toList()
                .toString();
        throw new AssertionError(
                "Timed out waiting for tournament completion: tournamentStatus="
                        + persisted.getStatus()
                        + ", matches="
                        + statusSummary
        );
    }

    private static String walletAddress(int suffix) {
        return "0x%040x".formatted(suffix);
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal sumBy(
            List<ClawgicStakingLedger> ledgers,
            java.util.function.Function<ClawgicStakingLedger, BigDecimal> selector
    ) {
        BigDecimal total = BigDecimal.ZERO.setScale(6);
        for (ClawgicStakingLedger ledger : ledgers) {
            total = total.add(selector.apply(ledger));
        }
        return total.setScale(6);
    }

    private static BigDecimal sumSettlementColumns(List<ClawgicStakingLedger> ledgers) {
        BigDecimal total = BigDecimal.ZERO.setScale(6);
        for (ClawgicStakingLedger ledger : ledgers) {
            total = total
                    .add(ledger.getJudgeFeeDeducted())
                    .add(ledger.getSystemRetention())
                    .add(ledger.getRewardPayout());
        }
        return total.setScale(6);
    }

    private boolean resolveRedisFallbackMode() {
        RedisClawgicJudgeQueue redisQueue = assertInstanceOf(RedisClawgicJudgeQueue.class, clawgicJudgeQueue);
        Boolean fallbackMode = (Boolean) ReflectionTestUtils.getField(redisQueue, "fallbackMode");
        return Boolean.TRUE.equals(fallbackMode);
    }

    private long resolveRedisQueueDepth() {
        Long depth = stringRedisTemplate.opsForList().size(REDIS_QUEUE_KEY);
        return depth == null ? 0L : depth;
    }

    private record TournamentFixture(
            UUID tournamentId,
            List<UUID> agentIds
    ) {
    }
}
