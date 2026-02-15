package com.moltrank.repository;

import com.moltrank.model.Round;
import com.moltrank.model.RoundStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoundRepository extends JpaRepository<Round, Integer> {
    List<Round> findByMarketId(Integer marketId);
    List<Round> findByMarketId(Integer marketId, Sort sort);
    List<Round> findByStatus(RoundStatus status);
    List<Round> findByMarketIdAndStatus(Integer marketId, RoundStatus status);
    List<Round> findByMarketIdAndStatusIn(Integer marketId, List<RoundStatus> statuses);
}
