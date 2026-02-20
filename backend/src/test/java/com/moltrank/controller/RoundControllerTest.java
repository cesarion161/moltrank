package com.moltrank.controller;

import com.moltrank.model.Market;
import com.moltrank.model.Round;
import com.moltrank.model.RoundStatus;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.RoundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for RoundController API endpoints.
 * Verifies the rounds page can retrieve data without crashes.
 */
@WebMvcTest(RoundController.class)
class RoundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoundRepository roundRepository;

    @MockitoBean
    private CommitmentRepository commitmentRepository;

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

    private Market buildMarket() {
        Market market = new Market();
        market.setId(1);
        market.setName("tech");
        market.setSubmoltId("tech");
        market.setSubscriptionRevenue(1000000L);
        market.setSubscribers(5);
        return market;
    }

    private Market buildProxyMarket() {
        Market market = new ProxyMarket();
        market.setId(1);
        market.setName("tech");
        market.setSubmoltId("tech");
        return market;
    }

    private Round buildRound(int id, RoundStatus status, Market market) {
        Round round = new Round();
        round.setId(id);
        round.setMarket(market);
        round.setStatus(status);
        round.setPairs(10);
        round.setBasePerPair(5000000L);
        round.setPremiumPerPair(2000000L);
        round.setCreatedAt(OffsetDateTime.now());
        round.setStartedAt(OffsetDateTime.now().minusHours(2));
        round.setCommitDeadline(OffsetDateTime.now().minusHours(1));
        round.setRevealDeadline(OffsetDateTime.now().minusMinutes(30));
        return round;
    }

    @Test
    void listRounds_returnsRoundsForMarket() throws Exception {
        Market market = buildMarket();
        Round r1 = buildRound(1, RoundStatus.SETTLED, market);
        r1.setSettledAt(OffsetDateTime.now());
        Round r2 = buildRound(2, RoundStatus.OPEN, market);

        when(roundRepository.findByMarketId(eq(1), any(Sort.class)))
                .thenReturn(List.of(r2, r1));

        mockMvc.perform(get("/api/rounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].market.id").value(1))
                .andExpect(jsonPath("$[0].market.name").value("tech"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].pairs").value(10))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[1].status").value("SETTLED"));
    }

    @Test
    void getActiveRound_returnsLatestActiveRound() throws Exception {
        Market market = buildMarket();
        Round round = buildRound(7, RoundStatus.COMMIT, market);
        round.setPairs(10);

        when(roundRepository.findTopByMarketIdAndStatusInOrderByIdDesc(eq(1), anyList()))
                .thenReturn(Optional.of(round));
        when(commitmentRepository.countDistinctCommittedPairsByRoundId(7))
                .thenReturn(3L);

        mockMvc.perform(get("/api/rounds/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.roundId").value(7))
                .andExpect(jsonPath("$.status").value("COMMIT"))
                .andExpect(jsonPath("$.totalPairs").value(10))
                .andExpect(jsonPath("$.remainingPairs").value(7));
    }

    @Test
    void getActiveRound_returns404WhenNoneExists() throws Exception {
        when(roundRepository.findTopByMarketIdAndStatusInOrderByIdDesc(eq(1), anyList()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rounds/active"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRounds_returnsEmptyListWhenNoRounds() throws Exception {
        when(roundRepository.findByMarketId(eq(1), any(Sort.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/rounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listRounds_respectsMarketIdParam() throws Exception {
        Market market = buildMarket();
        market.setId(2);
        Round round = buildRound(10, RoundStatus.COMMIT, market);

        when(roundRepository.findByMarketId(eq(2), any(Sort.class)))
                .thenReturn(List.of(round));

        mockMvc.perform(get("/api/rounds").param("marketId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void getRoundDetail_returnsRound() throws Exception {
        Market market = buildMarket();
        Round round = buildRound(1, RoundStatus.SETTLED, market);
        round.setSettledAt(OffsetDateTime.now());
        round.setContentMerkleRoot("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");

        when(roundRepository.findById(1)).thenReturn(Optional.of(round));

        mockMvc.perform(get("/api/rounds/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.market.id").value(1))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.pairs").value(10))
                .andExpect(jsonPath("$.basePerPair").value(5000000))
                .andExpect(jsonPath("$.premiumPerPair").value(2000000));
    }

    @Test
    void getRoundDetail_returns404WhenNotFound() throws Exception {
        when(roundRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rounds/{id}", 999))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRounds_includesAllRoundStatuses() throws Exception {
        Market market = buildMarket();
        Round open = buildRound(1, RoundStatus.OPEN, market);
        Round commit = buildRound(2, RoundStatus.COMMIT, market);
        Round reveal = buildRound(3, RoundStatus.REVEAL, market);
        Round settling = buildRound(4, RoundStatus.SETTLING, market);
        Round settled = buildRound(5, RoundStatus.SETTLED, market);

        when(roundRepository.findByMarketId(eq(1), any(Sort.class)))
                .thenReturn(List.of(settled, settling, reveal, commit, open));

        mockMvc.perform(get("/api/rounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].status").value("SETTLED"))
                .andExpect(jsonPath("$[1].status").value("SETTLING"))
                .andExpect(jsonPath("$[2].status").value("REVEAL"))
                .andExpect(jsonPath("$[3].status").value("COMMIT"))
                .andExpect(jsonPath("$[4].status").value("OPEN"));
    }

    @Test
    void listRounds_handlesProxyLikeMarketWithoutSerialization500() throws Exception {
        Round round = buildRound(1, RoundStatus.OPEN, buildProxyMarket());

        when(roundRepository.findByMarketId(eq(1), any(Sort.class)))
                .thenReturn(List.of(round));

        mockMvc.perform(get("/api/rounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].market.name").value("tech"));
    }
}
