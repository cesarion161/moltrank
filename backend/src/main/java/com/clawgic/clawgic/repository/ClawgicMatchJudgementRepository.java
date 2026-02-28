package com.clawgic.clawgic.repository;

import com.clawgic.clawgic.model.ClawgicMatchJudgement;
import com.clawgic.clawgic.model.ClawgicMatchJudgementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClawgicMatchJudgementRepository extends JpaRepository<ClawgicMatchJudgement, UUID> {
    List<ClawgicMatchJudgement> findByTournamentIdOrderByMatchIdAscAttemptAscCreatedAtAsc(UUID tournamentId);

    List<ClawgicMatchJudgement> findByMatchIdOrderByCreatedAtAsc(UUID matchId);

    List<ClawgicMatchJudgement> findByMatchIdAndStatusOrderByCreatedAtAsc(
            UUID matchId,
            ClawgicMatchJudgementStatus status
    );

    Optional<ClawgicMatchJudgement> findByMatchIdAndJudgeKeyAndAttempt(
            UUID matchId,
            String judgeKey,
            Integer attempt
    );

    boolean existsByMatchIdAndJudgeKeyAndAttempt(UUID matchId, String judgeKey, Integer attempt);

    @Query("""
            select coalesce(max(j.attempt), 0)
            from ClawgicMatchJudgement j
            where j.matchId = :matchId
              and j.judgeKey = :judgeKey
            """)
    int findMaxAttemptByMatchIdAndJudgeKey(
            @Param("matchId") UUID matchId,
            @Param("judgeKey") String judgeKey
    );
}
