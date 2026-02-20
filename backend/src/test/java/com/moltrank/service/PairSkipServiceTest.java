package com.moltrank.service;

import com.moltrank.model.Pair;
import com.moltrank.model.PairSkip;
import com.moltrank.repository.PairSkipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PairSkipServiceTest {

    @Mock
    private PairSkipRepository pairSkipRepository;

    @InjectMocks
    private PairSkipService pairSkipService;

    @Test
    void skipPair_createsSkipWhenMissing() {
        Pair pair = new Pair();
        pair.setId(1);
        String wallet = "wallet-123";

        when(pairSkipRepository.findByPairIdAndCuratorWallet(1, wallet))
                .thenReturn(Optional.empty());

        PairSkip saved = new PairSkip();
        saved.setId(10);
        saved.setPair(pair);
        saved.setCuratorWallet(wallet);

        when(pairSkipRepository.save(any(PairSkip.class))).thenReturn(saved);

        PairSkip result = pairSkipService.skipPair(pair, wallet);

        assertSame(saved, result);
        verify(pairSkipRepository).save(any(PairSkip.class));
    }

    @Test
    void skipPair_returnsExistingSkipWhenAlreadyPresent() {
        Pair pair = new Pair();
        pair.setId(1);
        String wallet = "wallet-123";

        PairSkip existing = new PairSkip();
        existing.setId(11);
        existing.setPair(pair);
        existing.setCuratorWallet(wallet);

        when(pairSkipRepository.findByPairIdAndCuratorWallet(1, wallet))
                .thenReturn(Optional.of(existing));

        PairSkip result = pairSkipService.skipPair(pair, wallet);

        assertSame(existing, result);
        verify(pairSkipRepository, never()).save(any(PairSkip.class));
    }
}
