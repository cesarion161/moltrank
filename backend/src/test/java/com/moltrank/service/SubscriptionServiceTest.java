package com.moltrank.service;

import com.moltrank.controller.dto.CreateSubscriptionRequest;
import com.moltrank.model.Market;
import com.moltrank.model.Round;
import com.moltrank.model.Subscription;
import com.moltrank.model.SubscriptionType;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.RoundRepository;
import com.moltrank.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private PoolService poolService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void createSubscription_realtimeUpdatesMarketAndPool() {
        Market market = buildMarket(1, 2, 1_000L);
        Round round = new Round();
        round.setId(42);

        when(marketRepository.findById(1)).thenReturn(Optional.of(market));
        when(roundRepository.findById(42)).thenReturn(Optional.of(round));
        when(marketRepository.save(any(Market.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription saved = invocation.getArgument(0);
            saved.setId(10);
            return saved;
        });
        when(poolService.getPoolBalance()).thenReturn(50_000L);

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "reader-wallet-1",
                5_000L,
                SubscriptionType.REALTIME,
                1,
                42,
                null,
                null
        );

        SubscriptionService.SubscriptionCreationResult result = subscriptionService.createSubscription(request);

        assertEquals(10, result.subscription().getId());
        assertEquals("reader-wallet-1", result.subscription().getReaderWallet());
        assertEquals(5_000L, result.subscription().getAmount());
        assertEquals(SubscriptionType.REALTIME, result.subscription().getType());
        assertNotNull(result.subscription().getSubscribedAt());
        assertNotNull(result.subscription().getExpiresAt());
        assertEquals(3, result.market().getSubscribers());
        assertEquals(6_000L, result.market().getSubscriptionRevenue());
        assertEquals(5_000L, result.marketRevenueDelta());
        assertEquals(5_000L, result.poolContribution());
        assertEquals(50_000L, result.poolBalance());

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription persisted = subscriptionCaptor.getValue();
        assertEquals(42, persisted.getRound().getId());
        assertEquals(1, persisted.getMarket().getId());

        verify(poolService).addToPool(5_000L, "subscription:realtime:market:1");
    }

    @Test
    void createSubscription_freeDelayOnlyUpdatesSubscribers() {
        Market market = buildMarket(1, 7, 2_000L);

        when(marketRepository.findById(1)).thenReturn(Optional.of(market));
        when(marketRepository.save(any(Market.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription saved = invocation.getArgument(0);
            saved.setId(11);
            return saved;
        });
        when(poolService.getPoolBalance()).thenReturn(123_000L);

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "reader-wallet-2",
                1_000L,
                SubscriptionType.FREE_DELAY,
                1,
                null,
                null,
                null
        );

        SubscriptionService.SubscriptionCreationResult result = subscriptionService.createSubscription(request);

        assertEquals(8, result.market().getSubscribers());
        assertEquals(2_000L, result.market().getSubscriptionRevenue());
        assertEquals(0L, result.marketRevenueDelta());
        assertEquals(0L, result.poolContribution());
        assertEquals(123_000L, result.poolBalance());
        assertNull(result.subscription().getRound());

        verify(poolService, never()).addToPool(any(Long.class), any(String.class));
    }

    @Test
    void createSubscription_rejectsInvalidRequest() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "reader-wallet-3",
                1_000L,
                SubscriptionType.REALTIME,
                null,
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> subscriptionService.createSubscription(request));
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void createSubscription_rejectsUnknownMarket() {
        when(marketRepository.findById(99)).thenReturn(Optional.empty());

        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "reader-wallet-4",
                1_000L,
                SubscriptionType.REALTIME,
                99,
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> subscriptionService.createSubscription(request));
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    private static Market buildMarket(int id, int subscribers, long revenue) {
        Market market = new Market();
        market.setId(id);
        market.setName("General");
        market.setSubmoltId("general");
        market.setSubscribers(subscribers);
        market.setSubscriptionRevenue(revenue);
        return market;
    }
}
