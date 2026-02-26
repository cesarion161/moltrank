package com.moltrank.clawgic.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "clawgic_payment_authorizations")
public class ClawgicPaymentAuthorization {

    @Id
    @Column(name = "payment_authorization_id", nullable = false, updatable = false)
    private UUID paymentAuthorizationId;

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "wallet_address", nullable = false, length = 128)
    private String walletAddress;

    @Column(name = "request_nonce", nullable = false, length = 128)
    private String requestNonce;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "authorization_nonce", length = 128)
    private String authorizationNonce;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClawgicPaymentAuthorizationStatus status = ClawgicPaymentAuthorizationStatus.CHALLENGED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_header_json", columnDefinition = "jsonb")
    private JsonNode paymentHeaderJson;

    @Column(name = "amount_authorized_usdc", nullable = false, precision = 18, scale = 6)
    private BigDecimal amountAuthorizedUsdc = BigDecimal.ZERO;

    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "recipient_wallet_address", length = 128)
    private String recipientWalletAddress;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "challenge_expires_at")
    private OffsetDateTime challengeExpiresAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
