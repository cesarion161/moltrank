package com.moltrank.service;

import com.moltrank.model.*;
import com.moltrank.repository.MarketRepository;
import com.moltrank.repository.PairRepository;
import com.moltrank.repository.RoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundOrchestratorServiceTest {

    @Mock private RoundRepository roundRepository;
    @Mock private MarketRepository marketRepository;
    @Mock private PairRepository pairRepository;
    @Mock private PairGenerationService pairGenerationService;
    @Mock private AutoRevealService autoRevealService;

    @InjectMocks
    private RoundOrchestratorService service;

    private Market market;

    @BeforeEach
    void setUp() throws Exception {
        market = new Market();
        market.setId(1);
        market.setName("test-market");
        market.setSubmoltId("test");
        market.setSubscribers(3);

        setField(service, "commitDurationMinutes", 5);
        setField(service, "revealDurationMinutes", 5);
        setField(service, "minCurators", 1);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // --- checkAndCreateRounds: automated trigger ---

    @Test
    void checkAndCreateRounds_createsRoundForEligibleMarket() {
        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(100);
            return r;
        });

        Pair mockPair = new Pair();
        when(pairGenerationService.generatePairs(any(Round.class))).thenReturn(List.of(mockPair));

        service.checkAndCreateRounds();

        verify(roundRepository, atLeastOnce()).save(any(Round.class));
        verify(pairGenerationService).generatePairs(any(Round.class));
    }

    @Test
    void checkAndCreateRounds_skipsMarketWithActiveRound() {
        Round activeRound = new Round();
        activeRound.setStatus(RoundStatus.COMMIT);

        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of(activeRound));

        service.checkAndCreateRounds();

        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_skipsMarketWithInsufficientSubscribers() {
        market.setSubscribers(0);

        when(marketRepository.findAll()).thenReturn(List.of(market));
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of());

        service.checkAndCreateRounds();

        // createNewRound is called but returns null due to subscriber check
        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_handlesMultipleMarkets() {
        Market market2 = new Market();
        market2.setId(2);
        market2.setName("market-2");
        market2.setSubmoltId("test2");
        market2.setSubscribers(5);

        Round activeRound = new Round();
        activeRound.setStatus(RoundStatus.REVEAL);

        when(marketRepository.findAll()).thenReturn(List.of(market, market2));
        // market 1: has active round
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of(activeRound));
        // market 2: no active round
        when(roundRepository.findByMarketIdAndStatusIn(eq(2), anyList())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(200);
            return r;
        });

        Pair mockPair = new Pair();
        when(pairGenerationService.generatePairs(any(Round.class))).thenReturn(List.of(mockPair));

        service.checkAndCreateRounds();

        // Only market 2 should get a round created
        verify(pairGenerationService, times(1)).generatePairs(any());
    }

    @Test
    void checkAndCreateRounds_continuesAfterFailure() {
        Market market2 = new Market();
        market2.setId(2);
        market2.setName("market-2");
        market2.setSubmoltId("test2");
        market2.setSubscribers(5);

        when(marketRepository.findAll()).thenReturn(List.of(market, market2));
        when(roundRepository.findByMarketIdAndStatusIn(anyInt(), anyList())).thenReturn(List.of());

        // First market throws exception
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            if (r.getMarket().getId() == 1) {
                throw new RuntimeException("DB error");
            }
            r.setId(200);
            return r;
        });

        service.checkAndCreateRounds();

        // Should not abort - market 2 is still attempted
        verify(roundRepository, atLeast(2)).save(any(Round.class));
    }

    // --- createNewRound ---

    @Test
    void createNewRound_setsCorrectDeadlines() {
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(100);
            return r;
        });

        Pair mockPair = new Pair();
        when(pairGenerationService.generatePairs(any(Round.class))).thenReturn(List.of(mockPair));

        Round created = service.createNewRound(market);

        assertNotNull(created);
        assertEquals(RoundStatus.OPEN, created.getStatus());
        assertNotNull(created.getStartedAt());
        assertNotNull(created.getCommitDeadline());
        assertNotNull(created.getRevealDeadline());

        // Commit deadline = startedAt + 5 minutes
        assertEquals(created.getStartedAt().plusMinutes(5), created.getCommitDeadline());
        // Reveal deadline = commitDeadline + 5 minutes
        assertEquals(created.getCommitDeadline().plusMinutes(5), created.getRevealDeadline());
    }

    @Test
    void createNewRound_blockedByCommitPhaseRound() {
        Round commitRound = new Round();
        commitRound.setStatus(RoundStatus.COMMIT);

        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of(commitRound));

        Round result = service.createNewRound(market);

        assertNull(result);
        verify(pairGenerationService, never()).generatePairs(any());
    }

    @Test
    void createNewRound_blockedBySettlingPhaseRound() {
        Round settlingRound = new Round();
        settlingRound.setStatus(RoundStatus.SETTLING);

        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of(settlingRound));

        Round result = service.createNewRound(market);

        assertNull(result);
    }

    @Test
    void createNewRound_allowedAfterSettled() {
        // SETTLED rounds should NOT block new round creation
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(100);
            return r;
        });

        Pair mockPair = new Pair();
        when(pairGenerationService.generatePairs(any(Round.class))).thenReturn(List.of(mockPair));

        Round result = service.createNewRound(market);

        assertNotNull(result);
    }

    @Test
    void createNewRound_deletesRoundIfNoPairsGenerated() {
        when(roundRepository.findByMarketIdAndStatusIn(eq(1), anyList())).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round r = inv.getArgument(0);
            r.setId(100);
            return r;
        });
        when(pairGenerationService.generatePairs(any(Round.class))).thenReturn(List.of());

        Round result = service.createNewRound(market);

        assertNull(result);
        verify(roundRepository).delete(any(Round.class));
    }

    // --- processRoundTransitions ---

    @Test
    void processRoundTransitions_transitionsOpenToCommit() {
        Round openRound = new Round();
        openRound.setId(1);
        openRound.setStatus(RoundStatus.OPEN);
        openRound.setStartedAt(OffsetDateTime.now().minusMinutes(1));
        openRound.setCommitDeadline(OffsetDateTime.now().plusMinutes(4));

        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of(openRound));
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processRoundTransitions();

        ArgumentCaptor<Round> captor = ArgumentCaptor.forClass(Round.class);
        verify(roundRepository).save(captor.capture());
        assertEquals(RoundStatus.COMMIT, captor.getValue().getStatus());
    }

    @Test
    void processRoundTransitions_transitionsCommitToReveal() {
        Round commitRound = new Round();
        commitRound.setId(2);
        commitRound.setStatus(RoundStatus.COMMIT);
        commitRound.setCommitDeadline(OffsetDateTime.now().minusMinutes(1));

        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of(commitRound));
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processRoundTransitions();

        ArgumentCaptor<Round> captor = ArgumentCaptor.forClass(Round.class);
        verify(roundRepository).save(captor.capture());
        assertEquals(RoundStatus.REVEAL, captor.getValue().getStatus());
        verify(autoRevealService).autoRevealCommitments(any(Round.class));
    }

    @Test
    void processRoundTransitions_transitionsRevealToSettling() {
        Round revealRound = new Round();
        revealRound.setId(3);
        revealRound.setStatus(RoundStatus.REVEAL);
        revealRound.setRevealDeadline(OffsetDateTime.now().minusMinutes(1));

        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of(revealRound));
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processRoundTransitions();

        ArgumentCaptor<Round> captor = ArgumentCaptor.forClass(Round.class);
        verify(roundRepository).save(captor.capture());
        assertEquals(RoundStatus.SETTLING, captor.getValue().getStatus());
    }

    @Test
    void processRoundTransitions_doesNotTransitionFutureRound() {
        Round futureRound = new Round();
        futureRound.setId(4);
        futureRound.setStatus(RoundStatus.OPEN);
        futureRound.setStartedAt(OffsetDateTime.now().plusMinutes(10));
        futureRound.setCommitDeadline(OffsetDateTime.now().plusMinutes(15));

        when(roundRepository.findByStatus(RoundStatus.OPEN)).thenReturn(List.of(futureRound));
        when(roundRepository.findByStatus(RoundStatus.COMMIT)).thenReturn(List.of());
        when(roundRepository.findByStatus(RoundStatus.REVEAL)).thenReturn(List.of());

        service.processRoundTransitions();

        verify(roundRepository, never()).save(any(Round.class));
    }
}
