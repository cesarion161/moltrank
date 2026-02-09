package com.moltrank.controller;

import com.moltrank.model.Post;
import com.moltrank.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for curated feed.
 */
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final PostRepository postRepository;

    public FeedController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * Get ELO-ranked posts feed.
     *
     * @param marketId Market ID
     * @param type Feed type: "realtime" or "delayed"
     * @param limit Optional result limit (defaults to 50)
     * @return List of posts ranked by ELO (descending)
     */
    @GetMapping
    public ResponseEntity<List<Post>> getFeed(
            @RequestParam Integer marketId,
            @RequestParam(defaultValue = "realtime") String type,
            @RequestParam(defaultValue = "50") Integer limit) {

        PageRequest pageRequest = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.DESC, "elo"));

        List<Post> posts = postRepository.findByMarketId(marketId, pageRequest);

        // Note: In a production system, the "delayed" type would filter posts
        // based on subscription status and add delays for freemium users.
        // For now, we return the same feed for both types.

        return ResponseEntity.ok(posts);
    }
}
