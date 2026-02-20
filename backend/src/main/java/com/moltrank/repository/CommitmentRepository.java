package com.moltrank.repository;

import com.moltrank.model.Commitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommitmentRepository extends JpaRepository<Commitment, Integer> {
    List<Commitment> findByPairId(Integer pairId);
    List<Commitment> findByCuratorWallet(String curatorWallet);
    List<Commitment> findByRevealed(Boolean revealed);
    List<Commitment> findByPairIdAndRevealed(Integer pairId, Boolean revealed);

    @Query("SELECT COUNT(DISTINCT c.pair.id) FROM Commitment c WHERE c.pair.round.id = :roundId")
    long countDistinctCommittedPairsByRoundId(@Param("roundId") Integer roundId);
}
