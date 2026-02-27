package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicJudgeProperties;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicProviderType;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import com.moltrank.clawgic.model.DebatePhase;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.model.DebateTranscriptMessage;
import com.moltrank.clawgic.model.DebateTranscriptRole;
import com.moltrank.clawgic.provider.ClawgicProviderTurnRequest;
import com.moltrank.clawgic.provider.MockClawgicDebateProviderClient;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import com.moltrank.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
        "clawgic.mock-provider=true",
        "clawgic.debate.provider-timeout-seconds=1",
        "clawgic.judge.keys[0]=mock-judge-primary",
        "clawgic.judge.keys[1]=mock-judge-secondary"
})
class ClawgicDebateExecutionServiceIntegrationTest {

    @Autowired
    private ClawgicDebateExecutionService clawgicDebateExecutionService;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicMatchRepository clawgicMatchRepository;

    @Autowired
    private ClawgicJudgeQueue clawgicJudgeQueue;

    @Autowired
    private ClawgicJudgeProperties clawgicJudgeProperties;

    @MockitoSpyBean
    private MockClawgicDebateProviderClient mockClawgicDebateProviderClient;

    @BeforeEach
    void resetSpies() {
        reset(mockClawgicDebateProviderClient);
        clawgicJudgeQueue.setConsumer(_ -> {
        });
    }

    @Test
    void executeMatchPersistsTranscriptAfterEachTurnAndTransitionsToPendingJudge() {
        String walletOne = randomWalletAddress();
        String walletTwo = randomWalletAddress();
        createUser(walletOne);
        createUser(walletTwo);

        UUID agent1Id = createAgent(walletOne, "Execution Agent One");
        UUID agent2Id = createAgent(walletTwo, "Execution Agent Two");
        ClawgicTournament tournament = createTournament("Should deterministic mocks be mandatory for MVP demo runs?");
        ClawgicMatch scheduledMatch = createScheduledMatch(tournament.getTournamentId(), agent1Id, agent2Id);

        List<Integer> transcriptSizesBeforeProviderTurns = new ArrayList<>();
        doAnswer(invocation -> {
            ClawgicProviderTurnRequest providerTurnRequest = invocation.getArgument(0);
            ClawgicMatch persistedMatch = clawgicMatchRepository.findById(providerTurnRequest.matchId()).orElseThrow();
            transcriptSizesBeforeProviderTurns.add(
                    DebateTranscriptJsonCodec.fromJson(persistedMatch.getTranscriptJson()).size()
            );
            return invocation.callRealMethod();
        }).when(mockClawgicDebateProviderClient).generateTurn(any());

        ClawgicMatch executedMatch = clawgicDebateExecutionService.executeMatch(scheduledMatch.getMatchId());
        ClawgicMatch persistedMatch = clawgicMatchRepository.findById(executedMatch.getMatchId()).orElseThrow();

        assertEquals(ClawgicMatchStatus.PENDING_JUDGE, persistedMatch.getStatus());
        assertEquals(DebatePhase.CONCLUSION, persistedMatch.getPhase());
        assertNotNull(persistedMatch.getStartedAt());
        assertNotNull(persistedMatch.getExecutionDeadlineAt());
        assertNotNull(persistedMatch.getJudgeRequestedAt());

        List<DebateTranscriptMessage> transcriptMessages =
                DebateTranscriptJsonCodec.fromJson(persistedMatch.getTranscriptJson());
        int expectedTurns = DebatePhase.orderedValues().size() * 2;
        assertEquals(expectedTurns, transcriptMessages.size());
        assertEquals(
                IntStream.range(0, expectedTurns).boxed().toList(),
                transcriptSizesBeforeProviderTurns
        );

        for (int phaseIndex = 0; phaseIndex < DebatePhase.orderedValues().size(); phaseIndex++) {
            DebatePhase phase = DebatePhase.orderedValues().get(phaseIndex);
            DebateTranscriptMessage firstTurn = transcriptMessages.get(phaseIndex * 2);
            DebateTranscriptMessage secondTurn = transcriptMessages.get((phaseIndex * 2) + 1);
            assertEquals(DebateTranscriptRole.AGENT_1, firstTurn.role());
            assertEquals(phase, firstTurn.phase());
            assertEquals(DebateTranscriptRole.AGENT_2, secondTurn.role());
            assertEquals(phase, secondTurn.phase());
        }
    }

