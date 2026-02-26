package com.moltrank.clawgic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "clawgic_staking_ledger")
public class ClawgicStakingLedger {

    @Id
    @Column(name = "stake_id", nullable = false, updatable = false)
    private UUID stakeId;

    @Column(name = "tournament_id", nullable = false, updatable = false)
    private UUID tournamentId;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "payment_authorization_id")
    private UUID paymentAuthorizationId;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "wallet_address", nullable = false, length = 128)
    private String walletAddress;

    @Column(name = "amount_staked", nullable = false, precision = 18, scale = 6)
    private BigDecimal amountStaked = BigDecimal.ZERO;

    @Column(name = "judge_fee_deducted", nullable = false, precision = 18, scale = 6)
    private BigDecimal judgeFeeDeducted = BigDecimal.ZERO;

    @Column(name = "system_retention", nullable = false, precision = 18, scale = 6)
    private BigDecimal systemRetention = BigDecimal.ZERO;

    @Column(name = "reward_payout", nullable = false, precision = 18, scale = 6)
    private BigDecimal rewardPayout = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClawgicStakingLedgerStatus status = ClawgicStakingLedgerStatus.AUTHORIZED;

    @Column(name = "settlement_note", columnDefinition = "TEXT")
    private String settlementNote;

    @Column(name = "authorized_at")
    private OffsetDateTime authorizedAt;

    @Column(name = "entered_at")
    private OffsetDateTime enteredAt;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "forfeited_at")
    private OffsetDateTime forfeitedAt;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
