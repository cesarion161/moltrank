package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.PairRepository;
import com.moltrank.repository.RoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Round orchestration service implementing the round state machine.
 * Manages round lifecycle: OPEN -> COMMIT -> REVEAL -> SETTLING -> SETTLED
 *
 * Uses Spring Scheduler for automatic state transitions in MVP/demo mode.
 * Future: Event-driven fallback listening to on-chain events.
 */
@Service
public class RoundOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(RoundOrchestratorService.class);

    @Value("${moltrank.round.commit-duration-minutes:5}")
    private int commitDurationMinutes;

    @Value("${moltrank.round.reveal-duration-minutes:5}")
    private int revealDurationMinutes;

    @Value("${moltrank.round.min-curators:1}")
    private int minCurators;

    private final RoundRepository roundRepository;
    private final MarketRepository marketRepository;
    private final PairRepository pairRepository;
    private final PairGenerationService pairGenerationService;
    private final AutoRevealService autoRevealService;

    public RoundOrchestratorService(
            RoundRepository roundRepository,
            MarketRepository marketRepository,
            PairRepository pairRepository,
            PairGenerationService pairGenerationService,
            AutoRevealService autoRevealService) {
        this.roundRepository = roundRepository;
        this.marketRepository = marketRepository;
        this.pairRepository = pairRepository;
        this.pairGenerationService = pairGenerationService;
        this.autoRevealService = autoRevealService;
    }

    /**
     * Scheduled task: check all markets and create rounds when conditions are met.
     * A round is created when:
     * - The market has sufficient subscribers (>= minCurators)
     * - There are enough posts to generate at least one pair
     * - No active (non-SETTLED) round already exists for the market
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    @Transactional
    public void checkAndCreateRounds() {
        log.debug("Checking markets for automated round creation");

        List<Market> markets = marketRepository.findAll();
        for (Market market : markets) {
            // Skip markets that already have an active round
            List<Round> activeRounds = roundRepository.findByMarketIdAndStatusIn(
                    market.getId(),
                    List.of(RoundStatus.OPEN, RoundStatus.COMMIT, RoundStatus.REVEAL, RoundStatus.SETTLING));
            if (!activeRounds.isEmpty()) {
                log.debug("Market {} already has active round(s), skipping", market.getName());
                continue;
            }

            // Attempt round creation (method handles subscriber/post checks internally)
            try {
                Round created = createNewRound(market);
                if (created != null) {
                    log.info("Automatically created round {} for market {}", created.getId(), market.getName());
                }
            } catch (Exception e) {
                log.error("Failed to auto-create round for market {}", market.getName(), e);
            }
        }
    }

    /**
     * Scheduled task: process state transitions every minute.
     * Checks all active rounds and transitions them based on deadlines.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000) // Run every 60 seconds, offset from round creation
    @Transactional
    public void processRoundTransitions() {
        log.debug("Processing round state transitions");

        OffsetDateTime now = OffsetDateTime.now();

        // Transition OPEN -> COMMIT (start of commit phase)
        List<Round> openRounds = roundRepository.findByStatus(RoundStatus.OPEN);
        for (Round round : openRounds) {
            if (round.getCommitDeadline() != null && now.isAfter(round.getStartedAt())) {
                // For demo: automatically transition to COMMIT after round starts
                // In production: wait for curator participation signal
                transitionToCommit(round);
            }
        }

        // Transition COMMIT -> REVEAL (commit deadline passed)
        List<Round> commitRounds = roundRepository.findByStatus(RoundStatus.COMMIT);
        for (Round round : commitRounds) {
            if (round.getCommitDeadline() != null && now.isAfter(round.getCommitDeadline())) {
                transitionToReveal(round);
            }
        }

        // Transition REVEAL -> SETTLING (reveal deadline passed)
        List<Round> revealRounds = roundRepository.findByStatus(RoundStatus.REVEAL);
        for (Round round : revealRounds) {
            if (round.getRevealDeadline() != null && now.isAfter(round.getRevealDeadline())) {
                transitionToSettling(round);
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

        // Check for existing active rounds (any non-SETTLED status)
        List<Round> activeRounds = roundRepository.findByMarketIdAndStatusIn(
                market.getId(),
                List.of(RoundStatus.OPEN, RoundStatus.COMMIT, RoundStatus.REVEAL, RoundStatus.SETTLING));
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

        // Future: trigger settlement engine
        // settlementEngine.processRound(round);
    }

    /**
     * Manually transition round to SETTLED status.
     * Called by settlement engine after completion.
     */
    @Transactional
    public void markRoundSettled(Round round) {
        log.info("Marking round {} as SETTLED", round.getId());
        round.setStatus(RoundStatus.SETTLED);
        round.setSettledAt(OffsetDateTime.now());
        round.setUpdatedAt(OffsetDateTime.now());
        roundRepository.save(round);
    }

    /**
     * Get all rounds for a specific market.
     */
    public List<Round> getRoundsByMarket(Integer marketId) {
        return roundRepository.findByMarketId(marketId);
    }

    /**
     * Get all rounds with a specific status.
     */
    public List<Round> getRoundsByStatus(RoundStatus status) {
        return roundRepository.findByStatus(status);
    }
}
