package com.moltrank.clawgic.provider;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.model.DebateTranscriptMessage;
import com.moltrank.clawgic.model.DebateTranscriptRole;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic mock judge used for reproducible local runs and tests.
 */
@Component
public class MockClawgicJudgeProviderClient implements ClawgicJudgeProviderClient {

    @Override
    public ObjectNode evaluate(ClawgicJudgeRequest request) {
        int agent1Words = countWords(request.transcript(), DebateTranscriptRole.AGENT_1);
        int agent2Words = countWords(request.transcript(), DebateTranscriptRole.AGENT_2);

        int agent1Logic = scoreFor(request, "agent1.logic", agent1Words, 4);
        int agent1Persona = scoreFor(request, "agent1.persona", agent1Words, 3);
        int agent1Rebuttal = scoreFor(request, "agent1.rebuttal", agent1Words, 5);

        int agent2Logic = scoreFor(request, "agent2.logic", agent2Words, 4);
        int agent2Persona = scoreFor(request, "agent2.persona", agent2Words, 3);
        int agent2Rebuttal = scoreFor(request, "agent2.rebuttal", agent2Words, 5);

        int agent1Total = agent1Logic + agent1Persona + agent1Rebuttal;
        int agent2Total = agent2Logic + agent2Persona + agent2Rebuttal;
        UUID winnerId = resolveWinner(request, agent1Total, agent2Total);

        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("winner_id", winnerId.toString());
        ObjectNode agent1 = root.putObject("agent_1");
        agent1.put("logic", agent1Logic);
        agent1.put("persona_adherence", agent1Persona);
        agent1.put("rebuttal_strength", agent1Rebuttal);

        ObjectNode agent2 = root.putObject("agent_2");
        agent2.put("logic", agent2Logic);
        agent2.put("persona_adherence", agent2Persona);
        agent2.put("rebuttal_strength", agent2Rebuttal);

        root.put(
                "reasoning",
                "Mock judge scored transcript deterministically by structure and volume "
                        + "(agent_1_total="
                        + agent1Total
                        + ", agent_2_total="
                        + agent2Total
                        + ", agent_1_words="
                        + agent1Words
                        + ", agent_2_words="
                        + agent2Words
                        + ")."
        );
        return root;
    }

    private static UUID resolveWinner(ClawgicJudgeRequest request, int agent1Total, int agent2Total) {
        if (agent1Total > agent2Total) {
            return request.agent1Id();
        }
        if (agent2Total > agent1Total) {
            return request.agent2Id();
        }
        int tieBreak = stableIndex(seed(request, "winner.tiebreak"), 2);
        return tieBreak == 0 ? request.agent1Id() : request.agent2Id();
    }

    private static int scoreFor(ClawgicJudgeRequest request, String criterion, int wordCount, int baseFloor) {
        int scaledByWords = Math.min(4, wordCount / 30);
        int jitter = stableIndex(seed(request, criterion), 4);
        int score = baseFloor + scaledByWords + jitter;
        return Math.min(10, Math.max(0, score));
    }

    private static int countWords(List<DebateTranscriptMessage> transcript, DebateTranscriptRole role) {
        int count = 0;
        for (DebateTranscriptMessage message : transcript) {
            if (message.role() != role) {
                continue;
            }
            count += wordCount(message.content());
        }
        return count;
    }

    private static int wordCount(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        return normalized.split("\\s+").length;
    }

    private static String seed(ClawgicJudgeRequest request, String suffix) {
        return request.matchId()
                + "|"
                + request.judgeKey()
                + "|"
                + request.model()
                + "|"
                + request.topic()
                + "|"
                + request.transcript().size()
                + "|"
                + suffix;
    }

    private static int stableIndex(String seed, int bound) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            int raw = ByteBuffer.wrap(hash).getInt();
            return Math.floorMod(raw, bound);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest algorithm is required", ex);
        }
    }
}
