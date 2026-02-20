package com.moltrank.service;

import com.moltrank.model.Market;
import com.moltrank.model.Post;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScraperServiceTest {

    @Mock
    private MoltbookClient moltbookClient;

    @Mock
    private PostRepository postRepository;

    @Mock
    private MarketRepository marketRepository;

    private ScraperService scraperService;
    private Market market;

    @BeforeEach
    void setUp() {
        scraperService = new ScraperService(moltbookClient, postRepository, marketRepository);

        market = new Market();
        market.setId(1);
        market.setName("Tech");
        market.setSubmoltId("tech");
    }

    @Test
    void scrapePosts_skipsDuplicatesAndPersistsOnlyNewPosts() {
        MoltbookClient.MoltbookPost duplicate = new MoltbookClient.MoltbookPost(
                "mb-dup", "agent-1", "duplicate content", 10, 2, 1);
        MoltbookClient.MoltbookPost fresh = new MoltbookClient.MoltbookPost(
                "mb-new", "agent-2", "fresh content", 20, 3, 4);

        when(moltbookClient.fetchPosts("tech", 25)).thenReturn(List.of(duplicate, fresh));

        Post existingPost = new Post();
        existingPost.setId(11);
        existingPost.setMoltbookId("mb-dup");
        when(postRepository.findByMoltbookId("mb-dup")).thenReturn(Optional.of(existingPost));
        when(postRepository.findByMoltbookId("mb-new")).thenReturn(Optional.empty());

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(22);
            return post;
        });

        List<Post> ingested = scraperService.scrapePosts(market, 25);

        assertEquals(1, ingested.size());
        assertEquals("mb-new", ingested.getFirst().getMoltbookId());
        assertEquals("agent-2", ingested.getFirst().getAgent());
        assertEquals(market, ingested.getFirst().getMarket());
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void scrapePosts_usesDefaultFetchLimit() {
        when(moltbookClient.fetchPosts("tech", 100)).thenReturn(List.of());

        List<Post> ingested = scraperService.scrapePosts(market);

        assertTrue(ingested.isEmpty());
        verify(moltbookClient).fetchPosts("tech", 100);
    }
}
