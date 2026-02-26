package com.moltrank.clawgic.controller;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.clawgic.dto.ClawgicHealthResponse;
import com.moltrank.clawgic.model.ClawgicSkeletonStatus;
import com.moltrank.clawgic.service.ClawgicHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClawgicHealthController.class)
@Import(ClawgicRuntimeProperties.class)
class ClawgicHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClawgicHealthService clawgicHealthService;

    @Test
    void health_returnsStubPayload() throws Exception {
        when(clawgicHealthService.health()).thenReturn(new ClawgicHealthResponse(
                "clawgic",
                ClawgicSkeletonStatus.STUB,
                false,
                true,
                true));

        mockMvc.perform(get("/api/clawgic/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("clawgic"))
                .andExpect(jsonPath("$.status").value("STUB"))
                .andExpect(jsonPath("$.clawgicEnabled").value(false))
                .andExpect(jsonPath("$.mockProvider").value(true))
                .andExpect(jsonPath("$.mockJudge").value(true));
    }
}
