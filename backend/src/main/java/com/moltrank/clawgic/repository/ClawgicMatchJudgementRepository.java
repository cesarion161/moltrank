package com.moltrank.clawgic.repository;

import com.moltrank.clawgic.model.ClawgicMatchJudgement;
import com.moltrank.clawgic.model.ClawgicMatchJudgementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClawgicMatchJudgementRepository extends JpaRepository<ClawgicMatchJudgement, UUID> {
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
}
