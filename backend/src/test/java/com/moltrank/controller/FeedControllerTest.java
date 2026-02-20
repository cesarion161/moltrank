package com.moltrank.controller;

import com.moltrank.model.Market;
import com.moltrank.model.Post;
import com.moltrank.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedController.class)
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository postRepository;

    private static final class ByteBuddyInterceptorStub {
    }

    private static final class ProxyMarket extends Market {
        public Object getHibernateLazyInitializer() {
            return new ByteBuddyInterceptorStub();
        }

        public Object getHandler() {
            return new ByteBuddyInterceptorStub();
        }
    }

    private Post buildPost(int id, String agent, int elo, boolean proxyLikeMarket) {
        Market market = proxyLikeMarket ? new ProxyMarket() : new Market();
        market.setId(1);
        market.setName("General");
        market.setSubmoltId("general");

        Post post = new Post();
        post.setId(id);
        post.setMoltbookId("post-" + id);
        post.setMarket(market);
        post.setAgent(agent);
        post.setContent("Content " + id);
        post.setElo(elo);
        post.setMatchups(10 + id);
        post.setWins(5 + id);
        post.setCreatedAt(OffsetDateTime.now().minusMinutes(id));
        post.setUpdatedAt(OffsetDateTime.now().minusMinutes(id));
        return post;
    }

    @Test
    void getFeed_returnsRankedPosts() throws Exception {
        Post higher = buildPost(2, "agent-beta", 1550, false);
        Post lower = buildPost(1, "agent-alpha", 1500, false);

        when(postRepository.findByMarketId(eq(1), any(Pageable.class)))
                .thenReturn(List.of(higher, lower));

        mockMvc.perform(get("/api/feed")
                        .param("marketId", "1")
                        .param("type", "realtime")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].agent").value("agent-beta"))
                .andExpect(jsonPath("$[0].elo").value(1550))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[1].agent").value("agent-alpha"));
    }

    @Test
    void getFeed_handlesProxyLikeMarketWithoutSerialization500() throws Exception {
        Post post = buildPost(1, "agent-alpha", 1500, true);
        when(postRepository.findByMarketId(eq(1), any(Pageable.class)))
                .thenReturn(List.of(post));

        mockMvc.perform(get("/api/feed")
                        .param("marketId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].agent").value("agent-alpha"))
                .andExpect(jsonPath("$[0].market").doesNotExist());
    }
}
