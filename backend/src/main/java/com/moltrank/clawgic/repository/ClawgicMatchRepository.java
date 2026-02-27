package com.moltrank.clawgic.repository;

import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClawgicMatchRepository extends JpaRepository<ClawgicMatch, UUID> {
    boolean existsByTournamentId(UUID tournamentId);

    List<ClawgicMatch> findByTournamentIdOrderByCreatedAtAsc(UUID tournamentId);

    List<ClawgicMatch> findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(UUID tournamentId);

    List<ClawgicMatch> findByStatusOrderByUpdatedAtAsc(ClawgicMatchStatus status);

    List<ClawgicMatch> findByStatusInAndWinnerAgentIdIsNotNullAndNextMatchIdIsNotNullOrderByUpdatedAtAsc(
            List<ClawgicMatchStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ClawgicMatch m where m.matchId = :matchId")
    Optional<ClawgicMatch> findByMatchIdForUpdate(@Param("matchId") UUID matchId);

    @Query(
            value = """
                    SELECT clawgic_match.*
                    FROM clawgic_matches clawgic_match
                    JOIN clawgic_tournaments tournament
                      ON tournament.tournament_id = clawgic_match.tournament_id
                    WHERE clawgic_match.status = 'SCHEDULED'
                      AND clawgic_match.agent1_id IS NOT NULL
                      AND clawgic_match.agent2_id IS NOT NULL
                      AND tournament.status = 'IN_PROGRESS'
                    ORDER BY tournament.start_time ASC,
                             clawgic_match.bracket_round ASC NULLS LAST,
                             clawgic_match.bracket_position ASC NULLS LAST,
                             clawgic_match.created_at ASC
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<ClawgicMatch> findNextReadyMatchForExecution();
}
