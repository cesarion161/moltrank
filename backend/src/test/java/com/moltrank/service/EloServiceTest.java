package com.moltrank.service;

import com.moltrank.model.Post;
import com.moltrank.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EloServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private EloService eloService;

    private Post winner;
    private Post loser;

    @BeforeEach
    void setUp() {
        winner = new Post();
        winner.setId(1);
        winner.setElo(1500);
        winner.setMoltbookId("mb-1");
        winner.setAgent("AgentA");
        winner.setContent("Content A");

        loser = new Post();
        loser.setId(2);
        loser.setElo(1500);
        loser.setMoltbookId("mb-2");
        loser.setAgent("AgentB");
        loser.setContent("Content B");
    }

    @Test
    void updateElo_equalRatings_winnerGainsLoserLoses() {
        when(postRepository.findById(1)).thenReturn(Optional.of(winner));
        when(postRepository.findById(2)).thenReturn(Optional.of(loser));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        eloService.updateElo(1, 2, 1_000_000_000L, new BigDecimal("0.50"));

        assertTrue(winner.getElo() > 1500, "Winner ELO should increase");
        assertTrue(loser.getElo() < 1500, "Loser ELO should decrease");
        // ELO is zero-sum for equal-rated players
        assertEquals(3000, winner.getElo() + loser.getElo(), "Total ELO should be conserved (approx)");
        verify(postRepository, times(2)).save(any(Post.class));
    }

    @Test
    void updateElo_higherRatedWinner_smallerGain() {
        winner.setElo(1800);
        loser.setElo(1200);

        when(postRepository.findById(1)).thenReturn(Optional.of(winner));
        when(postRepository.findById(2)).thenReturn(Optional.of(loser));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        eloService.updateElo(1, 2, 1_000_000_000L, new BigDecimal("0.50"));

        int winnerGain = winner.getElo() - 1800;
        int loserLoss = 1200 - loser.getElo();

        // Expected outcome for higher-rated winner is small gain
        assertTrue(winnerGain < 10, "Higher-rated winner should gain less, got: " + winnerGain);
        assertTrue(loserLoss < 10, "Lower-rated loser should lose less, got: " + loserLoss);
    }

    @Test
    void updateElo_lowerRatedWinner_largerGain() {
        winner.setElo(1200);
        loser.setElo(1800);

        when(postRepository.findById(1)).thenReturn(Optional.of(winner));
        when(postRepository.findById(2)).thenReturn(Optional.of(loser));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        eloService.updateElo(1, 2, 1_000_000_000L, new BigDecimal("0.50"));

        int winnerGain = winner.getElo() - 1200;
        int loserLoss = 1800 - loser.getElo();

        // Upset: lower-rated winner gets a big gain
        assertTrue(winnerGain > 20, "Lower-rated winner should gain more, got: " + winnerGain);
        assertTrue(loserLoss > 20, "Higher-rated loser should lose more, got: " + loserLoss);
    }

    @Test
    void updateElo_higherStake_largerKFactor() {
        Post winnerLow = new Post();
        winnerLow.setId(3);
        winnerLow.setElo(1500);
        winnerLow.setMoltbookId("mb-3");
        winnerLow.setAgent("Agent");
        winnerLow.setContent("Content");

        Post loserLow = new Post();
        loserLow.setId(4);
        loserLow.setElo(1500);
        loserLow.setMoltbookId("mb-4");
        loserLow.setAgent("Agent");
        loserLow.setContent("Content");

        when(postRepository.findById(1)).thenReturn(Optional.of(winner));
        when(postRepository.findById(2)).thenReturn(Optional.of(loser));
        when(postRepository.findById(3)).thenReturn(Optional.of(winnerLow));
        when(postRepository.findById(4)).thenReturn(Optional.of(loserLow));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        // High stake (10 SOL)
        eloService.updateElo(1, 2, 10_000_000_000L, new BigDecimal("0.50"));
        int highStakeGain = winner.getElo() - 1500;

        // Low stake (0.1 SOL)
        eloService.updateElo(3, 4, 100_000_000L, new BigDecimal("0.50"));
        int lowStakeGain = winnerLow.getElo() - 1500;

        assertTrue(highStakeGain > lowStakeGain,
                "Higher stake should produce larger ELO change: " + highStakeGain + " vs " + lowStakeGain);
    }

    @Test
    void updateElo_stakeMultiplierCappedAt2x() {
        Post w1 = new Post(); w1.setId(5); w1.setElo(1500); w1.setMoltbookId("mb-5"); w1.setAgent("A"); w1.setContent("C");
        Post l1 = new Post(); l1.setId(6); l1.setElo(1500); l1.setMoltbookId("mb-6"); l1.setAgent("B"); l1.setContent("C");
        Post w2 = new Post(); w2.setId(7); w2.setElo(1500); w2.setMoltbookId("mb-7"); w2.setAgent("A"); w2.setContent("C");
        Post l2 = new Post(); l2.setId(8); l2.setElo(1500); l2.setMoltbookId("mb-8"); l2.setAgent("B"); l2.setContent("C");

        when(postRepository.findById(5)).thenReturn(Optional.of(w1));
        when(postRepository.findById(6)).thenReturn(Optional.of(l1));
        when(postRepository.findById(7)).thenReturn(Optional.of(w2));
        when(postRepository.findById(8)).thenReturn(Optional.of(l2));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        // 100 SOL - should hit 2x cap
        eloService.updateElo(5, 6, 100_000_000_000L, new BigDecimal("0.50"));
        int gain100Sol = w1.getElo() - 1500;

        // 1000 SOL - also hits 2x cap, should be same
        eloService.updateElo(7, 8, 1_000_000_000_000L, new BigDecimal("0.50"));
        int gain1000Sol = w2.getElo() - 1500;

        assertEquals(gain100Sol, gain1000Sol, "Both should be capped at 2x stake multiplier");
    }

    @Test
    void updateEloTie_equalRatings_noChange() {
        when(postRepository.findById(1)).thenReturn(Optional.of(winner));
        when(postRepository.findById(2)).thenReturn(Optional.of(loser));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        eloService.updateEloTie(1, 2, 1_000_000_000L, new BigDecimal("0.50"));

        // Equal ratings + tie = no change
        assertEquals(1500, winner.getElo(), "Equal rated tie should not change ELO");
        assertEquals(1500, loser.getElo(), "Equal rated tie should not change ELO");
    }

    @Test
    void updateEloTie_unequalRatings_convergence() {
        winner.setElo(1800);
        loser.setElo(1200);

        when(postRepository.findById(1)).thenReturn(Optional.of(winner));
        when(postRepository.findById(2)).thenReturn(Optional.of(loser));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        eloService.updateEloTie(1, 2, 1_000_000_000L, new BigDecimal("0.50"));

        // Tie between unequal players: higher-rated goes down, lower-rated goes up
        assertTrue(winner.getElo() < 1800, "Higher-rated player should lose rating on tie");
        assertTrue(loser.getElo() > 1200, "Lower-rated player should gain rating on tie");
    }

    @Test
    void updateElo_nullElo_usesInitialValue() {
        winner.setElo(null);
        loser.setElo(null);

        when(postRepository.findById(1)).thenReturn(Optional.of(winner));
        when(postRepository.findById(2)).thenReturn(Optional.of(loser));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        eloService.updateElo(1, 2, 1_000_000_000L, new BigDecimal("0.50"));

        assertNotNull(winner.getElo(), "Winner ELO should be set");
        assertNotNull(loser.getElo(), "Loser ELO should be set");
        assertTrue(winner.getElo() > 1500, "Winner should gain from initial 1500");
    }

    @Test
    void updateElo_postNotFound_throwsException() {
        when(postRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> eloService.updateElo(1, 2, 1_000_000_000L, BigDecimal.ZERO));
    }

    @Test
    void initializeElo_newPost_setsTo1500() {
        Post post = new Post();
        post.setId(1);
        post.setElo(null);
        post.setMoltbookId("mb-1");
        post.setAgent("A");
        post.setContent("C");

        when(postRepository.findById(1)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        eloService.initializeElo(1);

        assertEquals(1500, post.getElo());
        verify(postRepository).save(post);
    }

    @Test
    void initializeElo_existingElo_doesNotOverwrite() {
        Post post = new Post();
        post.setId(1);
        post.setElo(1800);
        post.setMoltbookId("mb-1");
        post.setAgent("A");
        post.setContent("C");

        when(postRepository.findById(1)).thenReturn(Optional.of(post));

        eloService.initializeElo(1);

        assertEquals(1800, post.getElo(), "Should not overwrite existing ELO");
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updateElo_curatorScoreAffectsKFactor() {
        Post w1 = new Post(); w1.setId(10); w1.setElo(1500); w1.setMoltbookId("mb-10"); w1.setAgent("A"); w1.setContent("C");
        Post l1 = new Post(); l1.setId(11); l1.setElo(1500); l1.setMoltbookId("mb-11"); l1.setAgent("B"); l1.setContent("C");
        Post w2 = new Post(); w2.setId(12); w2.setElo(1500); w2.setMoltbookId("mb-12"); w2.setAgent("A"); w2.setContent("C");
        Post l2 = new Post(); l2.setId(13); l2.setElo(1500); l2.setMoltbookId("mb-13"); l2.setAgent("B"); l2.setContent("C");

        when(postRepository.findById(10)).thenReturn(Optional.of(w1));
        when(postRepository.findById(11)).thenReturn(Optional.of(l1));
        when(postRepository.findById(12)).thenReturn(Optional.of(w2));
        when(postRepository.findById(13)).thenReturn(Optional.of(l2));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        // High curator score
        eloService.updateElo(10, 11, 1_000_000_000L, new BigDecimal("1.0"));
        int highCuratorGain = w1.getElo() - 1500;

        // Low curator score
        eloService.updateElo(12, 13, 1_000_000_000L, new BigDecimal("0.0"));
        int lowCuratorGain = w2.getElo() - 1500;

        assertTrue(highCuratorGain > lowCuratorGain,
                "Higher curator score should produce larger ELO change: " + highCuratorGain + " vs " + lowCuratorGain);
    }
}
