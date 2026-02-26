package com.moltrank.clawgic.web;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(com.moltrank.controller.FeedController.class)
@Import({LegacyMoltRankRouteWarningConfig.class, ClawgicRuntimeProperties.class})
class LegacyMoltRankRouteWarningInterceptorWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClawgicRuntimeProperties clawgicRuntimeProperties;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void legacyApiRouteRespondsAndIsTaggedWhenClawgicModeEnabled() throws Exception {
        clawgicRuntimeProperties.setEnabled(true);
        clawgicRuntimeProperties.setLegacyMoltRankApiEnabled(true);

        when(postRepository.findByMarketId(eq(1), any(Pageable.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/feed")
                        .param("marketId", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string(LegacyMoltRankRouteWarningInterceptor.LEGACY_ROUTE_HEADER, "true"))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
