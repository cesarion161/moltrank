package com.moltrank.controller;

import com.moltrank.model.*;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.IdentityRepository;
import com.moltrank.repository.PairRepository;
import com.moltrank.service.PairSelectionService;
import com.moltrank.service.PairSkipService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for PairsController API endpoints.
 * Tests the frontend → backend API flow for pair curation.
 */
@WebMvcTest(PairsController.class)
class PairsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PairRepository pairRepository;

    @MockitoBean
    private PairSelectionService pairSelectionService;

    @MockitoBean
    private CommitmentRepository commitmentRepository;

    @MockitoBean
    private IdentityRepository identityRepository;

    @MockitoBean
    private PairSkipService pairSkipService;

    private static final String WALLET = "4Nd1mYQzvgV8Vr3Z3nYb7pD6T8K9jF2eqWxY1S3Qh5Ro";

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

    private Market buildMarket(boolean proxyLike) {
        Market market = proxyLike ? new ProxyMarket() : new Market();
        market.setId(1);
        market.setName("tech");
        market.setSubmoltId("tech");
        return market;
    }

    private Pair buildPair(boolean proxyLikeMarket) {
        Round round = new Round();
        round.setId(1);
        round.setMarket(buildMarket(proxyLikeMarket));
        round.setStatus(RoundStatus.OPEN);
        round.setCommitDeadline(OffsetDateTime.now().plusMinutes(30));
        round.setRevealDeadline(OffsetDateTime.now().plusHours(1));

        Post postA = new Post();
        postA.setId(1);
        postA.setMoltbookId("post-a-001");
        postA.setAgent("agent-alpha");
        postA.setContent("First post content");
        postA.setElo(1500);
        postA.setMatchups(10);
        postA.setWins(6);
        postA.setMarket(buildMarket(proxyLikeMarket));

        Post postB = new Post();
        postB.setId(2);
        postB.setMoltbookId("post-b-002");
        postB.setAgent("agent-beta");
        postB.setContent("Second post content");
        postB.setElo(1520);
        postB.setMatchups(12);
        postB.setWins(7);
        postB.setMarket(buildMarket(proxyLikeMarket));

        Pair pair = new Pair();
        pair.setId(1);
        pair.setRound(round);
        pair.setPostA(postA);
        pair.setPostB(postB);
        pair.setTotalStake(0L);
        pair.setReward(0L);
        pair.setIsGolden(false);
        pair.setIsAudit(false);
        return pair;
    }

    private Identity buildIdentity(String wallet) {
        Identity identity = new Identity();
        identity.setId(1);
        identity.setWallet(wallet);
        identity.setXAccount("test-account");
        identity.setVerified(true);
        return identity;
    }

    @Test
    void getNextPair_returnsPairForCurator() throws Exception {
        Pair pair = buildPair(false);
        pair.getRound().setStatus(RoundStatus.COMMIT);
        when(pairSelectionService.findNextPairForCurator(WALLET, 1))
                .thenReturn(Optional.of(pair));

        mockMvc.perform(get("/api/pairs/next")
                        .param("wallet", WALLET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roundId").value(1))
                .andExpect(jsonPath("$.postA.id").value(1))
                .andExpect(jsonPath("$.postA.agent").value("agent-alpha"))
                .andExpect(jsonPath("$.postB.id").value(2))
                .andExpect(jsonPath("$.postB.agent").value("agent-beta"))
                .andExpect(jsonPath("$.isGolden").value(false))
                .andExpect(jsonPath("$.isAudit").value(false));
    }

    @Test
    void getNextPair_returns404WhenNoPairsAvailable() throws Exception {
        when(pairSelectionService.findNextPairForCurator(WALLET, 1))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pairs/next")
                        .param("wallet", WALLET))
                .andExpect(status().isNotFound());
    }

    @Test
    void commitPair_createsCommitment() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.of(buildIdentity(WALLET)));

        Commitment saved = new Commitment();
        saved.setId(1);
        saved.setPair(pair);
        saved.setCuratorWallet(WALLET);
        saved.setHash("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab");
        saved.setStake(1000000000L);
        saved.setEncryptedReveal("encrypted-payload");
        saved.setRevealed(false);

        when(commitmentRepository.save(any(Commitment.class))).thenReturn(saved);

        String requestBody = """
                {
                    "curatorWallet": "%s",
                    "hash": "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab",
                    "stake": 1000000000,
                    "encryptedReveal": "encrypted-payload"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/commit", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        ArgumentCaptor<Commitment> captor = ArgumentCaptor.forClass(Commitment.class);
        verify(commitmentRepository).save(captor.capture());
        Commitment persisted = captor.getValue();
        assertEquals(WALLET, persisted.getCuratorWallet());
        assertEquals(saved.getHash(), persisted.getHash());
        assertEquals(saved.getStake(), persisted.getStake());
        assertEquals(saved.getEncryptedReveal(), persisted.getEncryptedReveal());
        assertFalse(persisted.getRevealed());
        assertNotNull(persisted.getCommittedAt());
        assertSame(pair, persisted.getPair());
    }

    @Test
    void commitPair_acceptsFrontendPayloadContract() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.of(buildIdentity(WALLET)));
        when(commitmentRepository.save(any(Commitment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String commitmentHash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab";
        String requestBody = """
                {
                    "wallet": "%s",
                    "commitmentHash": "%s",
                    "stakeAmount": 1000000000,
                    "encryptedReveal": "encrypted-payload"
                }
                """.formatted(WALLET, commitmentHash);

        mockMvc.perform(post("/api/pairs/{id}/commit", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        ArgumentCaptor<Commitment> captor = ArgumentCaptor.forClass(Commitment.class);
        verify(commitmentRepository).save(captor.capture());
        Commitment persisted = captor.getValue();
        assertEquals(WALLET, persisted.getCuratorWallet());
        assertEquals(commitmentHash, persisted.getHash());
        assertEquals(1000000000L, persisted.getStake());
        assertEquals("encrypted-payload", persisted.getEncryptedReveal());
        assertFalse(persisted.getRevealed());
    }

    @Test
    void commitPair_returns400ForInvalidPayload() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));

        String requestBody = """
                {
                    "wallet": "%s",
                    "commitmentHash": "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab",
                    "stakeAmount": 0,
                    "encryptedReveal": ""
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/commit", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commitPair_returns400WhenWalletIdentityDoesNotExist() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.empty());

        String requestBody = """
                {
                    "wallet": "%s",
                    "commitmentHash": "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab",
                    "stakeAmount": 1000000000,
                    "encryptedReveal": "encrypted-payload"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/commit", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commitPair_returns404ForInvalidPair() throws Exception {
        when(pairRepository.findById(999)).thenReturn(Optional.empty());

        String requestBody = """
                {
                    "curatorWallet": "%s",
                    "hash": "0xabcdef",
                    "stake": 1000000000,
                    "encryptedReveal": "encrypted-payload"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/commit", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void getNextPair_handlesProxyLikeEntityGraphWithoutSerialization500() throws Exception {
        Pair pair = buildPair(true);
        pair.getRound().setStatus(RoundStatus.COMMIT);
        when(pairSelectionService.findNextPairForCurator(WALLET, 1))
                .thenReturn(Optional.of(pair));

        mockMvc.perform(get("/api/pairs/next")
                        .param("wallet", WALLET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.postA.agent").value("agent-alpha"))
                .andExpect(jsonPath("$.postA.market").doesNotExist());
    }

    @Test
    void getNextPair_curateFlowTracksRoundTransitions() throws Exception {
        Pair pair = buildPair(false);
        pair.getRound().setStatus(RoundStatus.COMMIT);

        when(pairSelectionService.findNextPairForCurator(WALLET, 1))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pair))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pairs/next")
                        .param("wallet", WALLET))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/pairs/next")
                        .param("wallet", WALLET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(get("/api/pairs/next")
                        .param("wallet", WALLET))
                .andExpect(status().isNotFound());
    }

    @Test
    void commitPair_handlesProxyLikeEntityGraphWithoutSerialization500() throws Exception {
        Pair pair = buildPair(true);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.of(buildIdentity(WALLET)));

        Commitment saved = new Commitment();
        saved.setId(1);
        saved.setPair(pair);
        saved.setCuratorWallet(WALLET);
        saved.setHash("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab");
        saved.setStake(1000000000L);
        saved.setEncryptedReveal("encrypted-payload");
        saved.setRevealed(false);

        when(commitmentRepository.save(any(Commitment.class))).thenReturn(saved);

        String requestBody = """
                {
                    "curatorWallet": "%s",
                    "hash": "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890ab",
                    "stake": 1000000000,
                    "encryptedReveal": "encrypted-payload"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/commit", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
    }

    @Test
    void skipPair_recordsSkipAndReturns204() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.of(buildIdentity(WALLET)));

        String requestBody = """
                {
                    "wallet": "%s"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/skip", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(pairSkipService).skipPair(pair, WALLET);
    }

    @Test
    void skipPair_acceptsLegacyCuratorWalletAlias() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.of(buildIdentity(WALLET)));

        String requestBody = """
                {
                    "curatorWallet": "%s"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/skip", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        verify(pairSkipService).skipPair(pair, WALLET);
    }

    @Test
    void skipPair_returns404ForInvalidPair() throws Exception {
        when(pairRepository.findById(999)).thenReturn(Optional.empty());

        String requestBody = """
                {
                    "wallet": "%s"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/skip", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void skipPair_returns400ForInvalidPayload() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));

        String requestBody = """
                {
                    "wallet": "   "
                }
                """;

        mockMvc.perform(post("/api/pairs/{id}/skip", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void skipPair_returns400WhenWalletIdentityDoesNotExist() throws Exception {
        Pair pair = buildPair(false);
        when(pairRepository.findById(1)).thenReturn(Optional.of(pair));
        when(identityRepository.findByWallet(WALLET)).thenReturn(Optional.empty());

        String requestBody = """
                {
                    "wallet": "%s"
                }
                """.formatted(WALLET);

        mockMvc.perform(post("/api/pairs/{id}/skip", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
