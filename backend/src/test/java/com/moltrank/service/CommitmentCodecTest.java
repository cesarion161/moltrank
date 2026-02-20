package com.moltrank.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moltrank.model.PairWinner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitmentCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void computesHashesAndPayloadsFromSharedVectors() throws Exception {
        List<Vector> vectors = loadVectors();

        for (Vector vector : vectors) {
            CommitmentCodec.RevealPayload payload = CommitmentCodec.decodeRevealPayloadBase64(vector.revealPayloadBase64());

            assertEquals(vector.choice(), payload.choice(), "Choice mismatch for vector " + vector.name());
            assertEquals(vector.nonceHex().toLowerCase(), payload.nonceHex(), "Nonce mismatch for vector " + vector.name());

            String hash = CommitmentCodec.computeCommitmentHash(
                    vector.wallet(),
                    vector.pairId(),
                    vector.choice(),
                    vector.stakeAmount(),
                    payload.nonce()
            );
            assertEquals(vector.commitmentHash().toLowerCase(), hash, "Hash mismatch for vector " + vector.name());
        }
    }

    @Test
    void normalizeCommitmentHash_acceptsPrefixedAndUnprefixedDigests() {
        String digest = "ABCD".repeat(16);
        assertEquals("0x" + digest.toLowerCase(), CommitmentCodec.normalizeCommitmentHash("0x" + digest));
        assertEquals("0x" + digest.toLowerCase(), CommitmentCodec.normalizeCommitmentHash(digest));
    }

    private List<Vector> loadVectors() throws Exception {
        Path vectorsPath = Path.of("..", "config", "commitment-test-vectors.json").normalize();
        String content = Files.readString(vectorsPath);
        return objectMapper.readValue(content, new TypeReference<>() {
        });
    }

    private record Vector(
            String name,
            String wallet,
            int pairId,
            long stakeAmount,
            PairWinner choice,
            String nonceHex,
            String revealPayloadBase64,
            String commitmentHash
    ) {
    }
}
