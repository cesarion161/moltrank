package com.moltrank.clawgic.service;

import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

@Service
@RequiredArgsConstructor
public class ClawgicMatchLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ClawgicMatchLifecycleService.class);
    private static final int MAX_MATCH_EXECUTIONS_PER_TICK = 4;
    private static final List<ClawgicMatchStatus> WINNER_PROPAGATION_STATUSES =
            List.of(ClawgicMatchStatus.COMPLETED, ClawgicMatchStatus.FORFEITED);

    private final ClawgicTournamentRepository clawgicTournamentRepository;
    private final ClawgicMatchRepository clawgicMatchRepository;
    private final ClawgicDebateExecutionService clawgicDebateExecutionService;
    private final ClawgicTournamentProgressionService clawgicTournamentProgressionService;
    private final TransactionTemplate transactionTemplate;

    public TickSummary processLifecycleTick() {
        int tournamentsActivated = runIntInTransaction(this::activateDueTournaments);
        int winnersPropagated = runIntInTransaction(this::propagateResolvedMatchWinners);
        int tournamentsCompleted = runIntInTransaction(this::completeResolvedTournaments);

        int matchesExecuted = 0;
        for (int i = 0; i < MAX_MATCH_EXECUTIONS_PER_TICK; i++) {
            boolean executed = runBooleanInTransaction(this::executeNextReadyMatch);
            if (!executed) {
                break;
            }
            matchesExecuted++;
            winnersPropagated += runIntInTransaction(this::propagateResolvedMatchWinners);
            tournamentsCompleted += runIntInTransaction(this::completeResolvedTournaments);
        }

        return new TickSummary(tournamentsActivated, winnersPropagated, tournamentsCompleted, matchesExecuted);
    }

    private int activateDueTournaments() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ClawgicTournament> dueTournaments =
                clawgicTournamentRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeAsc(
                        ClawgicTournamentStatus.LOCKED,
                        now
                );
        if (dueTournaments.isEmpty()) {
            return 0;
        }

        int activated = 0;
        for (ClawgicTournament tournament : dueTournaments) {
            if (tournament.getStatus() != ClawgicTournamentStatus.LOCKED) {
                continue;
            }
            tournament.setStatus(ClawgicTournamentStatus.IN_PROGRESS);
            if (tournament.getStartedAt() == null) {
                tournament.setStartedAt(now);
            }
            tournament.setUpdatedAt(now);
            activated++;
        }
        clawgicTournamentRepository.saveAll(dueTournaments);
        return activated;
    }

    private int propagateResolvedMatchWinners() {
        List<ClawgicMatch> resolvedMatches =
                clawgicMatchRepository.findByStatusInAndWinnerAgentIdIsNotNullAndNextMatchIdIsNotNullOrderByUpdatedAtAsc(
                        WINNER_PROPAGATION_STATUSES
                );
        if (resolvedMatches.isEmpty()) {
            return 0;
        }

        int propagated = 0;
        for (ClawgicMatch resolvedMatch : resolvedMatches) {
            UUID nextMatchId = resolvedMatch.getNextMatchId();
            if (nextMatchId == null) {
                continue;
            }

            ClawgicMatch nextMatch = clawgicMatchRepository.findByMatchIdForUpdate(nextMatchId).orElse(null);
            if (nextMatch == null) {
                continue;
            }
            if (!resolvedMatch.getTournamentId().equals(nextMatch.getTournamentId())) {
                log.warn(
                        "Skipping winner propagation from match {} to {} because tournament ids differ",
                        resolvedMatch.getMatchId(),
                        nextMatchId
                );
                continue;
            }
            if (nextMatch.getStatus() != ClawgicMatchStatus.SCHEDULED) {
                continue;
            }

            if (applyWinnerToNextMatch(resolvedMatch, nextMatch)) {
                nextMatch.setUpdatedAt(OffsetDateTime.now());
                clawgicMatchRepository.save(nextMatch);
                propagated++;
            }
        }

        return propagated;
    }

    private int completeResolvedTournaments() {
        List<ClawgicTournament> inProgressTournaments =
                clawgicTournamentRepository.findByStatusOrderByStartTimeAsc(ClawgicTournamentStatus.IN_PROGRESS);
        if (inProgressTournaments.isEmpty()) {
            return 0;
        }

        int completed = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (ClawgicTournament tournament : inProgressTournaments) {
            if (clawgicTournamentProgressionService.completeTournamentIfResolved(tournament.getTournamentId(), now)) {
                completed++;
            }
        }
        return completed;
    }

    private boolean applyWinnerToNextMatch(ClawgicMatch resolvedMatch, ClawgicMatch nextMatch) {
        UUID winnerAgentId = resolvedMatch.getWinnerAgentId();
        Integer nextSlot = resolvedMatch.getNextMatchAgentSlot();
        if (winnerAgentId == null || nextSlot == null) {
            return false;
        }

        if (nextSlot == 1) {
            return assignWinnerToSlot(
                    resolvedMatch,
                    nextMatch,
                    winnerAgentId,
                    nextMatch.getAgent1Id(),
                    true
            );
        }
        if (nextSlot == 2) {
            return assignWinnerToSlot(
                    resolvedMatch,
                    nextMatch,
                    winnerAgentId,
                    nextMatch.getAgent2Id(),
                    false
            );
        }

        log.warn(
                "Skipping winner propagation for match {} because next slot is invalid: {}",
                resolvedMatch.getMatchId(),
                nextSlot
        );
        return false;
    }

    private boolean assignWinnerToSlot(
            ClawgicMatch resolvedMatch,
            ClawgicMatch nextMatch,
            UUID winnerAgentId,
            UUID currentAgentId,
            boolean firstAgentSlot
    ) {
        if (currentAgentId == null) {
            if (firstAgentSlot) {
                nextMatch.setAgent1Id(winnerAgentId);
            } else {
                nextMatch.setAgent2Id(winnerAgentId);
            }
            return true;
        }
        if (currentAgentId.equals(winnerAgentId)) {
            return false;
        }

        log.warn(
                "Skipping winner propagation from match {} to next match {} because slot already has a different agent",
                resolvedMatch.getMatchId(),
                nextMatch.getMatchId()
        );
        return false;
    }

    private boolean executeNextReadyMatch() {
        List<ClawgicMatch> readyMatches = clawgicMatchRepository.findNextReadyMatchForExecution();
        if (readyMatches.isEmpty()) {
            return false;
        }

        ClawgicMatch readyMatch = readyMatches.getFirst();
        if (readyMatch.getAgent1Id() == null || readyMatch.getAgent2Id() == null) {
            return false;
        }

        ClawgicMatch executedMatch = clawgicDebateExecutionService.executeMatch(readyMatch.getMatchId());
        clawgicTournamentProgressionService.completeTournamentIfResolved(
                executedMatch.getTournamentId(),
                OffsetDateTime.now()
        );
        return true;
    }

    private int runIntInTransaction(IntSupplier work) {
        Integer value = transactionTemplate.execute(status -> work.getAsInt());
        return value == null ? 0 : value;
    }

    private boolean runBooleanInTransaction(BooleanSupplier work) {
        Boolean value = transactionTemplate.execute(status -> work.getAsBoolean());
        return Boolean.TRUE.equals(value);
    }

    public record TickSummary(
            int tournamentsActivated,
            int winnersPropagated,
            int tournamentsCompleted,
            int matchesExecuted
    ) {
        public boolean hasWork() {
            return tournamentsActivated > 0 || winnersPropagated > 0 || tournamentsCompleted > 0 || matchesExecuted > 0;
        }
    }
}
