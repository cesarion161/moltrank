package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.RoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for RoundOrchestratorService automated round creation and state transitions.
 * Verifies that rounds are created automatically without manual admin intervention.
 */
@ExtendWith(MockitoExtension.class)
class RoundOrchestratorServiceTest {

    @Mock private RoundRepository roundRepository;
    @Mock private MarketRepository marketRepository;
    @Mock private PairGenerationService pairGenerationService;
    @Mock private AutoRevealService autoRevealService;

    @InjectMocks
    private RoundOrchestratorService orchestrator;

    private Market market;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orchestrator, "commitDurationMinutes", 5);
        ReflectionTestUtils.setField(orchestrator, "revealDurationMinutes", 5);
        ReflectionTestUtils.setField(orchestrator, "minCurators", 1);
        ReflectionTestUtils.setField(orchestrator, "autoCreateEnabled", true);
        ReflectionTestUtils.setField(orchestrator, "autoCreateOnStartup", true);

        market = new Market();
        market.setId(1);
        market.setName("tech");
        market.setSubmoltId("tech");
        market.setSubscribers(3);
    }

    // ========================================================================
    // Automatic round creation via scheduler
    // ========================================================================

    @Test
    void checkAndCreateRounds_createsRoundForEligibleMarket() {
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());
        // createNewRound will check active rounds again
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(1);
            return r;
        });
        when(pairGenerationService.generatePairs(any())).thenReturn(List.of(new Pair()));

        orchestrator.checkAndCreateRounds();

        verify(roundRepository, atLeastOnce()).save(any(Round.class));
        verify(pairGenerationService).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_skipsMarketWithActiveOpenRound() {
        Round activeRound = buildRound(1, RoundStatus.OPEN, market);

        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of(activeRound));

        orchestrator.checkAndCreateRounds();

        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_skipsMarketWithActiveCommitRound() {
        Round commitRound = buildRound(1, RoundStatus.COMMIT, market);

        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of(commitRound));

        orchestrator.checkAndCreateRounds();

        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_skipsMarketWithActiveRevealRound() {
        Round revealRound = buildRound(1, RoundStatus.REVEAL, market);

        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of(revealRound));

        orchestrator.checkAndCreateRounds();

        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_skipsMarketWithActiveSettlingRound() {
        Round settlingRound = buildRound(1, RoundStatus.SETTLING, market);

        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of(settlingRound));

        orchestrator.checkAndCreateRounds();

        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_createsRoundWhenOnlySettledRoundsExist() {
        // Settled rounds should NOT block new round creation
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(2);
            return r;
        });
        when(pairGenerationService.generatePairs(any())).thenReturn(List.of(new Pair()));

        orchestrator.checkAndCreateRounds();

        verify(pairGenerationService).generatePairs(any());
    }

    @Test
    void onApplicationReady_runsAutoCreateWhenEnabled() {
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(3);
            return r;
        });
        when(pairGenerationService.generatePairs(any())).thenReturn(List.of(new Pair()));

        orchestrator.onApplicationReady();

        verify(marketRepository).findAll();
        verify(pairGenerationService).generatePairs(any());
    }

    @Test
    void onApplicationReady_skipsAutoCreateWhenDisabled() {
        ReflectionTestUtils.setField(orchestrator, "autoCreateEnabled", false);

        orchestrator.onApplicationReady();

        verifyNoInteractions(marketRepository);
        verifyNoInteractions(pairGenerationService);
    }

    // ========================================================================
    // createNewRound guard conditions
    // ========================================================================

    @Test
    void createNewRound_skipsWhenInsufficientSubscribers() {
        market.setSubscribers(0); // Below minCurators (default 1)

        // No active rounds
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());

        Round result = orchestrator.createNewRound(market);

        assertNull(result);
        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void createNewRound_skipsWhenActiveRoundExists() {
        Round active = buildRound(1, RoundStatus.COMMIT, market);
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of(active));

        Round result = orchestrator.createNewRound(market);

        assertNull(result);
        verify(roundRepository, never()).save(any());
    }

    @Test
    void createNewRound_setsCorrectDeadlines() {
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());

        ArgumentCaptor<Round> roundCaptor = ArgumentCaptor.forClass(Round.class);
        when(roundRepository.save(roundCaptor.capture())).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(1);
            return r;
        });
        when(pairGenerationService.generatePairs(any())).thenReturn(List.of(new Pair()));

        orchestrator.createNewRound(market);

        Round saved = roundCaptor.getAllValues().getFirst();
        assertNotNull(saved.getStartedAt());
        assertNotNull(saved.getCommitDeadline());
        assertNotNull(saved.getRevealDeadline());
        assertEquals(RoundStatus.OPEN, saved.getStatus());
        assertTrue(saved.getCommitDeadline().isAfter(saved.getStartedAt()));
        assertTrue(saved.getRevealDeadline().isAfter(saved.getCommitDeadline()));
    }

    @Test
    void createNewRound_deletesRoundWhenNoPairsGenerated() {
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(1);
            return r;
        });
        when(pairGenerationService.generatePairs(any())).thenReturn(List.of());

        Round result = orchestrator.createNewRound(market);

        assertNull(result);
        verify(roundRepository).delete(any(Round.class));
    }

    // ========================================================================
    // processRoundTransitions - state machine
    // ========================================================================

    @Test
    void processRoundTransitions_transitionsOpenToCommit() {
        Round openRound = buildRound(1, RoundStatus.OPEN, market);
        openRound.setStartedAt(OffsetDateTime.now().minusMinutes(1));
        openRound.setCommitDeadline(OffsetDateTime.now().plusMinutes(4));

        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of(openRound));
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());
        when(marketRepository.findAll()).thenReturn(List.of());

        orchestrator.processRoundTransitions();

        assertEquals(RoundStatus.COMMIT, openRound.getStatus());
        verify(roundRepository).save(openRound);
    }

    @Test
    void processRoundTransitions_transitionsCommitToReveal() {
        Round commitRound = buildRound(1, RoundStatus.COMMIT, market);
        commitRound.setCommitDeadline(OffsetDateTime.now().minusMinutes(1));

        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of(commitRound));
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());
        when(marketRepository.findAll()).thenReturn(List.of());

        orchestrator.processRoundTransitions();

        assertEquals(RoundStatus.REVEAL, commitRound.getStatus());
        verify(autoRevealService).autoRevealCommitments(commitRound);
    }

    @Test
    void processRoundTransitions_transitionsRevealToSettling() {
        Round revealRound = buildRound(1, RoundStatus.REVEAL, market);
        revealRound.setRevealDeadline(OffsetDateTime.now().minusMinutes(1));

        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of(revealRound));
        when(marketRepository.findAll()).thenReturn(List.of());

        orchestrator.processRoundTransitions();

        assertEquals(RoundStatus.SETTLING, revealRound.getStatus());
    }

    @Test
    void processRoundTransitions_callsCheckAndCreateRounds() {
        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(1);
            return r;
        });
        when(pairGenerationService.generatePairs(any())).thenReturn(List.of(new Pair()));

        orchestrator.processRoundTransitions();

        // Verify automatic round creation was triggered
        verify(marketRepository).findAll();
        verify(pairGenerationService).generatePairs(any());
    }

    @Test
    void processRoundTransitions_continuesOnAutoCreateFailure() {
        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());

        Market market2 = new Market();
        market2.setId(2);
        market2.setName("science");
        market2.setSubmoltId("science");
        market2.setSubscribers(5);

        when(marketRepository.findAll()).thenReturn(List.of(market, market2));
        // First market fails, second succeeds
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), any())).thenReturn(List.of());
        when(roundRepository.findByMarketIdAndStatusIn(eq(2), any())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            if (r.getMarket().getId() == 1 && r.getId() == null) {
                r.setId(1);
            } else if (r.getId() == null) {
                r.setId(2);
            }
            return r;
        });
        when(pairGenerationService.generatePairs(any()))
                .thenThrow(new RuntimeException("test failure"))
                .thenReturn(List.of(new Pair()));

        // Should not throw - errors are caught per-market
        assertDoesNotThrow(() -> orchestrator.processRoundTransitions());
    }

    @Test
    void processRoundTransitions_skipsAutoCreateWhenDisabled() {
        ReflectionTestUtils.setField(orchestrator, "autoCreateEnabled", false);
        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());

        orchestrator.processRoundTransitions();

        verifyNoInteractions(marketRepository);
        verifyNoInteractions(pairGenerationService);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Round buildRound(int id, RoundStatus status, Market market) {
        Round round = new Round();
        round.setId(id);
        round.setMarket(market);
        round.setStatus(status);
        round.setPairs(10);
        round.setStartedAt(OffsetDateTime.now().minusHours(1));
        round.setCommitDeadline(OffsetDateTime.now().plusMinutes(5));
        round.setRevealDeadline(OffsetDateTime.now().plusMinutes(10));
        return round;
    }
}
