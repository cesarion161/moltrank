package com.moltrank.repository;

import com.moltrank.model.Pair;
import com.moltrank.model.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PairRepository extends JpaRepository<Pair, Integer> {
    List<Pair> findByRoundId(Integer roundId);
    List<Pair> findByIsGolden(Boolean isGolden);
    List<Pair> findByIsAudit(Boolean isAudit);

    @Query("SELECT p FROM Pair p " +
            "WHERE p.round.market.id = :marketId " +
            "AND p.round.status IN :statuses " +
            "AND NOT EXISTS (" +
            "  SELECT c FROM Commitment c " +
            "  WHERE c.pair.id = p.id " +
            "  AND c.curatorWallet = :wallet" +
            ") " +
            "AND NOT EXISTS (" +
            "  SELECT s FROM PairSkip s " +
            "  WHERE s.pair.id = p.id " +
            "  AND s.curatorWallet = :wallet" +
            ") " +
            "ORDER BY p.id ASC")
    Optional<Pair> findNextPairForCurator(@Param("wallet") String wallet,
                                          @Param("marketId") Integer marketId,
                                          @Param("statuses") List<RoundStatus> statuses);
}
