package com.moltrank.service;

import com.moltrank.model.GlobalPool;
import com.moltrank.model.Market;
import com.moltrank.model.Round;
import com.moltrank.repository.GlobalPoolRepository;
import com.moltrank.repository.MarketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Global pool calculation service.
 * Manages GlobalPool economics: subscription revenue, slashing penalties, minority losses.
 *
 * Base reward per pair = GlobalPool x alpha / totalPairsThisRound (alpha = 0.30)
 * Premium per pair = GlobalPool x (1-alpha) x Revenue(M)/Revenue_total / pairs(M)
 *
 * PRD Reference: Sections 4.1, 4.2, 9.3
 */
@Service
public class PoolService {

    private static final Logger log = LoggerFactory.getLogger(PoolService.class);

    private final GlobalPoolRepository globalPoolRepository;
    private final MarketRepository marketRepository;

    public PoolService(GlobalPoolRepository globalPoolRepository, MarketRepository marketRepository) {
        this.globalPoolRepository = globalPoolRepository;
        this.marketRepository = marketRepository;
    }

    /**
     * Calculate base and premium rewards for a round.
     *
     * @param round The round to calculate rewards for
     * @param totalPairsInRound Total number of pairs in this round across all markets
     * @return Array [basePerPair, premiumPerPair] in lamports
     */
    @Transactional(readOnly = true)
    public long[] calculateRewards(Round round, int totalPairsInRound) {
        if (totalPairsInRound == 0) {
            log.warn("Cannot calculate rewards: totalPairsInRound is 0");
            return new long[]{0L, 0L};
        }

        GlobalPool pool = globalPoolRepository.findById(1)
                .orElseThrow(() -> new IllegalStateException("GlobalPool not found"));

        long poolBalance = pool.getBalance();
        BigDecimal alpha = pool.getAlpha(); // Default 0.30

        // Base reward: GlobalPool x alpha / totalPairs
        BigDecimal baseTotal = BigDecimal.valueOf(poolBalance)
                .multiply(alpha);
        long basePerPair = baseTotal.divide(BigDecimal.valueOf(totalPairsInRound), RoundingMode.DOWN).longValue();

        // Premium calculation: GlobalPool x (1-alpha) distributed by revenue weight
        BigDecimal premiumTotal = BigDecimal.valueOf(poolBalance)
                .multiply(BigDecimal.ONE.subtract(alpha));

        // Get total subscription revenue across all markets
        List<Market> allMarkets = marketRepository.findAll();
        long totalRevenue = allMarkets.stream()
                .mapToLong(Market::getSubscriptionRevenue)
                .sum();

        if (totalRevenue == 0) {
            log.warn("Total revenue is 0, premium per pair will be 0");
            return new long[]{basePerPair, 0L};
        }

        Market market = round.getMarket();
        long marketRevenue = market.getSubscriptionRevenue();
        int pairsInMarket = round.getPairs();

        if (pairsInMarket == 0) {
            log.warn("Pairs in market is 0 for round {}", round.getId());
            return new long[]{basePerPair, 0L};
        }

        // Premium for this market: premiumTotal x (marketRevenue / totalRevenue)
        BigDecimal marketPremium = premiumTotal
                .multiply(BigDecimal.valueOf(marketRevenue))
                .divide(BigDecimal.valueOf(totalRevenue), RoundingMode.DOWN);

        // Premium per pair in this market: marketPremium / pairsInMarket
        long premiumPerPair = marketPremium
                .divide(BigDecimal.valueOf(pairsInMarket), RoundingMode.DOWN)
                .longValue();

        log.info("Calculated rewards for round {}: basePerPair={}, premiumPerPair={}",
                round.getId(), basePerPair, premiumPerPair);

        return new long[]{basePerPair, premiumPerPair};
    }

    /**
     * Add funds to the global pool (from subscription revenue, slashing, etc.)
     *
     * @param amount Amount in lamports to add
     * @param source Description of source (e.g., "subscription", "slashing")
     */
    @Transactional
    public void addToPool(long amount, String source) {
        GlobalPool pool = globalPoolRepository.findById(1)
                .orElseThrow(() -> new IllegalStateException("GlobalPool not found"));

        pool.setBalance(pool.getBalance() + amount);
        pool.setUpdatedAt(OffsetDateTime.now());
        globalPoolRepository.save(pool);

        log.info("Added {} lamports to global pool from {}, new balance: {}",
                amount, source, pool.getBalance());
    }

    /**
     * Deduct funds from the global pool (for settlements)
     *
     * @param amount Amount in lamports to deduct
     * @param reason Description of reason (e.g., "settlement round 42")
     */
    @Transactional
    public void deductFromPool(long amount, String reason) {
        GlobalPool pool = globalPoolRepository.findById(1)
                .orElseThrow(() -> new IllegalStateException("GlobalPool not found"));

        if (pool.getBalance() < amount) {
            throw new IllegalStateException("Insufficient pool balance: " + pool.getBalance() + " < " + amount);
        }

        pool.setBalance(pool.getBalance() - amount);
        pool.setUpdatedAt(OffsetDateTime.now());
        globalPoolRepository.save(pool);

        log.info("Deducted {} lamports from global pool for {}, new balance: {}",
                amount, reason, pool.getBalance());
    }

    /**
     * Get current global pool balance
     *
     * @return Current balance in lamports
     */
    @Transactional(readOnly = true)
    public long getPoolBalance() {
        GlobalPool pool = globalPoolRepository.findById(1)
                .orElseThrow(() -> new IllegalStateException("GlobalPool not found"));
        return pool.getBalance();
    }

    /**
     * Update the alpha parameter (base reward percentage)
     *
     * @param alpha New alpha value (0.0 to 1.0)
     */
    @Transactional
    public void updateAlpha(BigDecimal alpha) {
        if (alpha.compareTo(BigDecimal.ZERO) < 0 || alpha.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Alpha must be between 0.0 and 1.0");
        }

        GlobalPool pool = globalPoolRepository.findById(1)
                .orElseThrow(() -> new IllegalStateException("GlobalPool not found"));

        pool.setAlpha(alpha);
        pool.setUpdatedAt(OffsetDateTime.now());
        globalPoolRepository.save(pool);

        log.info("Updated global pool alpha to {}", alpha);
    }
}
