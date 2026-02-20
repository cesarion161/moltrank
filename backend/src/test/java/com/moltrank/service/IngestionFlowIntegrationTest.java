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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionFlowIntegrationTest {

    @Mock
    private MoltbookClient moltbookClient;

    @Mock
    private PostRepository postRepository;

    @Mock
    private MarketRepository marketRepository;

    private IngestionOrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        ScraperService scraperService = new ScraperService(moltbookClient, postRepository, marketRepository);
        orchestratorService = new IngestionOrchestratorService(scraperService, marketRepository);

        ReflectionTestUtils.setField(orchestratorService, "ingestionEnabled", true);
        ReflectionTestUtils.setField(orchestratorService, "runOnStartup", true);
        ReflectionTestUtils.setField(orchestratorService, "fetchLimit", 10);
    }

    @Test
    void runIngestionCycle_triggerPersistsPosts() {
        Market market = new Market();
        market.setId(1);
        market.setName("Tech");
        market.setSubmoltId("tech");

        MoltbookClient.MoltbookPost fetched = new MoltbookClient.MoltbookPost(
                "mb-trigger-1", "agent-trigger", "triggered content", 5, 2, 1);

        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(moltbookClient.fetchPosts("tech", 10)).thenReturn(List.of(fetched));
        when(postRepository.findByMoltbookId("mb-trigger-1")).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(101);
            return post;
        });

        int inserted = orchestratorService.runIngestionCycle("test-trigger");

        assertEquals(1, inserted);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void runIngestionCycle_noopsWhenDisabled() {
        ReflectionTestUtils.setField(orchestratorService, "ingestionEnabled", false);

        int inserted = orchestratorService.runIngestionCycle("test-disabled");

        assertEquals(0, inserted);
        verify(marketRepository, never()).findAll();
    }
}
