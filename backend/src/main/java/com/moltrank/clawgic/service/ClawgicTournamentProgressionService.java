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

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClawgicTournamentProgressionService {

    private static final Logger log = LoggerFactory.getLogger(ClawgicTournamentProgressionService.class);
    private static final List<ClawgicMatchStatus> TERMINAL_MATCH_STATUSES =
            List.of(ClawgicMatchStatus.COMPLETED, ClawgicMatchStatus.FORFEITED);

    private final ClawgicTournamentRepository clawgicTournamentRepository;
    private final ClawgicMatchRepository clawgicMatchRepository;

    public boolean completeTournamentIfResolved(UUID tournamentId, OffsetDateTime now) {
        ClawgicTournament tournament = clawgicTournamentRepository.findByTournamentIdForUpdate(tournamentId).orElse(null);
        if (tournament == null || tournament.getStatus() != ClawgicTournamentStatus.IN_PROGRESS) {
            return false;
        }

        List<ClawgicMatch> matches =
                clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(tournamentId);
        if (matches.isEmpty() || matches.size() < resolveExpectedMatchCount(tournament)) {
            return false;
        }
        if (matches.stream().anyMatch(match -> !isTerminal(match.getStatus()))) {
            return false;
        }

        ClawgicMatch finalMatch = resolveFinalMatch(matches);
        if (finalMatch == null || !isTerminal(finalMatch.getStatus())) {
            return false;
        }
        if (finalMatch.getWinnerAgentId() == null) {
            log.warn(
                    "Cannot complete tournament {} because final match {} has no winner",
                    tournamentId,
                    finalMatch.getMatchId()
            );
            return false;
        }

        tournament.setWinnerAgentId(finalMatch.getWinnerAgentId());
        tournament.setMatchesCompleted(countMatchesWithStatus(matches, ClawgicMatchStatus.COMPLETED));
        tournament.setMatchesForfeited(countMatchesWithStatus(matches, ClawgicMatchStatus.FORFEITED));
        tournament.setStatus(ClawgicTournamentStatus.COMPLETED);
        tournament.setCompletedAt(now);
        tournament.setUpdatedAt(now);
        clawgicTournamentRepository.save(tournament);
        return true;
    }

    private static int resolveExpectedMatchCount(ClawgicTournament tournament) {
        Integer bracketSize = tournament.getBracketSize();
        if (bracketSize == null || bracketSize <= 1) {
            return 0;
        }
        return bracketSize - 1;
    }

    private static ClawgicMatch resolveFinalMatch(List<ClawgicMatch> matches) {
        return matches.stream()
                .filter(match -> match.getNextMatchId() == null)
                .max(Comparator
                        .comparingInt((ClawgicMatch match) -> nullSafeOrder(match.getBracketRound()))
                        .thenComparingInt(match -> nullSafeOrder(match.getBracketPosition())))
                .orElse(null);
    }

    private static int countMatchesWithStatus(List<ClawgicMatch> matches, ClawgicMatchStatus status) {
        return (int) matches.stream()
                .filter(match -> match.getStatus() == status)
                .count();
    }

    private static boolean isTerminal(ClawgicMatchStatus status) {
        return TERMINAL_MATCH_STATUSES.contains(status);
    }

    private static int nullSafeOrder(Integer value) {
        return value == null ? 0 : value;
    }
}
