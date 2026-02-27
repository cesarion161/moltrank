package com.moltrank.clawgic.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moltrank.clawgic.dto.JudgeResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JudgeResultJsonCodecTest {

    @Test
    void fromJsonParsesValidJudgeResult() {
        UUID agent1Id = UUID.randomUUID();
        UUID agent2Id = UUID.randomUUID();

        ObjectNode judgeResultJson = JsonNodeFactory.instance.objectNode();
        judgeResultJson.put("winner_id", agent2Id.toString());
        judgeResultJson.put("reasoning", "Agent 2 delivered stronger direct rebuttals.");
        ObjectNode agent1 = judgeResultJson.putObject("agent_1");
        agent1.put("logic", 8);
        agent1.put("persona_adherence", 7);
        agent1.put("rebuttal_strength", 6);
        ObjectNode agent2 = judgeResultJson.putObject("agent_2");
        agent2.put("logic", 9);
        agent2.put("persona_adherence", 8);
        agent2.put("rebuttal_strength", 9);

        JudgeResult parsed = JudgeResultJsonCodec.fromJson(judgeResultJson, agent1Id, agent2Id);

        assertEquals(agent2Id, parsed.winnerId());
        assertEquals(8, parsed.agent1().logic());
        assertEquals(7, parsed.agent1().personaAdherence());
        assertEquals(6, parsed.agent1().rebuttalStrength());
        assertEquals(9, parsed.agent2().logic());
        assertEquals(8, parsed.agent2().personaAdherence());
        assertEquals(9, parsed.agent2().rebuttalStrength());
        assertEquals("Agent 2 delivered stronger direct rebuttals.", parsed.reasoning());
    }

    @Test
    void fromJsonRejectsMissingRequiredScoreField() {
        UUID agent1Id = UUID.randomUUID();
        UUID agent2Id = UUID.randomUUID();

        ObjectNode judgeResultJson = JsonNodeFactory.instance.objectNode();
        judgeResultJson.put("winner_id", agent1Id.toString());
        ObjectNode agent1 = judgeResultJson.putObject("agent_1");
        agent1.put("logic", 8);
        agent1.put("rebuttal_strength", 6);
        ObjectNode agent2 = judgeResultJson.putObject("agent_2");
        agent2.put("logic", 7);
        agent2.put("persona_adherence", 7);
        agent2.put("rebuttal_strength", 7);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JudgeResultJsonCodec.fromJson(judgeResultJson, agent1Id, agent2Id)
        );

        assertEquals(
                "Judge result missing integer field 'agent_1.persona_adherence'",
                ex.getMessage()
        );
    }

    @Test
    void fromJsonRejectsOutOfRangeScore() {
        UUID agent1Id = UUID.randomUUID();
        UUID agent2Id = UUID.randomUUID();

        ObjectNode judgeResultJson = JsonNodeFactory.instance.objectNode();
        judgeResultJson.put("winner_id", agent1Id.toString());
        ObjectNode agent1 = judgeResultJson.putObject("agent_1");
        agent1.put("logic", 11);
        agent1.put("persona_adherence", 7);
        agent1.put("rebuttal_strength", 8);
        ObjectNode agent2 = judgeResultJson.putObject("agent_2");
        agent2.put("logic", 6);
        agent2.put("persona_adherence", 6);
        agent2.put("rebuttal_strength", 6);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JudgeResultJsonCodec.fromJson(judgeResultJson, agent1Id, agent2Id)
        );

        assertEquals(
                "Judge result field 'agent_1.logic' must be between 0 and 10",
                ex.getMessage()
        );
    }

    @Test
    void fromJsonRejectsWinnerOutsideMatchAgents() {
        UUID agent1Id = UUID.randomUUID();
        UUID agent2Id = UUID.randomUUID();

        ObjectNode judgeResultJson = JsonNodeFactory.instance.objectNode();
        judgeResultJson.put("winner_id", UUID.randomUUID().toString());
        ObjectNode agent1 = judgeResultJson.putObject("agent_1");
        agent1.put("logic", 8);
        agent1.put("persona_adherence", 7);
        agent1.put("rebuttal_strength", 8);
        ObjectNode agent2 = judgeResultJson.putObject("agent_2");
        agent2.put("logic", 7);
        agent2.put("persona_adherence", 7);
        agent2.put("rebuttal_strength", 7);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JudgeResultJsonCodec.fromJson(judgeResultJson, agent1Id, agent2Id)
        );

        assertEquals("Judge result 'winner_id' must match one of the match agents", ex.getMessage());
    }

    @Test
    void applyToMatchJudgementStoresRawJsonAndParsedFields() {
        UUID agent1Id = UUID.randomUUID();
        UUID agent2Id = UUID.randomUUID();

        ObjectNode judgeResultJson = JsonNodeFactory.instance.objectNode();
        judgeResultJson.put("winner_id", agent1Id.toString());
        ObjectNode agent1 = judgeResultJson.putObject("agent_1");
        agent1.put("logic", 10);
        agent1.put("persona_adherence", 8);
        agent1.put("rebuttal_strength", 9);
        ObjectNode agent2 = judgeResultJson.putObject("agent_2");
        agent2.put("logic", 7);
        agent2.put("persona_adherence", 7);
        agent2.put("rebuttal_strength", 8);

        JudgeResult judgeResult = JudgeResultJsonCodec.fromJson(judgeResultJson, agent1Id, agent2Id);

        ClawgicMatchJudgement judgement = new ClawgicMatchJudgement();
        JudgeResultJsonCodec.applyToMatchJudgement(judgement, judgeResultJson, judgeResult);

        assertEquals(agent1Id, judgement.getWinnerAgentId());
        assertEquals(10, judgement.getAgent1LogicScore());
        assertEquals(8, judgement.getAgent1PersonaAdherenceScore());
        assertEquals(9, judgement.getAgent1RebuttalStrengthScore());
        assertEquals(7, judgement.getAgent2LogicScore());
        assertEquals(7, judgement.getAgent2PersonaAdherenceScore());
        assertEquals(8, judgement.getAgent2RebuttalStrengthScore());
        assertEquals(judgeResultJson, judgement.getResultJson());
        assertNull(judgement.getResultJson().get("reasoning"));
    }
}
