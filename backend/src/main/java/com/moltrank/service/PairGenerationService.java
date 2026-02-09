package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.GoldenSetItemRepository;
import com.moltrank.repository.PairRepository;
import com.moltrank.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Demand-gated pair generation service.
 * Implements the maxPairs formula and injects Golden Set and Audit pairs.
 */
@Service
public class PairGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PairGenerationService.class);
    private static final double GOLDEN_SET_RATIO = 0.10; // 10% golden pairs
    private static final double AUDIT_PAIR_RATIO = 0.05; // 5% audit pairs

    @Value("${moltrank.pairs-per-subscriber:5}")
    private int pairsPerSubscriber;

    private final PostRepository postRepository;
    private final PairRepository pairRepository;
    private final GoldenSetItemRepository goldenSetItemRepository;
    private final Random random;

    public PairGenerationService(
            PostRepository postRepository,
            PairRepository pairRepository,
            GoldenSetItemRepository goldenSetItemRepository) {
        this.postRepository = postRepository;
        this.pairRepository = pairRepository;
        this.goldenSetItemRepository = goldenSetItemRepository;
        this.random = new Random();
    }

    /**
     * Generates pairs for a round using demand-gated formula.
     * maxPairs = min(uniquePosts / 2, subscribers × K)
     * where K = pairsPerSubscriber (default 5)
     *
     * @param round The round to generate pairs for
     * @return List of generated pairs
     */
    @Transactional
    public List<Pair> generatePairs(Round round) {
        Market market = round.getMarket();
        log.info("Generating pairs for round {} in market: {}", round.getId(), market.getName());

        // Get candidate posts for this market
        List<Post> candidatePosts = postRepository.findByMarketId(market.getId());
        int uniquePosts = candidatePosts.size();
        int subscribers = market.getSubscribers();

        // Compute max pairs using demand-gated formula
        int maxPairs = computeMaxPairs(uniquePosts, subscribers);
        log.info("Demand-gated formula: uniquePosts={}, subscribers={}, K={}, maxPairs={}",
                uniquePosts, subscribers, pairsPerSubscriber, maxPairs);

        if (maxPairs == 0) {
            log.warn("Zero pairs generated (subscribers={}, posts={})", subscribers, uniquePosts);
            return List.of();
        }

        // Calculate how many of each type
        int goldenCount = (int) Math.ceil(maxPairs * GOLDEN_SET_RATIO);
        int auditCount = (int) Math.ceil(maxPairs * AUDIT_PAIR_RATIO);
        int regularCount = maxPairs - goldenCount - auditCount;

        log.info("Pair distribution: regular={}, golden={}, audit={}", regularCount, goldenCount, auditCount);

        List<Pair> generatedPairs = new ArrayList<>();

        // Generate regular pairs
        generatedPairs.addAll(generateRegularPairs(round, candidatePosts, regularCount));

        // Inject Golden Set pairs
        generatedPairs.addAll(generateGoldenPairs(round, goldenCount));

        // Inject Audit Pairs (swapped duplicates of existing pairs)
        generatedPairs.addAll(generateAuditPairs(round, generatedPairs, auditCount));

        log.info("Successfully generated {} total pairs for round {}", generatedPairs.size(), round.getId());
        return generatedPairs;
    }

    /**
     * Computes the maximum number of pairs using demand-gated formula.
     * maxPairs(M) = min(uniquePosts(M)/2, subscriberCount(M) × K)
     *
     * @param uniquePosts Number of unique posts in the market
     * @param subscribers Number of subscribers to the market
     * @return Maximum number of pairs
     */
    private int computeMaxPairs(int uniquePosts, int subscribers) {
        int postsLimit = uniquePosts / 2;
        int subscriberLimit = subscribers * pairsPerSubscriber;
        return Math.min(postsLimit, subscriberLimit);
    }

    /**
     * Generates regular (non-golden, non-audit) pairs.
     *
     * @param round The round
     * @param candidatePosts Available posts
     * @param count Number of pairs to generate
     * @return List of regular pairs
     */
    private List<Pair> generateRegularPairs(Round round, List<Post> candidatePosts, int count) {
        if (candidatePosts.size() < 2) {
            log.warn("Not enough posts to generate pairs (need at least 2, have {})", candidatePosts.size());
            return List.of();
        }

        List<Pair> pairs = new ArrayList<>();
        Set<String> usedPairSignatures = new HashSet<>();

        int attempts = 0;
        int maxAttempts = count * 10; // Prevent infinite loop

        while (pairs.size() < count && attempts < maxAttempts) {
            attempts++;

            // Randomly select two different posts
            Post postA = candidatePosts.get(random.nextInt(candidatePosts.size()));
            Post postB = candidatePosts.get(random.nextInt(candidatePosts.size()));

            if (postA.getId().equals(postB.getId())) {
                continue; // Same post, try again
            }

            // Create signature to check for duplicates (order-independent)
            String signature = createPairSignature(postA.getId(), postB.getId());
            if (usedPairSignatures.contains(signature)) {
                continue; // Duplicate pair, try again
            }

            // Create pair
            Pair pair = new Pair();
            pair.setRound(round);
            pair.setPostA(postA);
            pair.setPostB(postB);
            pair.setIsGolden(false);
            pair.setIsAudit(false);

            Pair saved = pairRepository.save(pair);
            pairs.add(saved);
            usedPairSignatures.add(signature);
        }

        if (pairs.size() < count) {
            log.warn("Only generated {} regular pairs out of requested {}", pairs.size(), count);
        }

        return pairs;
    }

    /**
     * Generates Golden Set pairs with pre-determined correct answers.
     *
     * @param round The round
     * @param count Number of golden pairs to generate
     * @return List of golden pairs
     */
    private List<Pair> generateGoldenPairs(Round round, int count) {
        List<GoldenSetItem> goldenItems = goldenSetItemRepository.findAll();

        if (goldenItems.isEmpty()) {
            log.warn("No golden set items available, skipping golden pair injection");
            return List.of();
        }

        List<Pair> goldenPairs = new ArrayList<>();
        Collections.shuffle(goldenItems);

        for (int i = 0; i < Math.min(count, goldenItems.size()); i++) {
            GoldenSetItem item = goldenItems.get(i);

            Pair pair = new Pair();
            pair.setRound(round);
            pair.setPostA(item.getPostA());
            pair.setPostB(item.getPostB());
            pair.setIsGolden(true);
            pair.setIsAudit(false);
            pair.setGoldenAnswer(item.getCorrectAnswer());

            Pair saved = pairRepository.save(pair);
            goldenPairs.add(saved);
        }

        log.info("Generated {} golden set pairs", goldenPairs.size());
        return goldenPairs;
    }

    /**
     * Generates Audit Pairs by swapping positions of existing pairs.
     * Detects position-based voting bias.
     *
     * @param round The round
     * @param existingPairs Pairs to create audit versions of
     * @param count Number of audit pairs to generate
     * @return List of audit pairs
     */
    private List<Pair> generateAuditPairs(Round round, List<Pair> existingPairs, int count) {
        if (existingPairs.isEmpty()) {
            log.warn("No existing pairs to create audit pairs from");
            return List.of();
        }

        List<Pair> auditPairs = new ArrayList<>();
        List<Pair> candidateSource = new ArrayList<>(existingPairs);
        Collections.shuffle(candidateSource);

        for (int i = 0; i < Math.min(count, candidateSource.size()); i++) {
            Pair original = candidateSource.get(i);

            // Create swapped version
            Pair audit = new Pair();
            audit.setRound(round);
            audit.setPostA(original.getPostB()); // Swapped
            audit.setPostB(original.getPostA()); // Swapped
            audit.setIsGolden(false);
            audit.setIsAudit(true);

            Pair saved = pairRepository.save(audit);
            auditPairs.add(saved);
        }

        log.info("Generated {} audit pairs", auditPairs.size());
        return auditPairs;
    }

    /**
     * Creates an order-independent signature for a pair.
     *
     * @param postIdA First post ID
     * @param postIdB Second post ID
     * @return Signature string
     */
    private String createPairSignature(Integer postIdA, Integer postIdB) {
        int min = Math.min(postIdA, postIdB);
        int max = Math.max(postIdA, postIdB);
        return min + "-" + max;
    }
}
