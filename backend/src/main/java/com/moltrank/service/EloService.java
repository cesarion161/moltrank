package com.moltrank.service;

import com.moltrank.model.Post;
import com.moltrank.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * ELO rating update service.
 * Implements weighted ELO calculation for post rankings.
 *
 * Formula: E_X = 1 / (1 + 10^((R_Y - R_X) / 400))
 * K factor weighted by total stake and CuratorScore of voters
 *
 * PRD Reference: Section 4.7
 */
@Service
public class EloService {

    private static final Logger log = LoggerFactory.getLogger(EloService.class);
    private static final double BASE_K_FACTOR = 32.0;
    private static final int INITIAL_ELO = 1500;

    private final PostRepository postRepository;

    public EloService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * Update ELO ratings for two posts based on voting outcome.
     *
     * @param winnerId ID of the winning post
     * @param loserId ID of the losing post
     * @param totalStake Total stake on this pair (in lamports)
     * @param averageCuratorScore Average curator score of voters
     */
    @Transactional
    public void updateElo(Integer winnerId, Integer loserId, long totalStake, BigDecimal averageCuratorScore) {
        Post winner = postRepository.findById(winnerId)
                .orElseThrow(() -> new IllegalArgumentException("Winner post not found: " + winnerId));
        Post loser = postRepository.findById(loserId)
                .orElseThrow(() -> new IllegalArgumentException("Loser post not found: " + loserId));

        int winnerElo = winner.getElo() != null ? winner.getElo() : INITIAL_ELO;
        int loserElo = loser.getElo() != null ? loser.getElo() : INITIAL_ELO;

        // Calculate expected scores
        double expectedWinner = calculateExpectedScore(winnerElo, loserElo);
        double expectedLoser = calculateExpectedScore(loserElo, winnerElo);

        // Calculate weighted K factor
        double kFactor = calculateKFactor(totalStake, averageCuratorScore);

        // Update ELO ratings (winner gets 1.0, loser gets 0.0)
        int newWinnerElo = (int) Math.round(winnerElo + kFactor * (1.0 - expectedWinner));
        int newLoserElo = (int) Math.round(loserElo + kFactor * (0.0 - expectedLoser));

        winner.setElo(newWinnerElo);
        winner.setUpdatedAt(OffsetDateTime.now());
        loser.setElo(newLoserElo);
        loser.setUpdatedAt(OffsetDateTime.now());

        postRepository.save(winner);
        postRepository.save(loser);

        log.info("Updated ELO: post {} ({} -> {}), post {} ({} -> {}), K={}",
                winnerId, winnerElo, newWinnerElo,
                loserId, loserElo, newLoserElo,
                String.format("%.2f", kFactor));
    }

    /**
     * Update ELO for a tie.
     *
     * @param postAId ID of first post
     * @param postBId ID of second post
     * @param totalStake Total stake on this pair (in lamports)
     * @param averageCuratorScore Average curator score of voters
     */
    @Transactional
    public void updateEloTie(Integer postAId, Integer postBId, long totalStake, BigDecimal averageCuratorScore) {
        Post postA = postRepository.findById(postAId)
                .orElseThrow(() -> new IllegalArgumentException("Post A not found: " + postAId));
        Post postB = postRepository.findById(postBId)
                .orElseThrow(() -> new IllegalArgumentException("Post B not found: " + postBId));

        int eloA = postA.getElo() != null ? postA.getElo() : INITIAL_ELO;
        int eloB = postB.getElo() != null ? postB.getElo() : INITIAL_ELO;

        // Calculate expected scores
        double expectedA = calculateExpectedScore(eloA, eloB);
        double expectedB = calculateExpectedScore(eloB, eloA);

        // Calculate weighted K factor
        double kFactor = calculateKFactor(totalStake, averageCuratorScore);

        // Update ELO ratings (both get 0.5 for a tie)
        int newEloA = (int) Math.round(eloA + kFactor * (0.5 - expectedA));
        int newEloB = (int) Math.round(eloB + kFactor * (0.5 - expectedB));

        postA.setElo(newEloA);
        postA.setUpdatedAt(OffsetDateTime.now());
        postB.setElo(newEloB);
        postB.setUpdatedAt(OffsetDateTime.now());

        postRepository.save(postA);
        postRepository.save(postB);

        log.info("Updated ELO (tie): post {} ({} -> {}), post {} ({} -> {}), K={}",
                postAId, eloA, newEloA,
                postBId, eloB, newEloB,
                String.format("%.2f", kFactor));
    }

    /**
     * Calculate expected score for a post given ELO ratings.
     * Formula: E_X = 1 / (1 + 10^((R_Y - R_X) / 400))
     *
     * @param ratingX ELO rating of post X
     * @param ratingY ELO rating of post Y
     * @return Expected score (0.0 to 1.0)
     */
    private double calculateExpectedScore(int ratingX, int ratingY) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingY - ratingX) / 400.0));
    }

    /**
     * Calculate K factor weighted by stake and curator quality.
     * Higher stake and curator scores = higher K factor = larger rating changes.
     *
     * @param totalStake Total stake in lamports
     * @param averageCuratorScore Average curator score (0.0 to 1.0+)
     * @return Weighted K factor
     */
    private double calculateKFactor(long totalStake, BigDecimal averageCuratorScore) {
        // Normalize stake (assuming 1 SOL = 1e9 lamports)
        // For every 1 SOL staked, multiply K by 1.1
        double stakeInSol = totalStake / 1_000_000_000.0;
        double stakeMultiplier = 1.0 + (stakeInSol * 0.1);
        stakeMultiplier = Math.min(stakeMultiplier, 2.0); // Cap at 2x

        // Curator score multiplier (0.5x to 1.5x based on score)
        double curatorMultiplier = averageCuratorScore != null
                ? 0.5 + averageCuratorScore.doubleValue()
                : 1.0;
        curatorMultiplier = Math.max(0.5, Math.min(curatorMultiplier, 1.5)); // Clamp to [0.5, 1.5]

        double kFactor = BASE_K_FACTOR * stakeMultiplier * curatorMultiplier;

        return Math.max(8.0, Math.min(kFactor, 64.0)); // Clamp final K to [8, 64]
    }

    /**
     * Initialize ELO for a new post.
     *
     * @param postId ID of the post to initialize
     */
    @Transactional
    public void initializeElo(Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        if (post.getElo() == null) {
            post.setElo(INITIAL_ELO);
            post.setUpdatedAt(OffsetDateTime.now());
            postRepository.save(post);
            log.info("Initialized ELO for post {} to {}", postId, INITIAL_ELO);
        }
    }
}
