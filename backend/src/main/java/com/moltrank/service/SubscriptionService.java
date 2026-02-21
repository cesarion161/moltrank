package com.moltrank.service;

import com.moltrank.controller.dto.CreateSubscriptionRequest;
import com.moltrank.model.Market;
import com.moltrank.model.Round;
import com.moltrank.model.Subscription;
import com.moltrank.model.SubscriptionType;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.RoundRepository;
import com.moltrank.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final MarketRepository marketRepository;
    private final RoundRepository roundRepository;
    private final PoolService poolService;

    @Transactional
    public SubscriptionCreationResult createSubscription(CreateSubscriptionRequest request) {
        if (request == null || !request.isValid()) {
            throw new IllegalArgumentException("Invalid subscription request");
        }

        Integer marketId = request.resolvedMarketId();
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid market: " + marketId));

        Integer roundId = request.resolvedRoundId();
        Round round = roundId != null
                ? roundRepository.findById(roundId).orElse(null)
                : null;

        OffsetDateTime now = OffsetDateTime.now();
        long poolContribution = 0L;
        long marketRevenueDelta = 0L;

        market.setSubscribers(Math.addExact(market.getSubscribers(), 1));
        if (request.type() == SubscriptionType.REALTIME) {
            marketRevenueDelta = request.amount();
            poolContribution = request.amount();
            market.setSubscriptionRevenue(Math.addExact(market.getSubscriptionRevenue(), marketRevenueDelta));
            poolService.addToPool(poolContribution, "subscription:realtime:market:" + market.getId());
        }
        market.setUpdatedAt(now);
        Market savedMarket = marketRepository.save(market);

        Subscription subscription = new Subscription();
        subscription.setReaderWallet(request.readerWallet());
        subscription.setMarket(savedMarket);
        subscription.setAmount(request.amount());
        subscription.setType(request.type());
        subscription.setRound(round);
        subscription.setSubscribedAt(now);
        subscription.setExpiresAt(now.plusDays(30));

        Subscription created = subscriptionRepository.save(subscription);
        long poolBalance = poolService.getPoolBalance();

        return new SubscriptionCreationResult(
                created,
                savedMarket,
                marketRevenueDelta,
                poolContribution,
                poolBalance
        );
    }

    public record SubscriptionCreationResult(
            Subscription subscription,
            Market market,
            long marketRevenueDelta,
            long poolContribution,
            long poolBalance
    ) {
    }
}
