package com.moltrank.controller;

import com.moltrank.model.GoldenSetItem;
import com.moltrank.model.Market;
import com.moltrank.model.PairWinner;
import com.moltrank.model.Post;
import com.moltrank.service.GoldenSetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GoldenSetController.class)
class GoldenSetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoldenSetService goldenSetService;

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

    private Post buildPost(int id, String agent, boolean proxyLikeMarket) {
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
        post.setElo(1500 + id);
        post.setMatchups(20 + id);
        post.setWins(10 + id);
        post.setCreatedAt(OffsetDateTime.now().minusDays(id));
        post.setUpdatedAt(OffsetDateTime.now().minusDays(id));
        return post;
    }

    private GoldenSetItem buildItem(boolean proxyLikeMarket) {
        GoldenSetItem item = new GoldenSetItem();
        item.setId(1);
        item.setPostA(buildPost(101, "agent-a", proxyLikeMarket));
        item.setPostB(buildPost(102, "agent-b", proxyLikeMarket));
        item.setCorrectAnswer(PairWinner.A);
        item.setConfidence(new BigDecimal("0.9300"));
        item.setSource("manual");
        item.setCreatedAt(OffsetDateTime.now().minusDays(1));
        return item;
    }

    @Test
    void getAllGoldenSetItems_returnsMappedItems() throws Exception {
        when(goldenSetService.getAllGoldenSetItems())
                .thenReturn(List.of(buildItem(false)));

        mockMvc.perform(get("/api/golden-set"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].correctAnswer").value("A"))
                .andExpect(jsonPath("$[0].postA.id").value(101))
                .andExpect(jsonPath("$[0].postB.id").value(102));
    }

    @Test
    void getAllGoldenSetItems_handlesProxyLikeMarketWithoutSerialization500() throws Exception {
        when(goldenSetService.getAllGoldenSetItems())
                .thenReturn(List.of(buildItem(true)));

        mockMvc.perform(get("/api/golden-set"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].postA.id").value(101))
                .andExpect(jsonPath("$[0].postA.market").doesNotExist());
    }

    @Test
    void addGoldenSetItem_returns201WithoutEchoedBody() throws Exception {
        when(goldenSetService.addGoldenSetItem(any(GoldenSetItem.class)))
                .thenReturn(buildItem(false));

        String requestBody = """
                {
                  "source": "<script>alert(1)</script>",
                  "confidence": 0.9
                }
                """;

        mockMvc.perform(post("/api/golden-set")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
    }
}
