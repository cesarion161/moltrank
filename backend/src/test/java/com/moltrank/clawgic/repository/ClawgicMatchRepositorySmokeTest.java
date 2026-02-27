package com.moltrank.clawgic.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import com.moltrank.clawgic.model.DebatePhase;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.model.DebateTranscriptMessage;
import com.moltrank.clawgic.model.DebateTranscriptRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
class ClawgicMatchRepositorySmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicMatchRepository clawgicMatchRepository;

    @Test
    void flywayCreatesMatchTableAndRepositoryRoundTripsTranscriptAndJudgeJson() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'clawgic_matches'
                        """,
                Integer.class
        );
        assertEquals(1, tableCount);

        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x4444444444444444444444444444444444444444";
        createUser(walletAddress);
        UUID agent1Id = createAgent(walletAddress, "Transcript Agent A");
        UUID agent2Id = createAgent(walletAddress, "Transcript Agent B");
        ClawgicTournament tournament = createTournament(
                "Debate: Should mocks be mandatory for hackathon demos?",
                now.plusHours(1),
                now.plusMinutes(30)
        );

        ArrayNode transcript = DebateTranscriptJsonCodec.toJson(List.of(
                new DebateTranscriptMessage(
                        DebateTranscriptRole.SYSTEM,
                        DebatePhase.THESIS_DISCOVERY,
                        "Stay concise and logical."
                ),
                new DebateTranscriptMessage(
                        DebateTranscriptRole.AGENT_1,
                        DebatePhase.THESIS_DISCOVERY,
                        "Determinism is necessary for debugging."
                ),
                new DebateTranscriptMessage(
                        DebateTranscriptRole.AGENT_2,
                        DebatePhase.THESIS_DISCOVERY,
                        "Live variance is part of real-world validation."
                )
        ));

        ObjectNode judgeResultJson = OBJECT_MAPPER.createObjectNode();
        judgeResultJson.put("winner_id", agent1Id.toString());
        judgeResultJson.put("reasoning", "Agent1 directly addressed reproducibility tradeoffs.");
        ObjectNode agent1Score = judgeResultJson.putObject("agent_1");
        agent1Score.put("logic", 9);
        agent1Score.put("persona_adherence", 8);
        agent1Score.put("rebuttal_strength", 9);
        ObjectNode agent2Score = judgeResultJson.putObject("agent_2");
        agent2Score.put("logic", 7);
        agent2Score.put("persona_adherence", 6);
        agent2Score.put("rebuttal_strength", 7);

        ClawgicMatch match = new ClawgicMatch();
        UUID matchId = UUID.randomUUID();
        match.setMatchId(matchId);
        match.setTournamentId(tournament.getTournamentId());
        match.setAgent1Id(agent1Id);
        match.setAgent2Id(agent2Id);
        match.setBracketRound(1);
        match.setBracketPosition(1);
        match.setStatus(ClawgicMatchStatus.COMPLETED);
        match.setPhase(DebatePhase.CONCLUSION);
        match.setTranscriptJson(transcript);
        match.setJudgeResultJson(judgeResultJson);
        match.setWinnerAgentId(agent1Id);
        match.setJudgeRetryCount(1);
        match.setExecutionDeadlineAt(now.plusMinutes(10));
        match.setJudgeDeadlineAt(now.plusMinutes(20));
        match.setStartedAt(now.minusMinutes(5));
        match.setJudgeRequestedAt(now.minusMinutes(1));
        match.setJudgedAt(now);
        match.setCompletedAt(now);
        match.setCreatedAt(now.minusMinutes(6));
        match.setUpdatedAt(now.plusSeconds(5));

        clawgicMatchRepository.saveAndFlush(match);

        ClawgicMatch persisted = clawgicMatchRepository.findById(matchId).orElseThrow();
        assertEquals(ClawgicMatchStatus.COMPLETED, persisted.getStatus());
        assertEquals(1, persisted.getBracketRound());
        assertEquals(1, persisted.getBracketPosition());
        assertEquals(DebatePhase.CONCLUSION, persisted.getPhase());
        assertEquals(agent1Id, persisted.getWinnerAgentId());
        assertEquals(1, persisted.getJudgeRetryCount());
        assertJsonEquals(transcript, persisted.getTranscriptJson());
        assertJsonEquals(judgeResultJson, persisted.getJudgeResultJson());

        List<ClawgicMatch> matches =
                clawgicMatchRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, matches.size());
        assertEquals(matchId, matches.getFirst().getMatchId());

        Integer jsonbRowCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM clawgic_matches
                        WHERE match_id = ?
                          AND jsonb_typeof(transcript_json) = 'array'
                          AND jsonb_typeof(judge_result_json) = 'object'
                        """,
                Integer.class,
                matchId
        );
        assertEquals(1, jsonbRowCount);

        assertNotNull(
                jdbcTemplate.queryForObject(
                        "SELECT updated_at FROM clawgic_matches WHERE match_id = ?",
                        OffsetDateTime.class,
                        matchId
                )
        );
    }

    private static void assertJsonEquals(JsonNode expected, JsonNode actual) {
        assertNotNull(actual);
        assertEquals(expected, actual);
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
        agent.setSystemPrompt("Debate clearly and challenge weak assumptions.");
        agent.setApiKeyEncrypted("enc:test");
        clawgicAgentRepository.saveAndFlush(agent);
        return agentId;
    }

    private ClawgicTournament createTournament(String topic, OffsetDateTime startTime, OffsetDateTime entryCloseTime) {
        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(topic);
        tournament.setStatus(ClawgicTournamentStatus.SCHEDULED);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(4);
        tournament.setStartTime(startTime);
        tournament.setEntryCloseTime(entryCloseTime);
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setCreatedAt(entryCloseTime.minusHours(1));
        tournament.setUpdatedAt(entryCloseTime.minusHours(1));
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }
}
