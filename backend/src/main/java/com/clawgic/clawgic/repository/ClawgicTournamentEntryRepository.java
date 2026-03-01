package com.clawgic.clawgic.repository;

import com.clawgic.clawgic.model.ClawgicTournamentEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClawgicTournamentEntryRepository extends JpaRepository<ClawgicTournamentEntry, UUID> {
    boolean existsByTournamentIdAndAgentId(UUID tournamentId, UUID agentId);

    Optional<ClawgicTournamentEntry> findByTournamentIdAndAgentId(UUID tournamentId, UUID agentId);

    List<ClawgicTournamentEntry> findByTournamentIdOrderByCreatedAtAsc(UUID tournamentId);

    long countByTournamentId(UUID tournamentId);
}
