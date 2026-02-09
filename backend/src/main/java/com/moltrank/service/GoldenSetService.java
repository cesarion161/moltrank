package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.GoldenSetItemRepository;
import com.moltrank.repository.PairRepository;
import com.moltrank.repository.CuratorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages golden set pairs for curator calibration.
 *
 * Golden pairs are pre-labeled pairs with known correct answers used to:
 * - Calibrate curator accuracy
 * - Adjust curator voting power multipliers
 * - Detect low-quality curators
 */
@Service
public class GoldenSetService {

    private final GoldenSetItemRepository goldenSetItemRepository;
    private final PairRepository pairRepository;
    private final CuratorRepository curatorRepository;
    private final Random random = new Random();

    public GoldenSetService(
            GoldenSetItemRepository goldenSetItemRepository,
            PairRepository pairRepository,
            CuratorRepository curatorRepository) {
        this.goldenSetItemRepository = goldenSetItemRepository;
        this.pairRepository = pairRepository;
        this.curatorRepository = curatorRepository;
    }

    /**
     * Injects golden pairs into a round (10% of total pairs).
     * Golden pairs are randomly selected from the golden set pool.
     *
     * @param round The round to inject golden pairs into
     * @param totalPairs Total number of pairs in the round
     * @return List of created golden pairs
     */
    @Transactional
    public List<Pair> injectGoldenPairs(Round round, int totalPairs) {
        int goldenCount = (int) Math.ceil(totalPairs * 0.10);
        List<GoldenSetItem> allGoldenItems = goldenSetItemRepository.findAll();

        if (allGoldenItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<Pair> goldenPairs = new ArrayList<>();

        for (int i = 0; i < goldenCount; i++) {
            // Randomly select a golden set item
            GoldenSetItem goldenItem = allGoldenItems.get(random.nextInt(allGoldenItems.size()));

            Pair pair = new Pair();
            pair.setRound(round);
            pair.setPostA(goldenItem.getPostA());
            pair.setPostB(goldenItem.getPostB());
            pair.setIsGolden(true);
            pair.setIsAudit(false);
            pair.setGoldenAnswer(goldenItem.getCorrectAnswer());

            goldenPairs.add(pairRepository.save(pair));
        }

        return goldenPairs;
    }

    /**
     * Checks curator's vote against golden answer and updates calibration rate.
     *
     * @param curator The curator who voted
     * @param pair The golden pair that was voted on
     * @param vote The curator's vote
     * @return true if vote matched golden answer, false otherwise
     */
    @Transactional
    public boolean evaluateGoldenPairVote(Curator curator, Pair pair, PairWinner vote) {
        if (!pair.getIsGolden() || pair.getGoldenAnswer() == null) {
            throw new IllegalArgumentException("Pair is not a golden pair");
        }

        boolean correct = vote == pair.getGoldenAnswer();

        // Update curator's calibration rate
        updateCalibrationRate(curator, correct);

        return correct;
    }

    /**
     * Updates curator's calibration rate based on golden pair performance.
     * Uses exponential moving average with weight 0.1 (10% new, 90% historical).
     *
     * @param curator The curator to update
     * @param correct Whether the last golden pair vote was correct
     */
    private void updateCalibrationRate(Curator curator, boolean correct) {
        BigDecimal currentRate = curator.getCalibrationRate();
        BigDecimal newValue = correct ? BigDecimal.ONE : BigDecimal.ZERO;

        // EMA: new_rate = 0.9 * current_rate + 0.1 * new_value
        BigDecimal alpha = new BigDecimal("0.1");
        BigDecimal updatedRate = currentRate
                .multiply(BigDecimal.ONE.subtract(alpha))
                .add(newValue.multiply(alpha))
                .setScale(4, RoundingMode.HALF_UP);

        curator.setCalibrationRate(updatedRate);
        curatorRepository.save(curator);
    }

    /**
     * Checks if curator is below calibration threshold and should be suspended.
     *
     * @param curator The curator to check
     * @param threshold The minimum acceptable calibration rate (default 0.6)
     * @return true if curator should be suspended
     */
    public boolean isBelowThreshold(Curator curator, BigDecimal threshold) {
        if (threshold == null) {
            threshold = new BigDecimal("0.6");
        }
        return curator.getCalibrationRate().compareTo(threshold) < 0;
    }

    /**
     * Adds a new golden set item to the pool.
     *
     * @param item The golden set item to add
     * @return The saved golden set item
     */
    @Transactional
    public GoldenSetItem addGoldenSetItem(GoldenSetItem item) {
        return goldenSetItemRepository.save(item);
    }

    /**
     * Retrieves all golden set items.
     *
     * @return List of all golden set items
     */
    public List<GoldenSetItem> getAllGoldenSetItems() {
        return goldenSetItemRepository.findAll();
    }

    /**
     * Deletes a golden set item from the pool.
     *
     * @param id The ID of the golden set item to delete
     */
    @Transactional
    public void deleteGoldenSetItem(Integer id) {
        goldenSetItemRepository.deleteById(id);
    }
}
