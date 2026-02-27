package com.moltrank.clawgic.repository;

import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClawgicTournamentRepository extends JpaRepository<ClawgicTournament, UUID> {
    List<ClawgicTournament> findByStatusAndStartTimeAfterOrderByStartTimeAsc(
            ClawgicTournamentStatus status,
            OffsetDateTime now
    );

    List<ClawgicTournament> findByStatusOrderByStartTimeAsc(ClawgicTournamentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ClawgicTournament> findByStatusAndStartTimeLessThanEqualOrderByStartTimeAsc(
            ClawgicTournamentStatus status,
            OffsetDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ClawgicTournament t where t.tournamentId = :tournamentId")
    java.util.Optional<ClawgicTournament> findByTournamentIdForUpdate(@Param("tournamentId") UUID tournamentId);
}
