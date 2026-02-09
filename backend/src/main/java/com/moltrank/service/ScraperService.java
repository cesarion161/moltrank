package com.moltrank.service;

import com.moltrank.model.Market;
import com.moltrank.model.Post;
import com.moltrank.model.Round;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Post scraper service for ingesting content from Moltbook.
 * Handles post extraction, deduplication, and storage.
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);
    private static final int DEFAULT_FETCH_LIMIT = 100;

    private final MoltbookClient moltbookClient;
    private final PostRepository postRepository;
    private final MarketRepository marketRepository;

    public ScraperService(
            MoltbookClient moltbookClient,
            PostRepository postRepository,
            MarketRepository marketRepository) {
        this.moltbookClient = moltbookClient;
        this.postRepository = postRepository;
        this.marketRepository = marketRepository;
    }

    /**
     * Scrapes posts from Moltbook for a specific market.
     *
     * @param market The market to scrape posts for
     * @return List of newly ingested posts
     */
    @Transactional
    public List<Post> scrapePosts(Market market) {
        return scrapePosts(market, DEFAULT_FETCH_LIMIT);
    }

    /**
     * Scrapes posts from Moltbook for a specific market with a limit.
     *
     * @param market The market to scrape posts for
     * @param limit Maximum number of posts to fetch
     * @return List of newly ingested posts
     */
    @Transactional
    public List<Post> scrapePosts(Market market, int limit) {
        log.info("Starting post scrape for market: {} (submolt: {})", market.getName(), market.getSubmoltId());

        List<MoltbookClient.MoltbookPost> fetchedPosts = moltbookClient.fetchPosts(market.getSubmoltId(), limit);
        log.info("Fetched {} posts from Moltbook", fetchedPosts.size());

        List<Post> newPosts = new ArrayList<>();

        for (MoltbookClient.MoltbookPost moltbookPost : fetchedPosts) {
            // Check for duplicates
            Optional<Post> existing = postRepository.findByMoltbookId(moltbookPost.moltbookId());
            if (existing.isPresent()) {
                log.debug("Skipping duplicate post: {}", moltbookPost.moltbookId());
                continue;
            }

            // Create new post entity
            Post post = new Post();
            post.setMoltbookId(moltbookPost.moltbookId());
            post.setMarket(market);
            post.setAgent(moltbookPost.agent());
            post.setContent(moltbookPost.content());

            Post saved = postRepository.save(post);
            newPosts.add(saved);
            log.debug("Ingested new post: {} from agent: {}", saved.getMoltbookId(), saved.getAgent());
        }

        log.info("Successfully ingested {} new posts for market: {}", newPosts.size(), market.getName());
        return newPosts;
    }

    /**
     * Computes the Merkle root of all posts in a list.
     * Uses SHA-256 content hashes to build the Merkle tree.
     *
     * @param posts List of posts to compute Merkle root for
     * @return Hex-encoded Merkle root (64 characters)
     */
    public String computeMerkleRoot(List<Post> posts) {
        if (posts.isEmpty()) {
            return sha256("empty-tree");
        }

        // Compute content hashes for all posts
        List<String> hashes = posts.stream()
                .map(post -> sha256(post.getContent()))
                .toList();

        // Build Merkle tree
        return buildMerkleTree(hashes);
    }

    /**
     * Logs the Merkle root for a round's content provenance.
     *
     * @param round The round to log Merkle root for
     * @param posts The posts included in the round
     */
    public void logMerkleRoot(Round round, List<Post> posts) {
        String merkleRoot = computeMerkleRoot(posts);
        log.info("Content Merkle Root for Round {} (Market: {}): {}",
                round.getId(),
                round.getMarket().getName(),
                merkleRoot);
    }

    /**
     * Builds a Merkle tree from a list of hashes.
     *
     * @param hashes List of leaf hashes
     * @return Root hash of the Merkle tree
     */
    private String buildMerkleTree(List<String> hashes) {
        List<String> currentLevel = new ArrayList<>(hashes);

        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();

            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;
                nextLevel.add(sha256(left + right));
            }

            currentLevel = nextLevel;
        }

        return currentLevel.get(0);
    }

    /**
     * Computes SHA-256 hash of a string.
     *
     * @param input The input string
     * @return Hex-encoded SHA-256 hash
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
