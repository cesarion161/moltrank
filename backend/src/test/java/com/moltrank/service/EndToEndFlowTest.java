package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-end regression test verifying the full flow:
 * ingestion -> round creation -> pair generation -> voting -> settlement -> scoring
 * Uses mocked repositories to test service layer integration without DB.
 */
@ExtendWith(MockitoExtension.class)
class EndToEndFlowTest {

    @Mock private PostRepository postRepository;
    @Mock private PairRepository pairRepository;
    @Mock private GoldenSetItemRepository goldenSetItemRepository;
    @Mock private CommitmentRepository commitmentRepository;
    @Mock private CuratorRepository curatorRepository;
    @Mock private GlobalPoolRepository globalPoolRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private MarketRepository marketRepository;

    private EloService eloService;
    private PoolService poolService;
    private CuratorScoringService curatorScoringService;
    private PairGenerationService pairGenerationService;
    private SettlementService settlementService;

    private Market market;
    private GlobalPool globalPool;
    private List<Post> posts;

    @BeforeEach
    void setUp() {
        // Wire up services
        eloService = new EloService(postRepository);
        poolService = new PoolService(globalPoolRepository, marketRepository);
        curatorScoringService = new CuratorScoringService(curatorRepository);
        pairGenerationService = new PairGenerationService(postRepository, pairRepository, goldenSetItemRepository);
        settlementService = new SettlementService(
                pairRepository, commitmentRepository, curatorRepository,
                globalPoolRepository, roundRepository,
                poolService, eloService);
        ReflectionTestUtils.setField(settlementService, "gracePeriodMinutes", 30);

        ReflectionTestUtils.setField(pairGenerationService, "pairsPerSubscriber", 5);

        // Set up market
        market = new Market();
        market.setId(1);
        market.setName("tech");
        market.setSubmoltId("tech");
        market.setSubscriptionRevenue(5_000_000_000L);
        market.setSubscribers(10);

        // Set up pool
        globalPool = new GlobalPool();
        globalPool.setId(1);
        globalPool.setBalance(100_000_000_000L); // 100 SOL
        globalPool.setAlpha(new BigDecimal("0.30"));

        // Simulate ingested posts
        posts = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Post post = new Post();
            post.setId(i);
            post.setMoltbookId("mb-" + i);
            post.setAgent("Agent" + (i % 5 + 1));
            post.setContent("Ingested content for post " + i);
            post.setElo(1500);
            post.setMarket(market);
            posts.add(post);
        }
    }

    @Test
    void fullFlow_ingestThroughSettlement() {
        // === STEP 1: Ingestion (posts already created above) ===
        assertEquals(20, posts.size(), "Should have 20 ingested posts");

        // === STEP 2: Round creation ===
        Round round = new Round();
        round.setId(1);
        round.setMarket(market);
        round.setStatus(RoundStatus.OPEN);
        round.setPairs(0);
        round.setCommitDeadline(OffsetDateTime.now().plusHours(1));
        round.setRevealDeadline(OffsetDateTime.now().plusHours(2));

        // === STEP 3: Pair generation ===
        when(postRepository.findByMarketId(1)).thenReturn(posts);
        when(goldenSetItemRepository.findAll()).thenReturn(List.of());
        when(pairRepository.save(any(Pair.class))).thenAnswer(inv -> {
            Pair p = inv.getArgument(0);
            p.setId((int) (Math.random() * 10000) + 1);
            return p;
        });

        List<Pair> generatedPairs = pairGenerationService.generatePairs(round);

        // maxPairs = min(20/2, 10*5) = 10, but golden items unavailable -> 9
        assertTrue(generatedPairs.size() >= 8 && generatedPairs.size() <= 10,
                "Should generate approximately maxPairs pairs, got: " + generatedPairs.size());
        round.setPairs(generatedPairs.size());

        // Verify pair composition
        long regularCount = generatedPairs.stream().filter(p -> !p.getIsGolden() && !p.getIsAudit()).count();
        long goldenCount = generatedPairs.stream().filter(Pair::getIsGolden).count();
        long auditCount = generatedPairs.stream().filter(Pair::getIsAudit).count();
        assertEquals(generatedPairs.size(), regularCount + goldenCount + auditCount,
                "All pairs should be categorized as regular, golden, or audit");

        // === STEP 4: Voting (simulate commitments) ===
        // Take first regular pair for detailed settlement test
        Pair testPair = generatedPairs.stream()
                .filter(p -> !p.getIsGolden() && !p.getIsAudit())
                .findFirst()
                .orElseThrow();

        // Create 3 curators who vote: 2 for A, 1 for B
        Curator curator1 = createCurator("wallet-1", 1, 1, new BigDecimal("0.80"));
        Curator curator2 = createCurator("wallet-2", 1, 2, new BigDecimal("0.70"));
        Curator curator3 = createCurator("wallet-3", 1, 3, new BigDecimal("0.60"));

        Commitment commit1 = createCommitment(testPair, "wallet-1", PairWinner.A, 1_000_000_000L, true);
        Commitment commit2 = createCommitment(testPair, "wallet-2", PairWinner.A, 2_000_000_000L, true);
        Commitment commit3 = createCommitment(testPair, "wallet-3", PairWinner.B, 1_500_000_000L, true);

        // === STEP 5: Settlement ===
        round.setStatus(RoundStatus.SETTLING);

        when(roundRepository.findById(1)).thenReturn(Optional.of(round));
        when(roundRepository.findByStatus(RoundStatus.SETTLING)).thenReturn(List.of(round));
        when(globalPoolRepository.findById(1)).thenReturn(Optional.of(globalPool));
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(pairRepository.findByRoundId(1)).thenReturn(List.of(testPair));
        when(commitmentRepository.findByPairId(testPair.getId())).thenReturn(List.of(commit1, commit2, commit3));
        when(curatorRepository.findById(any(CuratorId.class))).thenAnswer(inv -> {
            CuratorId id = inv.getArgument(0);
            if ("wallet-1".equals(id.getWallet())) return Optional.of(curator1);
            if ("wallet-2".equals(id.getWallet())) return Optional.of(curator2);
            if ("wallet-3".equals(id.getWallet())) return Optional.of(curator3);
            return Optional.empty();
        });

        // Mock post lookups for ELO update
        when(postRepository.findById(testPair.getPostA().getId())).thenReturn(Optional.of(testPair.getPostA()));
        when(postRepository.findById(testPair.getPostB().getId())).thenReturn(Optional.of(testPair.getPostB()));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pairRepository.save(any(Pair.class))).thenAnswer(inv -> inv.getArgument(0));
        when(curatorRepository.save(any(Curator.class))).thenAnswer(inv -> inv.getArgument(0));
        when(globalPoolRepository.save(any(GlobalPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> inv.getArgument(0));

        String settlementHash = settlementService.settleRound(1);

        // Verify settlement outcomes
        assertNotNull(settlementHash, "Settlement should produce a hash");
        assertTrue(settlementHash.startsWith("0x"), "Hash should be hex-formatted");
        assertEquals(66, settlementHash.length(), "SHA-256 hex = 64 chars + 0x prefix");

        assertEquals(RoundStatus.SETTLED, round.getStatus(), "Round should be marked SETTLED");
        assertNotNull(round.getSettledAt(), "settledAt should be set");

        // Verify ELO was updated (majority was A)
        assertEquals(PairWinner.A, testPair.getWinner(), "Majority vote should be A");

        // Post A wins -> ELO should go up
        assertTrue(testPair.getPostA().getElo() >= 1500, "Winner post ELO should not decrease");

        // Majority voters should have earned
        assertTrue(curator1.getEarned() > 0 || curator2.getEarned() > 0,
                "Majority voters should earn rewards");

        // Minority voter should have lost (20% penalty)
        assertTrue(curator3.getLost() > 0, "Minority voter should incur loss");

        // === STEP 6: Scoring ===
        BigDecimal score1 = curatorScoringService.calculateCuratorScore(curator1);
        assertNotNull(score1, "Curator score should be calculable");
        assertTrue(score1.compareTo(BigDecimal.ZERO) >= 0, "Score should be non-negative");
    }

    @Test
    void fullFlow_nonRevealPenalty() {
        Round round = new Round();
        round.setId(2);
        round.setMarket(market);
        round.setStatus(RoundStatus.SETTLING);
        round.setPairs(1);
        round.setCommitDeadline(OffsetDateTime.now().minusMinutes(40));

        Pair pair = new Pair();
        pair.setId(100);
        pair.setRound(round);
        pair.setPostA(posts.get(0));
        pair.setPostB(posts.get(1));
        pair.setIsGolden(false);
        pair.setIsAudit(false);

        Curator revealedCurator = createCurator("wallet-revealed", 1, 10, new BigDecimal("0.80"));
        Curator nonRevealCurator = createCurator("wallet-nonreveal", 1, 11, new BigDecimal("0.60"));

        Commitment revealed = createCommitment(pair, "wallet-revealed", PairWinner.A, 1_000_000_000L, true);
        Commitment nonRevealed = createCommitment(pair, "wallet-nonreveal", null, 2_000_000_000L, false);
        nonRevealed.setCommittedAt(OffsetDateTime.now().minusMinutes(50));

        when(roundRepository.findById(2)).thenReturn(Optional.of(round));
        when(roundRepository.findByStatus(RoundStatus.SETTLING)).thenReturn(List.of(round));
        when(globalPoolRepository.findById(1)).thenReturn(Optional.of(globalPool));
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(pairRepository.findByRoundId(2)).thenReturn(List.of(pair));
        when(commitmentRepository.findByPairId(100)).thenReturn(List.of(revealed, nonRevealed));
        when(curatorRepository.findById(any(CuratorId.class))).thenAnswer(inv -> {
            CuratorId id = inv.getArgument(0);
            if ("wallet-revealed".equals(id.getWallet())) return Optional.of(revealedCurator);
            if ("wallet-nonreveal".equals(id.getWallet())) return Optional.of(nonRevealCurator);
            return Optional.empty();
        });
        when(postRepository.findById(posts.get(0).getId())).thenReturn(Optional.of(posts.get(0)));
        when(postRepository.findById(posts.get(1).getId())).thenReturn(Optional.of(posts.get(1)));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pairRepository.save(any(Pair.class))).thenAnswer(inv -> inv.getArgument(0));
        when(curatorRepository.save(any(Curator.class))).thenAnswer(inv -> inv.getArgument(0));
        when(globalPoolRepository.save(any(GlobalPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> inv.getArgument(0));

        String hash = settlementService.settleRound(2);

        assertNotNull(hash);
        // Non-reveal curator loses 100% of stake
        assertEquals(2_000_000_000L, nonRevealCurator.getLost(),
                "Non-reveal should lose 100% stake (2 SOL)");
        assertTrue(nonRevealed.getNonRevealPenalized(), "Non-reveal penalty should be marked on commitment");
    }

    @Test
    void fullFlow_nonRevealPenaltyWaitsForFailureGraceWindow() {
        Round round = new Round();
        round.setId(6);
        round.setMarket(market);
        round.setStatus(RoundStatus.SETTLING);
        round.setPairs(10);
        round.setCommitDeadline(OffsetDateTime.now().minusHours(2));
        round.setRevealDeadline(OffsetDateTime.now().minusHours(1));

        Pair pair = new Pair();
        pair.setId(102);
        pair.setRound(round);
        pair.setPostA(posts.get(0));
        pair.setPostB(posts.get(1));
        pair.setIsGolden(false);
        pair.setIsAudit(false);

        Curator revealedCurator = createCurator("wallet-revealed-3", 1, 14, new BigDecimal("0.80"));
        Curator nonRevealCurator = createCurator("wallet-nonreveal-3", 1, 15, new BigDecimal("0.60"));

        Commitment revealed = createCommitment(pair, "wallet-revealed-3", PairWinner.A, 1_000_000_000L, true);
        Commitment nonRevealed = createCommitment(pair, "wallet-nonreveal-3", null, 2_000_000_000L, false);
        nonRevealed.setCommittedAt(OffsetDateTime.now().minusHours(3));
        nonRevealed.setAutoRevealFailedAt(OffsetDateTime.now().minusMinutes(10));

        when(roundRepository.findById(6)).thenReturn(Optional.of(round));
        when(roundRepository.findByStatus(RoundStatus.SETTLING)).thenReturn(List.of(round));
        when(globalPoolRepository.findById(1)).thenReturn(Optional.of(globalPool));
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(pairRepository.findByRoundId(6)).thenReturn(List.of(pair));
        when(commitmentRepository.findByPairId(102)).thenReturn(List.of(revealed, nonRevealed));
        when(curatorRepository.findById(any(CuratorId.class))).thenAnswer(inv -> {
            CuratorId id = inv.getArgument(0);
            if ("wallet-revealed-3".equals(id.getWallet())) return Optional.of(revealedCurator);
            if ("wallet-nonreveal-3".equals(id.getWallet())) return Optional.of(nonRevealCurator);
            return Optional.empty();
        });
        when(postRepository.findById(posts.get(0).getId())).thenReturn(Optional.of(posts.get(0)));
        when(postRepository.findById(posts.get(1).getId())).thenReturn(Optional.of(posts.get(1)));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pairRepository.save(any(Pair.class))).thenAnswer(inv -> inv.getArgument(0));
        when(curatorRepository.save(any(Curator.class))).thenAnswer(inv -> inv.getArgument(0));
        when(globalPoolRepository.save(any(GlobalPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> inv.getArgument(0));

        String hash = settlementService.settleRound(6);

        assertNotNull(hash);
        assertEquals(0L, nonRevealCurator.getLost(),
                "Non-reveal should not be penalized before grace window from auto-reveal failure expires");
        assertFalse(nonRevealed.getNonRevealPenalized(),
                "Commitment should remain unpenalized while within grace window");
    }

    @Test
    void fullFlow_nonRevealAlreadyPenalized_notDoubleCharged() {
        Round round = new Round();
        round.setId(5);
        round.setMarket(market);
        round.setStatus(RoundStatus.SETTLING);
        round.setPairs(10);
        round.setCommitDeadline(OffsetDateTime.now().minusMinutes(40));

        Pair pair = new Pair();
        pair.setId(101);
        pair.setRound(round);
        pair.setPostA(posts.get(0));
        pair.setPostB(posts.get(1));
        pair.setIsGolden(false);
        pair.setIsAudit(false);

        Curator revealedCurator = createCurator("wallet-revealed-2", 1, 12, new BigDecimal("0.80"));
        Curator nonRevealCurator = createCurator("wallet-nonreveal-2", 1, 13, new BigDecimal("0.60"));
        nonRevealCurator.setLost(2_000_000_000L);

        Commitment revealed = createCommitment(pair, "wallet-revealed-2", PairWinner.A, 1_000_000_000L, true);
        Commitment nonRevealed = createCommitment(pair, "wallet-nonreveal-2", null, 2_000_000_000L, false);
        nonRevealed.setCommittedAt(OffsetDateTime.now().minusMinutes(50));
        nonRevealed.setNonRevealPenalized(true);
        nonRevealed.setNonRevealPenalizedAt(OffsetDateTime.now().minusMinutes(5));

        when(roundRepository.findById(5)).thenReturn(Optional.of(round));
        when(roundRepository.findByStatus(RoundStatus.SETTLING)).thenReturn(List.of(round));
        when(globalPoolRepository.findById(1)).thenReturn(Optional.of(globalPool));
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(pairRepository.findByRoundId(5)).thenReturn(List.of(pair));
        when(commitmentRepository.findByPairId(101)).thenReturn(List.of(revealed, nonRevealed));
        when(curatorRepository.findById(any(CuratorId.class))).thenAnswer(inv -> {
            CuratorId id = inv.getArgument(0);
            if ("wallet-revealed-2".equals(id.getWallet())) return Optional.of(revealedCurator);
            if ("wallet-nonreveal-2".equals(id.getWallet())) return Optional.of(nonRevealCurator);
            return Optional.empty();
        });
        when(postRepository.findById(posts.get(0).getId())).thenReturn(Optional.of(posts.get(0)));
        when(postRepository.findById(posts.get(1).getId())).thenReturn(Optional.of(posts.get(1)));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pairRepository.save(any(Pair.class))).thenAnswer(inv -> inv.getArgument(0));
        when(curatorRepository.save(any(Curator.class))).thenAnswer(inv -> inv.getArgument(0));
        when(globalPoolRepository.save(any(GlobalPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> inv.getArgument(0));

        String hash = settlementService.settleRound(5);

        assertNotNull(hash);
        assertEquals(2_000_000_000L, nonRevealCurator.getLost(),
                "Already-penalized non-reveal should not be charged twice");
    }

    @Test
    void fullFlow_idempotentSettlement() {
        Round round = new Round();
        round.setId(3);
        round.setMarket(market);
        round.setStatus(RoundStatus.SETTLED); // Already settled

        globalPool.setSettlementHash("0xabc123");

        when(roundRepository.findById(3)).thenReturn(Optional.of(round));
        when(globalPoolRepository.findById(1)).thenReturn(Optional.of(globalPool));

        String hash = settlementService.settleRound(3);

        assertEquals("0xabc123", hash, "Should return existing hash for already-settled round");
        verify(pairRepository, never()).findByRoundId(any());
    }

    @Test
    void fullFlow_wrongStatusThrows() {
        Round round = new Round();
        round.setId(4);
        round.setMarket(market);
        round.setStatus(RoundStatus.COMMIT); // Wrong status

        when(roundRepository.findById(4)).thenReturn(Optional.of(round));

        assertThrows(IllegalStateException.class,
                () -> settlementService.settleRound(4));
    }

    // === Helpers ===

    private Curator createCurator(String wallet, int marketId, int identityId, BigDecimal score) {
        Curator c = new Curator();
        c.setWallet(wallet);
        c.setMarketId(marketId);
        c.setIdentityId(identityId);
        c.setCuratorScore(score);
        c.setCalibrationRate(score);
        c.setAlignmentStability(score);
        c.setAuditPassRate(score);
        c.setFraudFlags(0);
        c.setEarned(0L);
        c.setLost(0L);
        return c;
    }

    private Commitment createCommitment(Pair pair, String wallet, PairWinner choice, long stake, boolean revealed) {
        Commitment c = new Commitment();
        c.setId((int) (Math.random() * 10000));
        c.setPair(pair);
        c.setCuratorWallet(wallet);
        c.setChoice(choice);
        c.setStake(stake);
        c.setRevealed(revealed);
        c.setHash("0x" + wallet.hashCode());
        c.setEncryptedReveal("encrypted");
        return c;
    }
}
