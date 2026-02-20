package com.moltrank.service;

import com.moltrank.model.Pair;
import com.moltrank.model.RoundStatus;
import com.moltrank.repository.PairRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairSelectionServiceTest {

    private static final String WALLET = "test-wallet";

    @Mock
    private PairRepository pairRepository;

    @InjectMocks
    private PairSelectionService pairSelectionService;

    @Test
    void findNextPairForCurator_queriesCommitPhaseOnly() {
        Pair pair = new Pair();
        when(pairRepository.findNextPairForCurator(WALLET, 1, List.of(RoundStatus.COMMIT)))
                .thenReturn(Optional.of(pair));

        Optional<Pair> result = pairSelectionService.findNextPairForCurator(WALLET, 1);

        assertSame(pair, result.orElseThrow());
        verify(pairRepository).findNextPairForCurator(WALLET, 1, List.of(RoundStatus.COMMIT));
    }

    @ParameterizedTest
    @EnumSource(RoundStatus.class)
    void isEligibleCurationPhase_allowsOnlyCommit(RoundStatus status) {
        assertEquals(status == RoundStatus.COMMIT, pairSelectionService.isEligibleCurationPhase(status));
    }
}
