package com.moltrank.controller;

import com.moltrank.model.Curator;
import com.moltrank.model.CuratorId;
import com.moltrank.repository.CuratorRepository;
import com.moltrank.service.CuratorScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for CuratorController API endpoints.
 * Uses @WebMvcTest slice to test controller layer with mocked services.
 */
@WebMvcTest(CuratorController.class)
class CuratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CuratorRepository curatorRepository;

    @MockitoBean
    private CuratorScoringService curatorScoringService;

    private static final String WALLET = "4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro";

    private Curator buildCurator(String wallet, int marketId) {
        Curator curator = new Curator();
        curator.setWallet(wallet);
        curator.setMarketId(marketId);
        curator.setIdentityId(1);
        curator.setEarned(5000L);
        curator.setLost(1200L);
        curator.setCuratorScore(new BigDecimal("0.8250"));
        curator.setCalibrationRate(new BigDecimal("0.9000"));
        curator.setAuditPassRate(new BigDecimal("0.7500"));
        curator.setAlignmentStability(new BigDecimal("0.8500"));
        curator.setFraudFlags(0);
        curator.setPairsThisEpoch(42);
        return curator;
    }

    @Test
    void getCuratorProfile_returnsCurator() throws Exception {
        Curator curator = buildCurator(WALLET, 1);
        when(curatorRepository.findById(new CuratorId(WALLET, 1)))
                .thenReturn(Optional.of(curator));

        mockMvc.perform(get("/api/curators/{wallet}", WALLET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet").value(WALLET))
                .andExpect(jsonPath("$.earned").value(5000))
                .andExpect(jsonPath("$.lost").value(1200))
                .andExpect(jsonPath("$.curatorScore").value(0.8250))
                .andExpect(jsonPath("$.calibrationRate").value(0.9000))
                .andExpect(jsonPath("$.auditPassRate").value(0.7500))
                .andExpect(jsonPath("$.alignmentStability").value(0.8500))
                .andExpect(jsonPath("$.fraudFlags").value(0));
    }

    @Test
    void getCuratorProfile_returns404WhenNotFound() throws Exception {
        when(curatorRepository.findById(any(CuratorId.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/curators/{wallet}", "unknownWallet"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCuratorProfile_respectsMarketIdParam() throws Exception {
        Curator curator = buildCurator(WALLET, 2);
        when(curatorRepository.findById(new CuratorId(WALLET, 2)))
                .thenReturn(Optional.of(curator));

        mockMvc.perform(get("/api/curators/{wallet}", WALLET)
                        .param("marketId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet").value(WALLET))
                .andExpect(jsonPath("$.marketId").value(2));
    }

    @Test
    void getLeaderboard_returnsRankedCurators() throws Exception {
        Curator c1 = buildCurator("wallet1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 1);
        c1.setCuratorScore(new BigDecimal("0.9500"));
        Curator c2 = buildCurator("wallet2aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 1);
        c2.setCuratorScore(new BigDecimal("0.7000"));

        when(curatorRepository.findByMarketId(eq(1), any(PageRequest.class)))
                .thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].curatorScore").value(0.9500))
                .andExpect(jsonPath("$[1].curatorScore").value(0.7000));
    }

    @Test
    void getLeaderboard_respectsLimitParam() throws Exception {
        Curator c1 = buildCurator("wallet1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 1);

        when(curatorRepository.findByMarketId(eq(1), any(PageRequest.class)))
                .thenReturn(List.of(c1));

        mockMvc.perform(get("/api/leaderboard")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getLeaderboard_returnsEmptyListForNoData() throws Exception {
        when(curatorRepository.findByMarketId(eq(1), any(PageRequest.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
