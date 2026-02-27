package com.moltrank.clawgic.repository;

import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClawgicMatchRepository extends JpaRepository<ClawgicMatch, UUID> {
    boolean existsByTournamentId(UUID tournamentId);

    List<ClawgicMatch> findByTournamentIdOrderByCreatedAtAsc(UUID tournamentId);

    List<ClawgicMatch> findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(UUID tournamentId);

    List<ClawgicMatch> findByStatusOrderByUpdatedAtAsc(ClawgicMatchStatus status);
}
