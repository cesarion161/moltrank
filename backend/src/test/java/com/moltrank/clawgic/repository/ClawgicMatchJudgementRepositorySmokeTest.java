package com.moltrank.clawgic.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.dto.JudgeResult;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchJudgement;
import com.moltrank.clawgic.model.ClawgicMatchJudgementStatus;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import com.moltrank.clawgic.model.DebatePhase;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.model.JudgeResultJsonCodec;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        "moltrank.ingestion.run-on-startup=false"
})
@Transactional
class ClawgicMatchJudgementRepositorySmokeTest {

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

    @Autowired
    private ClawgicMatchJudgementRepository clawgicMatchJudgementRepository;

    @Test
    void repositoryRoundTripsMatchJudgementsAndEnforcesJudgeAttemptUniqueness() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'clawgic_match_judgements'
                        """,
                Integer.class
        );
        assertEquals(1, tableCount);

        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x8888888888888888888888888888888888888888";
        createUser(walletAddress);
        UUID agent1Id = createAgent(walletAddress, "Judge Repo Agent A");
        UUID agent2Id = createAgent(walletAddress, "Judge Repo Agent B");
        ClawgicTournament tournament = createTournament(
                "Debate: Should an MVP keep every judge verdict?",
                now.plusHours(1),
                now.plusMinutes(30)
        );
        ClawgicMatch match = createPendingJudgeMatch(tournament.getTournamentId(), agent1Id, agent2Id, now);

        ObjectNode judgeResultJson = OBJECT_MAPPER.createObjectNode();
        judgeResultJson.put("winner_id", agent1Id.toString());
        judgeResultJson.put("reasoning", "Agent 1 was more consistent across all phases.");
        ObjectNode agent1Score = judgeResultJson.putObject("agent_1");
        agent1Score.put("logic", 9);
        agent1Score.put("persona_adherence", 8);
        agent1Score.put("rebuttal_strength", 9);
        ObjectNode agent2Score = judgeResultJson.putObject("agent_2");
        agent2Score.put("logic", 7);
        agent2Score.put("persona_adherence", 7);
        agent2Score.put("rebuttal_strength", 7);

        JudgeResult parsed = JudgeResultJsonCodec.fromJson(judgeResultJson, agent1Id, agent2Id);

        ClawgicMatchJudgement judgement = new ClawgicMatchJudgement();
        UUID judgementId = UUID.randomUUID();
        judgement.setJudgementId(judgementId);
        judgement.setMatchId(match.getMatchId());
        judgement.setTournamentId(tournament.getTournamentId());
        judgement.setJudgeKey("mock-judge-primary");
        judgement.setJudgeModel("clawgic-mock-v1");
        judgement.setStatus(ClawgicMatchJudgementStatus.ACCEPTED);
        judgement.setAttempt(1);
        JudgeResultJsonCodec.applyToMatchJudgement(judgement, judgeResultJson, parsed);
        judgement.setJudgedAt(now);
        judgement.setCreatedAt(now);
        judgement.setUpdatedAt(now.plusSeconds(1));

        clawgicMatchJudgementRepository.saveAndFlush(judgement);

        List<ClawgicMatchJudgement> judgements =
                clawgicMatchJudgementRepository.findByMatchIdOrderByCreatedAtAsc(match.getMatchId());
        assertEquals(1, judgements.size());
        ClawgicMatchJudgement persisted = judgements.getFirst();
        assertEquals(judgementId, persisted.getJudgementId());
        assertEquals(ClawgicMatchJudgementStatus.ACCEPTED, persisted.getStatus());
        assertEquals(agent1Id, persisted.getWinnerAgentId());
        assertEquals(9, persisted.getAgent1LogicScore());
        assertEquals(8, persisted.getAgent1PersonaAdherenceScore());
        assertEquals(9, persisted.getAgent1RebuttalStrengthScore());
        assertEquals(7, persisted.getAgent2LogicScore());
        assertEquals(7, persisted.getAgent2PersonaAdherenceScore());
        assertEquals(7, persisted.getAgent2RebuttalStrengthScore());
        assertJsonEquals(judgeResultJson, persisted.getResultJson());

        Integer jsonbAndParsedColumnsCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM clawgic_match_judgements
                        WHERE judgement_id = ?
                          AND jsonb_typeof(result_json) = 'object'
                          AND agent1_logic_score = 9
                          AND agent1_persona_adherence_score = 8
                          AND agent1_rebuttal_strength_score = 9
                          AND agent2_logic_score = 7
                          AND agent2_persona_adherence_score = 7
                          AND agent2_rebuttal_strength_score = 7
                        """,
                Integer.class,
                judgementId
        );
        assertEquals(1, jsonbAndParsedColumnsCount);

        ClawgicMatchJudgement retryAttempt = new ClawgicMatchJudgement();
        retryAttempt.setJudgementId(UUID.randomUUID());
        retryAttempt.setMatchId(match.getMatchId());
        retryAttempt.setTournamentId(tournament.getTournamentId());
        retryAttempt.setJudgeKey("mock-judge-primary");
        retryAttempt.setJudgeModel("clawgic-mock-v1");
        retryAttempt.setStatus(ClawgicMatchJudgementStatus.REJECTED);
        retryAttempt.setAttempt(2);
        retryAttempt.setResultJson(OBJECT_MAPPER.createObjectNode().put("error", "invalid json"));
        retryAttempt.setCreatedAt(now.plusSeconds(2));
        retryAttempt.setUpdatedAt(now.plusSeconds(2));
        clawgicMatchJudgementRepository.saveAndFlush(retryAttempt);

        assertTrue(clawgicMatchJudgementRepository.existsByMatchIdAndJudgeKeyAndAttempt(
                match.getMatchId(),
                "mock-judge-primary",
                2
        ));

        assertThrows(DataIntegrityViolationException.class, () -> {
            ClawgicMatchJudgement duplicateAttempt = new ClawgicMatchJudgement();
            duplicateAttempt.setJudgementId(UUID.randomUUID());
            duplicateAttempt.setMatchId(match.getMatchId());
            duplicateAttempt.setTournamentId(tournament.getTournamentId());
            duplicateAttempt.setJudgeKey("mock-judge-primary");
            duplicateAttempt.setJudgeModel("clawgic-mock-v1");
            duplicateAttempt.setStatus(ClawgicMatchJudgementStatus.ERROR);
            duplicateAttempt.setAttempt(2);
            duplicateAttempt.setResultJson(OBJECT_MAPPER.createObjectNode().put("error", "duplicate"));
            clawgicMatchJudgementRepository.save(duplicateAttempt);
            clawgicMatchJudgementRepository.flush();
        });
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

    private ClawgicMatch createPendingJudgeMatch(
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            OffsetDateTime now
    ) {
        ClawgicMatch match = new ClawgicMatch();
        match.setMatchId(UUID.randomUUID());
        match.setTournamentId(tournamentId);
        match.setAgent1Id(agent1Id);
        match.setAgent2Id(agent2Id);
        match.setBracketRound(1);
        match.setBracketPosition(1);
        match.setStatus(ClawgicMatchStatus.PENDING_JUDGE);
        match.setPhase(DebatePhase.CONCLUSION);
        match.setTranscriptJson(DebateTranscriptJsonCodec.emptyTranscript());
        match.setJudgeRequestedAt(now.minusSeconds(30));
        match.setCreatedAt(now.minusMinutes(5));
        match.setUpdatedAt(now.minusSeconds(10));
        return clawgicMatchRepository.saveAndFlush(match);
    }
}
