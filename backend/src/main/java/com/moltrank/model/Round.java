package com.moltrank.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "round")
public class Round {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "round_status")
    private RoundStatus status = RoundStatus.OPEN;

    @Column(nullable = false)
    private Integer pairs = 0;

    @Column(name = "base_per_pair", nullable = false)
    private Long basePerPair = 0L;

    @Column(name = "premium_per_pair", nullable = false)
    private Long premiumPerPair = 0L;

    @Column(name = "content_merkle_root", length = 64)
    private String contentMerkleRoot;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "commit_deadline")
    private OffsetDateTime commitDeadline;

    @Column(name = "reveal_deadline")
    private OffsetDateTime revealDeadline;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Market getMarket() {
        return market;
    }

    public void setMarket(Market market) {
        this.market = market;
    }

    public RoundStatus getStatus() {
        return status;
    }

    public void setStatus(RoundStatus status) {
        this.status = status;
    }

    public Integer getPairs() {
        return pairs;
    }

    public void setPairs(Integer pairs) {
        this.pairs = pairs;
    }

    public Long getBasePerPair() {
        return basePerPair;
    }

    public void setBasePerPair(Long basePerPair) {
        this.basePerPair = basePerPair;
    }

    public Long getPremiumPerPair() {
        return premiumPerPair;
    }

    public void setPremiumPerPair(Long premiumPerPair) {
        this.premiumPerPair = premiumPerPair;
    }

    public String getContentMerkleRoot() {
        return contentMerkleRoot;
    }

    public void setContentMerkleRoot(String contentMerkleRoot) {
        this.contentMerkleRoot = contentMerkleRoot;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCommitDeadline() {
        return commitDeadline;
    }

    public void setCommitDeadline(OffsetDateTime commitDeadline) {
        this.commitDeadline = commitDeadline;
    }

    public OffsetDateTime getRevealDeadline() {
        return revealDeadline;
    }

    public void setRevealDeadline(OffsetDateTime revealDeadline) {
        this.revealDeadline = revealDeadline;
    }

    public OffsetDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(OffsetDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
