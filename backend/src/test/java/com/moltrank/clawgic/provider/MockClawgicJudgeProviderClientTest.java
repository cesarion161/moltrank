package com.moltrank.clawgic.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.dto.JudgeResult;
import com.moltrank.clawgic.model.DebatePhase;
import com.moltrank.clawgic.model.DebateTranscriptMessage;
import com.moltrank.clawgic.model.DebateTranscriptRole;
import com.moltrank.clawgic.model.JudgeResultJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockClawgicJudgeProviderClientTest {

    private final MockClawgicJudgeProviderClient mockJudgeProviderClient = new MockClawgicJudgeProviderClient();

    @Test
    void evaluateReturnsDeterministicStrictJudgeJson() {
        UUID matchId = UUID.randomUUID();
        UUID tournamentId = UUID.randomUUID();
        UUID agent1Id = UUID.randomUUID();
        UUID agent2Id = UUID.randomUUID();

        ClawgicJudgeRequest request = new ClawgicJudgeRequest(
                matchId,
                tournamentId,
                agent1Id,
                agent2Id,
                "Should deterministic judges be the MVP default?",
                List.of(
                        new DebateTranscriptMessage(
                                DebateTranscriptRole.AGENT_1,
                                DebatePhase.THESIS_DISCOVERY,
                                "Deterministic outputs reduce incident risk and speed debug cycles."
                        ),
                        new DebateTranscriptMessage(
                                DebateTranscriptRole.AGENT_2,
                                DebatePhase.THESIS_DISCOVERY,
                                "Determinism can hide model quality regressions if metrics are too rigid."
                        )
                ),
                "mock-judge-primary",
                "clawgic-mock-v1"
        );

        ObjectNode firstResult = mockJudgeProviderClient.evaluate(request);
        ObjectNode secondResult = mockJudgeProviderClient.evaluate(request);
        assertEquals(firstResult, secondResult);

        JudgeResult parsed = JudgeResultJsonCodec.fromJson(firstResult, agent1Id, agent2Id);
        assertTrue(parsed.winnerId().equals(agent1Id) || parsed.winnerId().equals(agent2Id));
        assertTrue(parsed.agent1().logic() >= 0 && parsed.agent1().logic() <= 10);
        assertTrue(parsed.agent2().logic() >= 0 && parsed.agent2().logic() <= 10);
    }
}
