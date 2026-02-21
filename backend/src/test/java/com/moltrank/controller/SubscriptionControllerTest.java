package com.moltrank.controller;

import com.moltrank.model.Market;
import com.moltrank.model.Round;
import com.moltrank.model.Subscription;
import com.moltrank.model.SubscriptionType;
import com.moltrank.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @Test
    void createSubscription_acceptsLegacyNestedPayloadAndReturnsEconomics() throws Exception {
        SubscriptionService.SubscriptionCreationResult result = buildResult(
                1,
                7_000L,
                SubscriptionType.REALTIME,
                6,
                7_000L,
                7_000L,
                100_000L,
                17
        );
        when(subscriptionService.createSubscription(any())).thenReturn(result);

        String requestBody = """
                {
                  "readerWallet": "reader-wallet-legacy",
                  "amount": 7000,
                  "type": "REALTIME",
                  "market": { "id": 1 },
                  "round": { "id": 17 }
                }
                """;

        mockMvc.perform(post("/api/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.readerWallet").value("reader-wallet-legacy"))
                .andExpect(jsonPath("$.marketId").value(1))
                .andExpect(jsonPath("$.roundId").value(17))
                .andExpect(jsonPath("$.marketSubscribers").value(6))
                .andExpect(jsonPath("$.marketRevenueDelta").value(7000))
                .andExpect(jsonPath("$.poolContribution").value(7000))
                .andExpect(jsonPath("$.poolBalance").value(100000));
    }

    @Test
    void createSubscription_acceptsExplicitIdsPayload() throws Exception {
        SubscriptionService.SubscriptionCreationResult result = buildResult(
                2,
                1_000L,
                SubscriptionType.FREE_DELAY,
                11,
                0L,
                0L,
                250_000L,
                null
        );
        when(subscriptionService.createSubscription(any())).thenReturn(result);

        String requestBody = """
                {
                  "readerWallet": "reader-wallet-modern",
                  "amount": 1000,
                  "type": "FREE_DELAY",
                  "marketId": 2
                }
                """;

        mockMvc.perform(post("/api/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.readerWallet").value("reader-wallet-modern"))
                .andExpect(jsonPath("$.marketId").value(2))
                .andExpect(jsonPath("$.roundId").doesNotExist())
                .andExpect(jsonPath("$.marketSubscribers").value(11))
                .andExpect(jsonPath("$.marketRevenueDelta").value(0))
                .andExpect(jsonPath("$.poolContribution").value(0))
                .andExpect(jsonPath("$.poolBalance").value(250000));
    }

    @Test
    void createSubscription_returns400ForInvalidRequest() throws Exception {
        when(subscriptionService.createSubscription(any()))
                .thenThrow(new IllegalArgumentException("Invalid subscription request"));

        String requestBody = """
                {
                  "readerWallet": "reader-wallet-invalid",
                  "amount": 0,
                  "type": "FREE_DELAY",
                  "marketId": 1
                }
                """;

        mockMvc.perform(post("/api/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private static SubscriptionService.SubscriptionCreationResult buildResult(
            int marketId,
            long amount,
            SubscriptionType type,
            int subscribers,
            long marketRevenueDelta,
            long poolContribution,
            long poolBalance,
            Integer roundId
    ) {
        Market market = new Market();
        market.setId(marketId);
        market.setName("General");
        market.setSubmoltId("general");
        market.setSubscribers(subscribers);
        market.setSubscriptionRevenue(50_000L + marketRevenueDelta);

        Round round = null;
        if (roundId != null) {
            round = new Round();
            round.setId(roundId);
        }

        Subscription subscription = new Subscription();
        subscription.setId(1);
        subscription.setReaderWallet(type == SubscriptionType.REALTIME ? "reader-wallet-legacy" : "reader-wallet-modern");
        subscription.setAmount(amount);
        subscription.setType(type);
        subscription.setMarket(market);
        subscription.setRound(round);
        subscription.setSubscribedAt(OffsetDateTime.now());
        subscription.setExpiresAt(OffsetDateTime.now().plusDays(30));

        return new SubscriptionService.SubscriptionCreationResult(
                subscription,
                market,
                marketRevenueDelta,
                poolContribution,
                poolBalance
        );
    }
}
