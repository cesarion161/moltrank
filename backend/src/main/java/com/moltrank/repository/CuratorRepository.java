package com.moltrank.repository;

import com.moltrank.model.Curator;
import com.moltrank.model.CuratorId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuratorRepository extends JpaRepository<Curator, CuratorId> {
    List<Curator> findByWallet(String wallet);
    List<Curator> findByMarketId(Integer marketId);
    List<Curator> findByMarketIdOrderByCuratorScoreDesc(Integer marketId);
    List<Curator> findByMarketId(Integer marketId, Pageable pageable);
}
