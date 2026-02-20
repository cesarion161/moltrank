package com.moltrank.service;

import com.moltrank.model.Market;
import com.moltrank.repository.MarketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketBootstrapServiceTest {

    @Mock
    private MarketRepository marketRepository;

    @InjectMocks
    private MarketBootstrapService marketBootstrapService;

    @Test
    void run_createsGeneralMarketWhenMissing() throws Exception {
        when(marketRepository.existsByNameIgnoreCase(MarketBootstrapService.DEFAULT_MARKET_NAME))
                .thenReturn(false);
        when(marketRepository.existsBySubmoltIdIgnoreCase(MarketBootstrapService.DEFAULT_SUBMOLT_ID))
                .thenReturn(false);
        when(marketRepository.save(any(Market.class))).thenAnswer(invocation -> invocation.getArgument(0));

        marketBootstrapService.run(null);

        ArgumentCaptor<Market> captor = ArgumentCaptor.forClass(Market.class);
        verify(marketRepository).save(captor.capture());

        Market saved = captor.getValue();
        assertEquals(MarketBootstrapService.DEFAULT_MARKET_NAME, saved.getName());
        assertEquals(MarketBootstrapService.DEFAULT_SUBMOLT_ID, saved.getSubmoltId());
        assertEquals(0L, saved.getSubscriptionRevenue());
        assertEquals(0, saved.getSubscribers());
        assertEquals(0L, saved.getCreationBond());
        assertEquals(0, saved.getMaxPairs());
    }

    @Test
    void run_skipsWhenGeneralMarketByNameAlreadyExists() throws Exception {
        when(marketRepository.existsByNameIgnoreCase(MarketBootstrapService.DEFAULT_MARKET_NAME))
                .thenReturn(true);

        marketBootstrapService.run(null);

        verify(marketRepository, never()).save(any(Market.class));
    }

    @Test
    void run_skipsWhenGeneralMarketBySubmoltAlreadyExists() throws Exception {
        when(marketRepository.existsByNameIgnoreCase(MarketBootstrapService.DEFAULT_MARKET_NAME))
                .thenReturn(false);
        when(marketRepository.existsBySubmoltIdIgnoreCase(MarketBootstrapService.DEFAULT_SUBMOLT_ID))
                .thenReturn(true);

        marketBootstrapService.run(null);

        verify(marketRepository, never()).save(any(Market.class));
    }
}
