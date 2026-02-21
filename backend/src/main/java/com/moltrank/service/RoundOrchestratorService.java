package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.RoundRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Round orchestration service implementing the round state machine.
 * Manages round lifecycle: OPEN -> COMMIT -> REVEAL -> SETTLING -> SETTLED
 * Uses Spring Scheduler for automatic state transitions in MVP/demo mode.
 * Future: Event-driven fallback listening to on-chain events.
 */
@Service
@RequiredArgsConstructor
public class RoundOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(RoundOrchestratorService.class);

    @Value("${moltrank.round.commit-duration-minutes:5}")
    private int commitDurationMinutes;

    @Value("${moltrank.round.reveal-duration-minutes:5}")
    private int revealDurationMinutes;

    @Value("${moltrank.round.min-curators:1}")
    private int minCurators;

    @Value("${moltrank.round.auto-create-enabled:true}")
    private boolean autoCreateEnabled;

    @Value("${moltrank.round.auto-create-on-startup:true}")
    private boolean autoCreateOnStartup;

    private final RoundRepository roundRepository;
    private final MarketRepository marketRepository;
    private final PairGenerationService pairGenerationService;
    private final AutoRevealService autoRevealService;
    private final SettlementService settlementService;
    private final CuratorParticipationService curatorParticipationService;

    private static final List<RoundStatus> ACTIVE_STATUSES = List.of(
            RoundStatus.OPEN, RoundStatus.COMMIT, RoundStatus.REVEAL, RoundStatus.SETTLING);

    /**
     * Runs round auto-creation once at startup so eligible markets do not wait
     * for the first scheduler tick.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        if (!autoCreateEnabled || !autoCreateOnStartup) {
            log.debug("Round auto-create on startup skipped (enabled={}, runOnStartup={})",
                    autoCreateEnabled, autoCreateOnStartup);
            return;
        }
        checkAndCreateRounds();
    }

    /**
     * Scheduled task: process state transitions and create new rounds every minute.
     * Checks all active rounds, transitions them based on deadlines, and creates
     * new rounds for eligible markets.
     */
    @Scheduled(
            fixedRateString = "${moltrank.round.interval-ms:60000}",
            initialDelayString = "${moltrank.round.initial-delay-ms:60000}")
    @Transactional
    public void processRoundTransitions() {
        log.debug("Processing round state transitions");

        OffsetDateTime now = OffsetDateTime.now();

        // Transition OPEN -> COMMIT (start of commit phase)
        List<Round> openRounds = findRoundsByStatus(RoundStatus.OPEN);
        for (Round round : openRounds) {
            if (round.getCommitDeadline() != null && now.isAfter(round.getStartedAt())) {
                // For demo: automatically transition to COMMIT after round starts
                // In production: wait for curator participation signal
                transitionToCommit(round);
            }
        }

        // Transition COMMIT -> REVEAL (commit deadline passed)
        List<Round> commitRounds = findRoundsByStatus(RoundStatus.COMMIT);
        for (Round round : commitRounds) {
            if (round.getCommitDeadline() != null && now.isAfter(round.getCommitDeadline())) {
                transitionToReveal(round);
            }
        }

        // Transition REVEAL -> SETTLING (reveal deadline passed)
        List<Round> revealRounds = findRoundsByStatus(RoundStatus.REVEAL);
        Set<Integer> transitionedToSettlingRoundIds = new HashSet<>();
        for (Round round : revealRounds) {
            if (round.getRevealDeadline() != null && now.isAfter(round.getRevealDeadline())) {
                transitionToSettling(round);
                transitionedToSettlingRoundIds.add(round.getId());
            }
        }

        // Retry all rounds still in SETTLING. Failed rounds remain retryable.
        List<Round> settlingRounds = findRoundsByStatus(RoundStatus.SETTLING);
        for (Round round : settlingRounds) {
            if (transitionedToSettlingRoundIds.contains(round.getId())) {
                continue;
            }
            settleRound(round.getId());
        }

        // Auto-create new rounds for eligible markets
        if (autoCreateEnabled) {
            checkAndCreateRounds();
        } else {
            log.debug("Round auto-create skipped by config (enabled={})", autoCreateEnabled);
        }
    }

    /**
     * Checks all markets and creates new rounds where conditions are met:
     * - No active round (OPEN, COMMIT, REVEAL, or SETTLING) for the market
     * - Sufficient subscribers (>= minCurators)
     */
    @Transactional
    void checkAndCreateRounds() {
        List<Market> markets = marketRepository.findAll();
        for (Market market : markets) {
            List<Round> activeRounds = roundRepository.findByMarketIdAndStatusIn(
                    market.getId(), ACTIVE_STATUSES);
            if (activeRounds.isEmpty()) {
                try {
                    createNewRound(market);
                } catch (Exception e) {
                    log.error("Failed to auto-create round for market {}", market.getName(), e);
                }
            }
        }
    }

    /**
     * Creates a new round for the specified market.
     * Generates pairs, sets deadlines, and initializes the round state.
     *
     * @param market The market to create a round for
     * @return The newly created round
     */
    @Transactional
    public Round createNewRound(Market market) {
        log.info("Creating new round for market: {}", market.getName());

        // Check for existing active rounds (any non-settled status)
        List<Round> activeRounds = roundRepository.findByMarketIdAndStatusIn(
                market.getId(), ACTIVE_STATUSES);
        if (!activeRounds.isEmpty()) {
            log.warn("Market {} already has active rounds, skipping creation", market.getName());
            return null;
        }

        // Edge case: insufficient subscribers
        if (market.getSubscribers() < minCurators) {
            log.warn("Insufficient curators for market {} (need {}, have {})",
                    market.getName(), minCurators, market.getSubscribers());
            return null;
        }

        curatorParticipationService.resetPairsThisEpochForMarket(market.getId());

        Round round = new Round();
        round.setMarket(market);
        round.setStatus(RoundStatus.OPEN);
        round.setStartedAt(OffsetDateTime.now());

        // Set deadlines based on configuration
        round.setCommitDeadline(round.getStartedAt().plusMinutes(commitDurationMinutes));
        round.setRevealDeadline(round.getCommitDeadline().plusMinutes(revealDurationMinutes));

        // Save round first to get ID for pair generation
        Round savedRound = roundRepository.save(round);

        // Generate pairs for this round
        try {
            List<Pair> pairs = pairGenerationService.generatePairs(savedRound);
            savedRound.setPairs(pairs.size());

            // Edge case: empty round (no pairs generated)
            if (pairs.isEmpty()) {
                log.warn("No pairs generated for round {}, marking as invalid", savedRound.getId());
                // Could add an INVALID status or just delete the round
                roundRepository.delete(savedRound);
                return null;
            }

            savedRound = roundRepository.save(savedRound);
            log.info("Created round {} with {} pairs for market {}",
                    savedRound.getId(), pairs.size(), market.getName());

        } catch (Exception e) {
            log.error("Failed to generate pairs for round {}", savedRound.getId(), e);
            roundRepository.delete(savedRound);
            throw new RuntimeException("Failed to create round", e);
        }

        return savedRound;
    }

    /**
     * Transition round from OPEN to COMMIT phase.
     */
    private void transitionToCommit(Round round) {
        log.info("Transitioning round {} to COMMIT", round.getId());
        round.setStatus(RoundStatus.COMMIT);
        round.setUpdatedAt(OffsetDateTime.now());
        roundRepository.save(round);
    }

    /**
     * Transition round from COMMIT to REVEAL phase.
     * Triggers auto-reveal for all unrevealed commitments.
     */
    private void transitionToReveal(Round round) {
        log.info("Transitioning round {} to REVEAL", round.getId());
        round.setStatus(RoundStatus.REVEAL);
        round.setUpdatedAt(OffsetDateTime.now());
        roundRepository.save(round);

        // Trigger auto-reveal service
        try {
            autoRevealService.autoRevealCommitments(round);
        } catch (Exception e) {
            log.error("Auto-reveal failed for round {}", round.getId(), e);
            // Continue with transition even if auto-reveal fails
        }
    }

    /**
     * Transition round from REVEAL to SETTLING phase.
     * Prepares for settlement calculation.
     */
    private void transitionToSettling(Round round) {
        log.info("Transitioning round {} to SETTLING", round.getId());
        round.setStatus(RoundStatus.SETTLING);
        round.setUpdatedAt(OffsetDateTime.now());
        roundRepository.save(round);

        scheduleSettlementAfterCommit(round.getId());
    }

    private List<Round> findRoundsByStatus(RoundStatus status) {
        List<Round> rounds = roundRepository.findByStatus(status);
        return rounds != null ? rounds : List.of();
    }

    private void scheduleSettlementAfterCommit(Integer roundId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    settleRound(roundId);
                }
            });
            return;
        }

        settleRound(roundId);
    }

    private void settleRound(Integer roundId) {
        try {
            String settlementHash = settlementService.settleRound(roundId);
            log.info("Round {} settled with hash {}", roundId, settlementHash);
        } catch (Exception e) {
            log.error("Settlement failed for round {}. Will retry on next scheduler cycle.",
                    roundId, e);
        }
    }
}
