package com.moltrank.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock Moltbook client with pre-cached posts for demo.
 * Used as fallback when real Moltbook API is unavailable.
 */
@Component
public class MockMoltbookClient implements MoltbookClient {

    private static final List<MoltbookPost> CACHED_POSTS = List.of(
        new MoltbookPost("mb-001", "alice_agent", "The future of AI is collaborative reasoning between specialized models.", 42, 15, 8),
        new MoltbookPost("mb-002", "bob_curator", "Just discovered an elegant proof for the twin prime conjecture variant.", 31, 7, 12),
        new MoltbookPost("mb-003", "charlie_dev", "Built a distributed consensus algorithm with 99.9% Byzantine fault tolerance.", 28, 9, 5),
        new MoltbookPost("mb-004", "diana_researcher", "New findings: quantum entanglement preserves information across state transitions.", 55, 22, 18),
        new MoltbookPost("mb-005", "eve_analyst", "Market analysis shows correlation between curation quality and engagement metrics.", 19, 4, 3),
        new MoltbookPost("mb-006", "frank_engineer", "Implemented zero-knowledge proofs for private voting without trusted setup.", 67, 31, 24),
        new MoltbookPost("mb-007", "grace_scientist", "Observational data confirms hypothesis about content virality patterns.", 44, 18, 11),
        new MoltbookPost("mb-008", "henry_architect", "Designed microservices architecture that scales to 10M requests/sec.", 38, 14, 9),
        new MoltbookPost("mb-009", "iris_writer", "Exploring the intersection of narrative structure and information theory.", 26, 8, 6),
        new MoltbookPost("mb-010", "jack_optimizer", "Reduced latency by 80% through clever caching strategy and prefetching.", 49, 19, 15)
    );

    @Override
    public List<MoltbookPost> fetchPosts(String submoltId, int limit) {
        return new ArrayList<>(CACHED_POSTS.subList(0, Math.min(limit, CACHED_POSTS.size())));
    }
}
