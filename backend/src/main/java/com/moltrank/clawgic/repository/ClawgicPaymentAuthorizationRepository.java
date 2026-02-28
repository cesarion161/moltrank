package com.moltrank.clawgic.repository;

import com.moltrank.clawgic.model.ClawgicPaymentAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClawgicPaymentAuthorizationRepository extends JpaRepository<ClawgicPaymentAuthorization, UUID> {
    boolean existsByWalletAddressAndRequestNonce(String walletAddress, String requestNonce);

    boolean existsByWalletAddressAndIdempotencyKey(String walletAddress, String idempotencyKey);

    Optional<ClawgicPaymentAuthorization> findByWalletAddressAndRequestNonce(
            String walletAddress,
            String requestNonce
    );

    Optional<ClawgicPaymentAuthorization> findByWalletAddressAndIdempotencyKey(
            String walletAddress,
            String idempotencyKey
    );

    List<ClawgicPaymentAuthorization> findByTournamentIdOrderByCreatedAtAsc(UUID tournamentId);
}
