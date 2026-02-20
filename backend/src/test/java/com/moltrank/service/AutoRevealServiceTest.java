package com.moltrank.service;

import com.moltrank.model.Commitment;
import com.moltrank.model.Pair;
import com.moltrank.model.PairWinner;
import com.moltrank.model.Round;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.PairRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoRevealServiceTest {

    @Mock
    private CommitmentRepository commitmentRepository;

    @Mock
    private PairRepository pairRepository;

    @InjectMocks
    private AutoRevealService autoRevealService;

    private static final String WALLET = "4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro";

    private void configureRetries() {
        ReflectionTestUtils.setField(autoRevealService, "maxRetries", 1);
        ReflectionTestUtils.setField(autoRevealService, "retryDelaySeconds", 0);
    }

    @Test
    void autoRevealCommitments_revealsCanonicalPayloadWhenHashMatches() {
        configureRetries();

        Round round = new Round();
        round.setId(1);

        Pair pair = new Pair();
        pair.setId(1);

        byte[] nonce = parseHex("f1e2d3c4b5a697887766554433221100ffeeddccbbaa99887766554433221100");
        byte[] revealPayload = new byte[1 + nonce.length];
        revealPayload[0] = 1;
        System.arraycopy(nonce, 0, revealPayload, 1, nonce.length);
        String encodedRevealPayload = Base64.getEncoder().encodeToString(revealPayload);

        Commitment commitment = new Commitment();
        commitment.setId(7);
        commitment.setPair(pair);
        commitment.setCuratorWallet(WALLET);
        commitment.setStake(50L);
        commitment.setCommittedAt(OffsetDateTime.now().minusMinutes(1));
        commitment.setEncryptedReveal(encodedRevealPayload);
        commitment.setHash(CommitmentCodec.computeCommitmentHash(
                WALLET,
                pair.getId(),
                PairWinner.B,
                commitment.getStake(),
                nonce
        ));

        when(pairRepository.findByRoundId(round.getId())).thenReturn(List.of(pair));
        when(commitmentRepository.findByPairIdAndRevealed(pair.getId(), false)).thenReturn(List.of(commitment));
        when(commitmentRepository.save(any(Commitment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        autoRevealService.autoRevealCommitments(round);

        ArgumentCaptor<Commitment> captor = ArgumentCaptor.forClass(Commitment.class);
        verify(commitmentRepository).save(captor.capture());
        Commitment saved = captor.getValue();
        assertTrue(saved.getRevealed());
        assertEquals(PairWinner.B, saved.getChoice());
        assertEquals("f1e2d3c4b5a697887766554433221100ffeeddccbbaa99887766554433221100", saved.getNonce());
        assertNotNull(saved.getRevealedAt());
    }

    @Test
    void autoRevealCommitments_rejectsRevealWhenHashDoesNotMatch() {
        configureRetries();

        Round round = new Round();
        round.setId(1);

        Pair pair = new Pair();
        pair.setId(1);

        byte[] nonce = parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        byte[] revealPayload = new byte[1 + nonce.length];
        revealPayload[0] = 0;
        System.arraycopy(nonce, 0, revealPayload, 1, nonce.length);

        Commitment commitment = new Commitment();
        commitment.setId(8);
        commitment.setPair(pair);
        commitment.setCuratorWallet(WALLET);
        commitment.setStake(50L);
        commitment.setCommittedAt(OffsetDateTime.now().minusMinutes(1));
        commitment.setEncryptedReveal(Base64.getEncoder().encodeToString(revealPayload));
        commitment.setHash("0x1111111111111111111111111111111111111111111111111111111111111111");

        when(pairRepository.findByRoundId(round.getId())).thenReturn(List.of(pair));
        when(commitmentRepository.findByPairIdAndRevealed(pair.getId(), false)).thenReturn(List.of(commitment));

        autoRevealService.autoRevealCommitments(round);

        verify(commitmentRepository, never()).save(any(Commitment.class));
    }

    private static byte[] parseHex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int start = i * 2;
            out[i] = (byte) Integer.parseInt(value.substring(start, start + 2), 16);
        }
        return out;
    }
}