    @Test
    void executeMatchMarksMatchForfeitedWhenProviderTurnTimesOut() {
        String walletOne = randomWalletAddress();
        String walletTwo = randomWalletAddress();
        createUser(walletOne);
        createUser(walletTwo);

        UUID agent1Id = createAgent(walletOne, "Timeout Agent One");
        UUID agent2Id = createAgent(walletTwo, "Timeout Agent Two");
        ClawgicTournament tournament = createTournament("Should timeout failures forfeit immediately?");
        ClawgicMatch scheduledMatch = createScheduledMatch(tournament.getTournamentId(), agent1Id, agent2Id);

        doAnswer(invocation -> {
            Thread.sleep(1500);
            return invocation.callRealMethod();
        }).when(mockClawgicDebateProviderClient).generateTurn(any());

        ClawgicMatch forfeitedMatch = clawgicDebateExecutionService.executeMatch(scheduledMatch.getMatchId());
        ClawgicMatch persistedMatch = clawgicMatchRepository.findById(forfeitedMatch.getMatchId()).orElseThrow();

        assertEquals(ClawgicMatchStatus.FORFEITED, persistedMatch.getStatus());
        assertEquals(DebatePhase.THESIS_DISCOVERY, persistedMatch.getPhase());
        assertEquals(agent2Id, persistedMatch.getWinnerAgentId());
        assertNotNull(persistedMatch.getForfeitedAt());
        assertNull(persistedMatch.getJudgeRequestedAt());
        assertTrue(persistedMatch.getForfeitReason().contains("PROVIDER_TIMEOUT"));
        assertTrue(persistedMatch.getForfeitReason().contains("failing_agent_id=" + agent1Id));
        assertEquals(0, DebateTranscriptJsonCodec.fromJson(persistedMatch.getTranscriptJson()).size());
        verify(mockClawgicDebateProviderClient, times(1)).generateTurn(any());
    }

    @Test
    void executeMatchMarksMatchForfeitedWhenProviderAuthFails() {
        String walletOne = randomWalletAddress();
        String walletTwo = randomWalletAddress();
        createUser(walletOne);
        createUser(walletTwo);

        UUID agent1Id = createAgent(walletOne, "Auth Fail Agent One");
        UUID agent2Id = createAgent(walletTwo, "Auth Fail Agent Two");
        ClawgicTournament tournament = createTournament("Should invalid keys map to deterministic forfeits?");
        ClawgicMatch scheduledMatch = createScheduledMatch(tournament.getTournamentId(), agent1Id, agent2Id);

        doThrow(new IllegalStateException("401 unauthorized: invalid api key"))
                .when(mockClawgicDebateProviderClient)
                .generateTurn(any());

        ClawgicMatch forfeitedMatch = clawgicDebateExecutionService.executeMatch(scheduledMatch.getMatchId());
        ClawgicMatch persistedMatch = clawgicMatchRepository.findById(forfeitedMatch.getMatchId()).orElseThrow();

        assertEquals(ClawgicMatchStatus.FORFEITED, persistedMatch.getStatus());
        assertEquals(agent2Id, persistedMatch.getWinnerAgentId());
        assertNotNull(persistedMatch.getForfeitedAt());
        assertNull(persistedMatch.getJudgeRequestedAt());
        assertTrue(persistedMatch.getForfeitReason().contains("PROVIDER_AUTH_FAILURE"));
        assertTrue(persistedMatch.getForfeitReason().contains("failing_agent_id=" + agent1Id));
        assertEquals(0, DebateTranscriptJsonCodec.fromJson(persistedMatch.getTranscriptJson()).size());
        verify(mockClawgicDebateProviderClient, times(1)).generateTurn(any());
    }

    @Test
    void executeMatchMarksMatchForfeitedWhenProviderThrowsGenericErrorAndSkipsJudgeRequest() {
        String walletOne = randomWalletAddress();
        String walletTwo = randomWalletAddress();
        createUser(walletOne);
        createUser(walletTwo);

        UUID agent1Id = createAgent(walletOne, "Error Agent One");
        UUID agent2Id = createAgent(walletTwo, "Error Agent Two");
        ClawgicTournament tournament = createTournament("Should generic provider errors skip judge pipeline?");
        ClawgicMatch scheduledMatch = createScheduledMatch(tournament.getTournamentId(), agent1Id, agent2Id);

        doThrow(new IllegalStateException("provider blew up"))
                .when(mockClawgicDebateProviderClient)
                .generateTurn(any());

        ClawgicMatch forfeitedMatch = clawgicDebateExecutionService.executeMatch(scheduledMatch.getMatchId());
        ClawgicMatch persistedMatch = clawgicMatchRepository.findById(forfeitedMatch.getMatchId()).orElseThrow();

        assertEquals(ClawgicMatchStatus.FORFEITED, persistedMatch.getStatus());
        assertEquals(agent2Id, persistedMatch.getWinnerAgentId());
        assertNotNull(persistedMatch.getForfeitedAt());
        assertNull(persistedMatch.getJudgeRequestedAt());
        assertTrue(persistedMatch.getForfeitReason().contains("PROVIDER_ERROR"));
        assertTrue(persistedMatch.getForfeitReason().contains("failing_agent_id=" + agent1Id));
        assertEquals(0, DebateTranscriptJsonCodec.fromJson(persistedMatch.getTranscriptJson()).size());
        verify(mockClawgicDebateProviderClient, times(1)).generateTurn(any());
    }

