package com.moltrank.controller;

import com.moltrank.model.Market;
import com.moltrank.model.Post;
import com.moltrank.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

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

    private Post buildPost(int id, int elo, int matchups, int wins, boolean proxyLikeMarket) {
        Market market = proxyLikeMarket ? new ProxyMarket() : new Market();
        market.setId(1);
        market.setName("General");
        market.setSubmoltId("general");

        Post post = new Post();
        post.setId(id);
        post.setMoltbookId("post-" + id);
        post.setMarket(market);
        post.setAgent("agent-alpha");
        post.setContent("Content " + id);
        post.setElo(elo);
        post.setMatchups(matchups);
        post.setWins(wins);
        post.setCreatedAt(OffsetDateTime.now().minusDays(id));
        post.setUpdatedAt(OffsetDateTime.now().minusDays(id));
        return post;
    }

    @Test
    void getAgentProfile_returnsAggregatedStatsAndPosts() throws Exception {
        Post post1 = buildPost(1, 1500, 10, 6, false);
        Post post2 = buildPost(2, 1600, 20, 8, false);
        when(postRepository.findByAgent("agent-alpha")).thenReturn(List.of(post1, post2));

        mockMvc.perform(get("/api/agents/{id}", "agent-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-alpha"))
                .andExpect(jsonPath("$.totalPosts").value(2))
                .andExpect(jsonPath("$.totalMatchups").value(30))
                .andExpect(jsonPath("$.totalWins").value(14))
                .andExpect(jsonPath("$.avgElo").value(1550))
                .andExpect(jsonPath("$.maxElo").value(1600))
                .andExpect(jsonPath("$.winRate").value(14d / 30d))
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].id").value(1))
                .andExpect(jsonPath("$.posts[1].id").value(2));
    }

    @Test
    void getAgentProfile_returns404WhenNoPosts() throws Exception {
        when(postRepository.findByAgent("unknown-agent")).thenReturn(List.of());

        mockMvc.perform(get("/api/agents/{id}", "unknown-agent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAgentProfile_handlesProxyLikeMarketWithoutSerialization500() throws Exception {
        Post post = buildPost(1, 1500, 10, 6, true);
        when(postRepository.findByAgent("agent-alpha")).thenReturn(List.of(post));

        mockMvc.perform(get("/api/agents/{id}", "agent-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent-alpha"))
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(1))
                .andExpect(jsonPath("$.posts[0].market").doesNotExist());
    }
}
