package com.moltrank.repository;

import com.moltrank.model.Curator;
import com.moltrank.model.CuratorId;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CuratorRepository extends JpaRepository<Curator, CuratorId> {
    List<Curator> findByWallet(String wallet);
    List<Curator> findByMarketId(Integer marketId);
    List<Curator> findByMarketIdOrderByCuratorScoreDesc(Integer marketId);
    List<Curator> findByMarketId(Integer marketId, Pageable pageable);
    long countByMarketId(Integer marketId);
    Optional<Curator> findByWalletAndMarketId(String wallet, Integer marketId);

    @Modifying
    @Query("UPDATE Curator c " +
            "SET c.pairsThisEpoch = c.pairsThisEpoch + 1, c.updatedAt = :updatedAt " +
            "WHERE c.wallet = :wallet " +
            "AND c.marketId = :marketId " +
            "AND c.pairsThisEpoch < :cap")
    int incrementPairsThisEpochIfBelowCap(@Param("wallet") String wallet,
                                          @Param("marketId") Integer marketId,
                                          @Param("cap") int cap,
                                          @Param("updatedAt") OffsetDateTime updatedAt);
}
