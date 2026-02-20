package com.moltrank.service;

import com.moltrank.model.Market;
import com.moltrank.repository.MarketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures baseline MVP data exists on startup.
 */
@Component
public class MarketBootstrapService implements ApplicationRunner {

    static final String DEFAULT_MARKET_NAME = "General";
    static final String DEFAULT_SUBMOLT_ID = "general";

    private static final Logger log = LoggerFactory.getLogger(MarketBootstrapService.class);

    private final MarketRepository marketRepository;

    public MarketBootstrapService(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (marketRepository.existsByNameIgnoreCase(DEFAULT_MARKET_NAME)
                || marketRepository.existsBySubmoltIdIgnoreCase(DEFAULT_SUBMOLT_ID)) {
            log.debug("Default market bootstrap skipped: existing General market detected.");
            return;
        }

        Market market = new Market();
        market.setName(DEFAULT_MARKET_NAME);
        market.setSubmoltId(DEFAULT_SUBMOLT_ID);
        market.setSubscriptionRevenue(0L);
        market.setSubscribers(0);
        market.setCreationBond(0L);
        market.setMaxPairs(0);

        Market saved = marketRepository.save(market);
        log.info("Bootstrapped default market: id={}, name={}, submoltId={}",
                saved.getId(), saved.getName(), saved.getSubmoltId());
    }
}
