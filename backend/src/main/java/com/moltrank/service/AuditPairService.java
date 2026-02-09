package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.PairRepository;
import com.moltrank.repository.CuratorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Generates and manages audit pairs for fraud detection.
 *
 * Audit pairs are duplicate pairs with swapped A/B positions shown to the same curator.
 * If a curator votes for the same position both times, it indicates they're not paying
 * attention or gaming the system.
 */
@Service
public class AuditPairService {

    private final PairRepository pairRepository;
    private final CuratorRepository curatorRepository;
    private final Random random = new Random();

    // Track which pairs have been selected for audit duplication
    // Key: original pair ID, Value: swapped audit pair ID
    private final Map<Integer, Integer> auditPairMap = new HashMap<>();

    public AuditPairService(PairRepository pairRepository, CuratorRepository curatorRepository) {
        this.pairRepository = pairRepository;
        this.curatorRepository = curatorRepository;
    }

    /**
     * Generates audit pairs for a round (5% of total pairs).
     * Creates swapped-position duplicates of randomly selected normal pairs.
     *
     * @param round The round to generate audit pairs for
     * @param normalPairs List of normal (non-golden, non-audit) pairs in the round
     * @return List of created audit pairs
     */
    @Transactional
    public List<Pair> generateAuditPairs(Round round, List<Pair> normalPairs) {
        if (normalPairs.isEmpty()) {
            return new ArrayList<>();
        }

        int auditCount = (int) Math.ceil(normalPairs.size() * 0.05);
        List<Pair> auditPairs = new ArrayList<>();

        // Randomly select pairs to duplicate
        List<Pair> selectedPairs = new ArrayList<>(normalPairs);
        Collections.shuffle(selectedPairs, random);

        for (int i = 0; i < Math.min(auditCount, selectedPairs.size()); i++) {
            Pair original = selectedPairs.get(i);

            // Create swapped audit pair
            Pair auditPair = new Pair();
            auditPair.setRound(round);
            auditPair.setPostA(original.getPostB()); // Swap positions
            auditPair.setPostB(original.getPostA());
            auditPair.setIsGolden(false);
            auditPair.setIsAudit(true);

            Pair savedAuditPair = pairRepository.save(auditPair);
            auditPairs.add(savedAuditPair);

            // Track the relationship
            auditPairMap.put(original.getId(), savedAuditPair.getId());
        }

        return auditPairs;
    }

    /**
     * Checks if curator voted inconsistently on an audit pair.
     * Inconsistency: voting same position (A or B) on both original and swapped pair.
     *
     * @param curator The curator who voted
     * @param auditPair The audit pair that was voted on
     * @param auditVote The curator's vote on the audit pair
     * @param originalVote The curator's vote on the original pair
     * @return true if inconsistent (failed audit), false if consistent
     */
    @Transactional
    public boolean evaluateAuditPair(Curator curator, Pair auditPair, PairWinner originalVote, PairWinner auditVote) {
        if (!auditPair.getIsAudit()) {
            throw new IllegalArgumentException("Pair is not an audit pair");
        }

        // Check for inconsistency
        // If curator voted A on original and A on audit (which is swapped), they failed
        // Because A on audit corresponds to B on original
        boolean inconsistent = (originalVote == auditVote);

        if (inconsistent) {
            applyFraudPenalty(curator);
        }

        // Update audit pass rate
        updateAuditPassRate(curator, !inconsistent);

        return inconsistent;
    }

    /**
     * Applies fraud penalty to curator:
     * - Increments fraud flags
     * - 50% stake slash (handled externally via fraud flag)
     *
     * @param curator The curator who failed the audit
     */
    private void applyFraudPenalty(Curator curator) {
        curator.setFraudFlags(curator.getFraudFlags() + 1);
        curatorRepository.save(curator);
    }

    /**
     * Updates curator's audit pass rate.
     * Uses exponential moving average with weight 0.1.
     *
     * @param curator The curator to update
     * @param passed Whether the audit was passed
     */
    private void updateAuditPassRate(Curator curator, boolean passed) {
        BigDecimal currentRate = curator.getAuditPassRate();
        BigDecimal newValue = passed ? BigDecimal.ONE : BigDecimal.ZERO;

        // EMA: new_rate = 0.9 * current_rate + 0.1 * new_value
        BigDecimal alpha = new BigDecimal("0.1");
        BigDecimal updatedRate = currentRate
                .multiply(BigDecimal.ONE.subtract(alpha))
                .add(newValue.multiply(alpha))
                .setScale(4, RoundingMode.HALF_UP);

        curator.setAuditPassRate(updatedRate);
        curatorRepository.save(curator);
    }

    /**
     * Checks if a pair is the audit duplicate of another pair.
     *
     * @param originalPairId The ID of the potentially original pair
     * @param auditPairId The ID of the potentially audit pair
     * @return true if auditPairId is the audit duplicate of originalPairId
     */
    public boolean isAuditPairOf(Integer originalPairId, Integer auditPairId) {
        return auditPairMap.containsKey(originalPairId)
                && auditPairMap.get(originalPairId).equals(auditPairId);
    }

    /**
     * Gets the audit pair ID for an original pair, if it exists.
     *
     * @param originalPairId The ID of the original pair
     * @return The audit pair ID, or null if no audit pair exists
     */
    public Integer getAuditPairId(Integer originalPairId) {
        return auditPairMap.get(originalPairId);
    }

    /**
     * Clears the audit pair tracking map (typically called at round end).
     */
    public void clearAuditPairMap() {
        auditPairMap.clear();
    }
}
