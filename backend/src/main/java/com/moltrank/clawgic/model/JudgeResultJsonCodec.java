package com.moltrank.clawgic.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.dto.JudgeResult;

import java.util.Set;
import java.util.UUID;

/**
 * Parses and validates strict judge-result JSON payloads.
 */
public final class JudgeResultJsonCodec {

    private static final String FIELD_WINNER_ID = "winner_id";
    private static final String FIELD_AGENT_1 = "agent_1";
    private static final String FIELD_AGENT_2 = "agent_2";
    private static final String FIELD_REASONING = "reasoning";

    private static final String FIELD_LOGIC = "logic";
    private static final String FIELD_PERSONA_ADHERENCE = "persona_adherence";
    private static final String FIELD_REBUTTAL_STRENGTH = "rebuttal_strength";

    private static final Set<String> ALLOWED_TOP_LEVEL_FIELDS = Set.of(
            FIELD_WINNER_ID,
            FIELD_AGENT_1,
            FIELD_AGENT_2,
            FIELD_REASONING
    );

    private static final Set<String> ALLOWED_SCORE_FIELDS = Set.of(
            FIELD_LOGIC,
            FIELD_PERSONA_ADHERENCE,
            FIELD_REBUTTAL_STRENGTH
    );

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 10;

    private JudgeResultJsonCodec() {
    }

    public static JudgeResult fromJson(JsonNode judgeResultJson, UUID agent1Id, UUID agent2Id) {
        if (judgeResultJson == null || judgeResultJson.isNull() || !judgeResultJson.isObject()) {
            throw new IllegalArgumentException("Judge result JSON must be an object");
        }
        if (agent1Id == null || agent2Id == null) {
            throw new IllegalArgumentException("Match agent ids are required for judge result validation");
        }

        rejectUnexpectedFields(judgeResultJson, ALLOWED_TOP_LEVEL_FIELDS, "judge result");

        UUID winnerId = requireWinnerId(judgeResultJson, agent1Id, agent2Id);
        JudgeResult.AgentScore agent1 = parseScoreObject(judgeResultJson, FIELD_AGENT_1);
        JudgeResult.AgentScore agent2 = parseScoreObject(judgeResultJson, FIELD_AGENT_2);
        String reasoning = optionalTextField(judgeResultJson, FIELD_REASONING);

        return new JudgeResult(winnerId, agent1, agent2, reasoning);
    }

    public static void applyToMatchJudgement(
            ClawgicMatchJudgement matchJudgement,
            JsonNode rawJudgeResultJson,
            JudgeResult judgeResult
    ) {
        if (matchJudgement == null) {
            throw new IllegalArgumentException("Match judgement is required");
        }
        if (rawJudgeResultJson == null || !rawJudgeResultJson.isObject()) {
            throw new IllegalArgumentException("Raw judge result JSON must be an object");
        }
        if (judgeResult == null) {
            throw new IllegalArgumentException("Parsed judge result is required");
        }
        if (judgeResult.winnerId() == null) {
            throw new IllegalArgumentException("Parsed judge result winner_id is required");
        }
        if (judgeResult.agent1() == null || judgeResult.agent2() == null) {
            throw new IllegalArgumentException("Parsed judge result agent score blocks are required");
        }

        matchJudgement.setResultJson(rawJudgeResultJson.deepCopy());
        matchJudgement.setWinnerAgentId(judgeResult.winnerId());
        matchJudgement.setReasoning(judgeResult.reasoning());

        matchJudgement.setAgent1LogicScore(judgeResult.agent1().logic());
        matchJudgement.setAgent1PersonaAdherenceScore(judgeResult.agent1().personaAdherence());
        matchJudgement.setAgent1RebuttalStrengthScore(judgeResult.agent1().rebuttalStrength());

        matchJudgement.setAgent2LogicScore(judgeResult.agent2().logic());
        matchJudgement.setAgent2PersonaAdherenceScore(judgeResult.agent2().personaAdherence());
        matchJudgement.setAgent2RebuttalStrengthScore(judgeResult.agent2().rebuttalStrength());
    }

