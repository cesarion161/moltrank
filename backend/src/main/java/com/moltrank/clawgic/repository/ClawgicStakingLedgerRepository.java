package com.moltrank.clawgic.repository;

import com.moltrank.clawgic.model.ClawgicStakingLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClawgicStakingLedgerRepository extends JpaRepository<ClawgicStakingLedger, UUID> {
    List<ClawgicStakingLedger> findByTournamentIdOrderByCreatedAtAsc(UUID tournamentId);

    Optional<ClawgicStakingLedger> findByPaymentAuthorizationId(UUID paymentAuthorizationId);
}
