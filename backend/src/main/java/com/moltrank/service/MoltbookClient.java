package com.moltrank.service;

import java.util.List;

/**
 * Abstraction layer for Moltbook API access.
 * Allows graceful fallback when Moltbook API is unstable.
 */
public interface MoltbookClient {

    /**
     * Fetches posts from a Moltbook submolt.
     *
     * @param submoltId The submolt identifier
     * @param limit Maximum number of posts to fetch
     * @return List of posts from the submolt
     */
    List<MoltbookPost> fetchPosts(String submoltId, int limit);

    /**
     * Data class representing a post from Moltbook.
     */
    record MoltbookPost(
        String moltbookId,
        String agent,
        String content,
        int likes,
        int shares,
        int replies
    ) {}
}
