package com.moltrank.service;

import com.moltrank.model.Curator;
import com.moltrank.model.Identity;
import com.moltrank.repository.CuratorRepository;
import com.moltrank.repository.IdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuratorParticipationServiceTest {

    private static final String WALLET = "wallet-1";

    @Mock
    private CuratorRepository curatorRepository;

    @Mock
    private IdentityRepository identityRepository;

    @InjectMocks
    private CuratorParticipationService curatorParticipationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(curatorParticipationService, "maxPairsPerCuratorPerRound", 3);
    }

    @Test
    void hasRemainingCapacity_returnsTrueWhenCuratorDoesNotExist() {
        when(curatorRepository.findByWalletAndMarketId(WALLET, 1)).thenReturn(Optional.empty());

        boolean result = curatorParticipationService.hasRemainingCapacity(WALLET, 1);

        assertTrue(result);
    }

    @Test
    void hasRemainingCapacity_returnsFalseWhenCapReached() {
        Curator curator = new Curator();
        curator.setPairsThisEpoch(3);
        when(curatorRepository.findByWalletAndMarketId(WALLET, 1)).thenReturn(Optional.of(curator));

        boolean result = curatorParticipationService.hasRemainingCapacity(WALLET, 1);

        assertFalse(result);
    }

    @Test
    void tryConsumePairEvaluationSlot_incrementsExistingCuratorBelowCap() {
        when(curatorRepository.incrementPairsThisEpochIfBelowCap(eq(WALLET), eq(1), eq(3), any()))
                .thenReturn(1);

        boolean accepted = curatorParticipationService.tryConsumePairEvaluationSlot(WALLET, 1);

        assertTrue(accepted);
        verify(curatorRepository, never()).save(any(Curator.class));
    }

    @Test
    void tryConsumePairEvaluationSlot_rejectsWhenCuratorAlreadyAtCap() {
        Curator curator = new Curator();
        curator.setPairsThisEpoch(3);

        when(curatorRepository.incrementPairsThisEpochIfBelowCap(eq(WALLET), eq(1), eq(3), any()))
                .thenReturn(0);
        when(curatorRepository.findByWalletAndMarketId(WALLET, 1)).thenReturn(Optional.of(curator));

        boolean accepted = curatorParticipationService.tryConsumePairEvaluationSlot(WALLET, 1);

        assertFalse(accepted);
        verify(curatorRepository, never()).save(any(Curator.class));
    }

    @Test
    void tryConsumePairEvaluationSlot_bootstrapsCuratorForFirstEvaluation() {
        Identity identity = new Identity();
        identity.setId(99);
        identity.setWallet(WALLET);

        when(curatorRepository.incrementPairsThisEpochIfBelowCap(eq(WALLET), eq(1), eq(3), any()))
                .thenReturn(0);
        when(curatorRepository.findByWalletAndMarketId(WALLET, 1)).thenReturn(Optional.empty());
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.of(identity));
        when(curatorRepository.save(any(Curator.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean accepted = curatorParticipationService.tryConsumePairEvaluationSlot(WALLET, 1);

        assertTrue(accepted);

        ArgumentCaptor<Curator> captor = ArgumentCaptor.forClass(Curator.class);
        verify(curatorRepository).save(captor.capture());
        Curator saved = captor.getValue();
        assertEquals(WALLET, saved.getWallet());
        assertEquals(1, saved.getMarketId());
        assertEquals(99, saved.getIdentityId());
        assertEquals(1, saved.getPairsThisEpoch());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void resetPairsThisEpochForMarket_resetsOnlyCuratorsWithNonZeroCounters() {
        Curator zero = new Curator();
        zero.setPairsThisEpoch(0);

        Curator nonZero = new Curator();
        nonZero.setPairsThisEpoch(4);

        when(curatorRepository.findByMarketId(1)).thenReturn(List.of(zero, nonZero));
        when(curatorRepository.save(any(Curator.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int resets = curatorParticipationService.resetPairsThisEpochForMarket(1);

        assertEquals(1, resets);
        assertEquals(0, nonZero.getPairsThisEpoch());
        verify(curatorRepository).save(nonZero);
    }
}
