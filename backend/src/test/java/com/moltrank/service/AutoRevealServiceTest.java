package com.moltrank.service;

import com.moltrank.model.Commitment;
import com.moltrank.model.Curator;
import com.moltrank.model.CuratorId;
import com.moltrank.model.Market;
import com.moltrank.model.Pair;
import com.moltrank.model.PairWinner;
import com.moltrank.model.Round;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.CuratorRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoRevealServiceTest {

    @Mock
    private CommitmentRepository commitmentRepository;

    @Mock
    private PairRepository pairRepository;

    @Mock
    private CuratorRepository curatorRepository;

    @Mock
    private PoolService poolService;

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
        assertFalse(saved.getAutoRevealFailed());
        assertNull(saved.getAutoRevealFailureReason());
        assertNull(saved.getAutoRevealFailedAt());
        assertNotNull(saved.getRevealedAt());
    }

    @Test
    void autoRevealCommitments_marksFailureWhenHashDoesNotMatch() {
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
        when(commitmentRepository.save(any(Commitment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        autoRevealService.autoRevealCommitments(round);

        ArgumentCaptor<Commitment> captor = ArgumentCaptor.forClass(Commitment.class);
        verify(commitmentRepository).save(captor.capture());
        Commitment saved = captor.getValue();
        assertFalse(saved.getRevealed());
        assertTrue(saved.getAutoRevealFailed());
        assertEquals("HASH_MISMATCH", saved.getAutoRevealFailureReason());
        assertNotNull(saved.getAutoRevealFailedAt());
        assertNull(saved.getChoice());
        assertNull(saved.getNonce());
    }

    @Test
    void enforceNonRevealPenalties_forfeitsExpiredCommitmentAndUpdatesAccounting() {
        ReflectionTestUtils.setField(autoRevealService, "gracePeriodMinutes", 30);

        Market market = new Market();
        market.setId(1);

        Round round = new Round();
        round.setId(10);
        round.setMarket(market);
        round.setCommitDeadline(OffsetDateTime.now().minusMinutes(31));

        Pair pair = new Pair();
        pair.setId(33);
        pair.setRound(round);

        Commitment commitment = new Commitment();
        commitment.setId(12);
        commitment.setPair(pair);
        commitment.setCuratorWallet(WALLET);
        commitment.setStake(1_000_000_000L);
        commitment.setCommittedAt(OffsetDateTime.now().minusMinutes(90));
        commitment.setRevealed(false);
        commitment.setNonRevealPenalized(false);

        Curator curator = new Curator();
        curator.setWallet(WALLET);
        curator.setMarketId(1);
        curator.setIdentityId(42);
        curator.setLost(100L);

        when(commitmentRepository.findByRevealedAndNonRevealPenalized(false, false)).thenReturn(List.of(commitment));
        when(curatorRepository.findById(new CuratorId(WALLET, 1))).thenReturn(Optional.of(curator));
        when(curatorRepository.save(any(Curator.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commitmentRepository.save(any(Commitment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        autoRevealService.enforceNonRevealPenalties();

        verify(poolService).addToPool(1_000_000_000L, "non-reveal penalty commitment 12");
        assertEquals(1_000_000_100L, curator.getLost());

        ArgumentCaptor<Commitment> captor = ArgumentCaptor.forClass(Commitment.class);
        verify(commitmentRepository).save(captor.capture());
        Commitment saved = captor.getValue();
        assertTrue(saved.getNonRevealPenalized());
        assertNotNull(saved.getNonRevealPenalizedAt());
        assertTrue(saved.getAutoRevealFailed());
        assertEquals("NON_REVEAL_FORFEITED", saved.getAutoRevealFailureReason());
        assertNotNull(saved.getAutoRevealFailedAt());
    }

    @Test
    void enforceNonRevealPenalties_skipsCommitmentWithinGraceWindow() {
        ReflectionTestUtils.setField(autoRevealService, "gracePeriodMinutes", 30);

        Market market = new Market();
        market.setId(1);

        Round round = new Round();
        round.setId(11);
        round.setMarket(market);
        round.setCommitDeadline(OffsetDateTime.now().minusMinutes(10));

        Pair pair = new Pair();
        pair.setId(34);
        pair.setRound(round);

        Commitment commitment = new Commitment();
        commitment.setId(13);
        commitment.setPair(pair);
        commitment.setCuratorWallet(WALLET);
        commitment.setStake(1_000_000_000L);
        commitment.setCommittedAt(OffsetDateTime.now().minusMinutes(20));
        commitment.setRevealed(false);
        commitment.setNonRevealPenalized(false);

        when(commitmentRepository.findByRevealedAndNonRevealPenalized(false, false)).thenReturn(List.of(commitment));

        autoRevealService.enforceNonRevealPenalties();

        verify(poolService, never()).addToPool(anyLong(), any(String.class));
        verify(commitmentRepository, never()).save(any(Commitment.class));
        verifyNoInteractions(curatorRepository);
    }

    @Test
    void enforceNonRevealPenalties_usesAutoRevealFailureTimeForGraceWindow() {
        ReflectionTestUtils.setField(autoRevealService, "gracePeriodMinutes", 30);

        Market market = new Market();
        market.setId(1);

        Round round = new Round();
        round.setId(12);
        round.setMarket(market);
        round.setCommitDeadline(OffsetDateTime.now().minusHours(2));
        round.setRevealDeadline(OffsetDateTime.now().minusHours(1));

        Pair pair = new Pair();
        pair.setId(35);
        pair.setRound(round);

        Commitment commitment = new Commitment();
        commitment.setId(14);
        commitment.setPair(pair);
        commitment.setCuratorWallet(WALLET);
        commitment.setStake(1_000_000_000L);
        commitment.setCommittedAt(OffsetDateTime.now().minusHours(3));
        commitment.setAutoRevealFailedAt(OffsetDateTime.now().minusMinutes(10));
        commitment.setRevealed(false);
        commitment.setNonRevealPenalized(false);

        when(commitmentRepository.findByRevealedAndNonRevealPenalized(false, false)).thenReturn(List.of(commitment));

        autoRevealService.enforceNonRevealPenalties();

        verify(poolService, never()).addToPool(anyLong(), any(String.class));
        verify(commitmentRepository, never()).save(any(Commitment.class));
        verifyNoInteractions(curatorRepository);
    }

    @Test
    void scheduledNonRevealPenaltyEnforcement_skipsWhenDisabled() {
        ReflectionTestUtils.setField(autoRevealService, "penaltyEnforcementEnabled", false);

        autoRevealService.scheduledNonRevealPenaltyEnforcement();

        verify(commitmentRepository, never()).findByRevealedAndNonRevealPenalized(eq(false), eq(false));
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