    @Test
    void executeMatchPublishesJudgeQueueMessagesForEachConfiguredJudgeKey() throws InterruptedException {
        String walletOne = randomWalletAddress();
        String walletTwo = randomWalletAddress();
        createUser(walletOne);
        createUser(walletTwo);

        UUID agent1Id = createAgent(walletOne, "Queue Agent One");
        UUID agent2Id = createAgent(walletTwo, "Queue Agent Two");
        ClawgicTournament tournament = createTournament("Should queue publish one event per configured judge?");
        ClawgicMatch scheduledMatch = createScheduledMatch(tournament.getTournamentId(), agent1Id, agent2Id);

        CountDownLatch latch = new CountDownLatch(clawgicJudgeProperties.getKeys().size());
        List<ClawgicJudgeQueueMessage> receivedMessages = new CopyOnWriteArrayList<>();
        clawgicJudgeQueue.setConsumer(message -> {
            if (!scheduledMatch.getMatchId().equals(message.matchId())) {
                return;
            }
            receivedMessages.add(message);
            latch.countDown();
        });

        clawgicDebateExecutionService.executeMatch(scheduledMatch.getMatchId());

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(clawgicJudgeProperties.getKeys().size(), receivedMessages.size());
        assertEquals(
                clawgicJudgeProperties.getKeys().stream().sorted().toList(),
                receivedMessages.stream().map(ClawgicJudgeQueueMessage::judgeKey).sorted().toList()
        );
        assertTrue(receivedMessages.stream().allMatch(message -> scheduledMatch.getMatchId().equals(message.matchId())));
    }

    private void createUser(String walletAddress) {
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        clawgicUserRepository.saveAndFlush(user);
    }

    private UUID createAgent(String walletAddress, String name) {
        ClawgicAgent agent = new ClawgicAgent();
        UUID agentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        agent.setAgentId(agentId);
        agent.setWalletAddress(walletAddress);
        agent.setName(name);
        agent.setSystemPrompt("Argue in concise and falsifiable claims.");
        agent.setPersona("Prioritize reliability and deterministic outcomes.");
        agent.setProviderType(ClawgicProviderType.MOCK);
        agent.setApiKeyEncrypted("enc:test");
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        clawgicAgentRepository.saveAndFlush(agent);
        return agentId;
    }

    private ClawgicTournament createTournament(String topic) {
        ClawgicTournament tournament = new ClawgicTournament();
        OffsetDateTime now = OffsetDateTime.now();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(topic);
        tournament.setStatus(ClawgicTournamentStatus.LOCKED);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(4);
        tournament.setStartTime(now.minusMinutes(5));
        tournament.setEntryCloseTime(now.minusMinutes(65));
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setCreatedAt(now.minusHours(1));
        tournament.setUpdatedAt(now.minusHours(1));
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }

    private ClawgicMatch createScheduledMatch(UUID tournamentId, UUID agent1Id, UUID agent2Id) {
        ClawgicMatch match = new ClawgicMatch();
        OffsetDateTime now = OffsetDateTime.now();
        match.setMatchId(UUID.randomUUID());
        match.setTournamentId(tournamentId);
        match.setAgent1Id(agent1Id);
        match.setAgent2Id(agent2Id);
        match.setBracketRound(1);
        match.setBracketPosition(1);
        match.setStatus(ClawgicMatchStatus.SCHEDULED);
        match.setTranscriptJson(DebateTranscriptJsonCodec.emptyTranscript());
        match.setJudgeRetryCount(0);
        match.setCreatedAt(now.minusMinutes(2));
        match.setUpdatedAt(now.minusMinutes(2));
        return clawgicMatchRepository.saveAndFlush(match);
    }

    private static String randomWalletAddress() {
        String hex = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        return "0x" + hex.substring(0, 40);
    }
}