    public static ObjectNode toJson(JudgeResult judgeResult) {
        if (judgeResult == null) {
            throw new IllegalArgumentException("Judge result is required");
        }
        if (judgeResult.winnerId() == null) {
            throw new IllegalArgumentException("Judge result winner_id is required");
        }
        if (judgeResult.agent1() == null || judgeResult.agent2() == null) {
            throw new IllegalArgumentException("Judge result agent score blocks are required");
        }

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put(FIELD_WINNER_ID, judgeResult.winnerId().toString());

        ObjectNode agent1 = root.putObject(FIELD_AGENT_1);
        agent1.put(FIELD_LOGIC, judgeResult.agent1().logic());
        agent1.put(FIELD_PERSONA_ADHERENCE, judgeResult.agent1().personaAdherence());
        agent1.put(FIELD_REBUTTAL_STRENGTH, judgeResult.agent1().rebuttalStrength());

        ObjectNode agent2 = root.putObject(FIELD_AGENT_2);
        agent2.put(FIELD_LOGIC, judgeResult.agent2().logic());
        agent2.put(FIELD_PERSONA_ADHERENCE, judgeResult.agent2().personaAdherence());
        agent2.put(FIELD_REBUTTAL_STRENGTH, judgeResult.agent2().rebuttalStrength());

        if (judgeResult.reasoning() != null) {
            root.put(FIELD_REASONING, judgeResult.reasoning());
        }
        return root;
    }

    private static UUID requireWinnerId(JsonNode root, UUID agent1Id, UUID agent2Id) {
        JsonNode winnerNode = root.get(FIELD_WINNER_ID);
        if (winnerNode == null || !winnerNode.isTextual()) {
            throw new IllegalArgumentException("Judge result missing textual field 'winner_id'");
        }

        UUID winnerId;
        try {
            winnerId = UUID.fromString(winnerNode.textValue());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Judge result field 'winner_id' must be a valid UUID", ex);
        }

        if (!winnerId.equals(agent1Id) && !winnerId.equals(agent2Id)) {
            throw new IllegalArgumentException(
                    "Judge result 'winner_id' must match one of the match agents"
            );
        }

        return winnerId;
    }

    private static JudgeResult.AgentScore parseScoreObject(JsonNode root, String fieldName) {
        JsonNode scoreNode = root.get(fieldName);
        if (scoreNode == null || !scoreNode.isObject()) {
            throw new IllegalArgumentException("Judge result missing object field '" + fieldName + "'");
        }

        rejectUnexpectedFields(scoreNode, ALLOWED_SCORE_FIELDS, fieldName + " score block");

        return new JudgeResult.AgentScore(
                requireScore(scoreNode, fieldName, FIELD_LOGIC),
                requireScore(scoreNode, fieldName, FIELD_PERSONA_ADHERENCE),
                requireScore(scoreNode, fieldName, FIELD_REBUTTAL_STRENGTH)
        );
    }

    private static int requireScore(JsonNode scoreNode, String agentFieldName, String scoreFieldName) {
        JsonNode valueNode = scoreNode.get(scoreFieldName);
        if (valueNode == null || !valueNode.isIntegralNumber()) {
            throw new IllegalArgumentException(
                    "Judge result missing integer field '"
                            + agentFieldName
                            + "."
                            + scoreFieldName
                            + "'"
            );
        }

        int value = valueNode.intValue();
        if (value < MIN_SCORE || value > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "Judge result field '"
                            + agentFieldName
                            + "."
                            + scoreFieldName
                            + "' must be between "
                            + MIN_SCORE
                            + " and "
                            + MAX_SCORE
            );
        }

        return value;
    }

    private static String optionalTextField(JsonNode root, String fieldName) {
        JsonNode fieldNode = root.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isTextual()) {
            throw new IllegalArgumentException("Judge result field '" + fieldName + "' must be textual");
        }
        return fieldNode.textValue();
    }

    private static void rejectUnexpectedFields(JsonNode node, Set<String> allowedFields, String objectLabel) {
        var fieldIterator = node.fieldNames();
        while (fieldIterator.hasNext()) {
            String fieldName = fieldIterator.next();
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException(
                        "Unexpected field '" + fieldName + "' in " + objectLabel
                );
            }
        }
    }
}
