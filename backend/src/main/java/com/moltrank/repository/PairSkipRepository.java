package com.moltrank.repository;

import com.moltrank.model.PairSkip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PairSkipRepository extends JpaRepository<PairSkip, Integer> {
    Optional<PairSkip> findByPairIdAndCuratorWallet(Integer pairId, String curatorWallet);
}
