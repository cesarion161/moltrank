package com.moltrank.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pair")
public class Pair {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_a", nullable = false)
    private Post postA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_b", nullable = false)
    private Post postB;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pair_winner")
    private PairWinner winner;

    @Column(name = "total_stake", nullable = false)
    private Long totalStake = 0L;

    @Column(nullable = false)
    private Long reward = 0L;

    @Column(name = "is_golden", nullable = false)
    private Boolean isGolden = false;

    @Column(name = "is_audit", nullable = false)
    private Boolean isAudit = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "golden_answer", columnDefinition = "pair_winner")
    private PairWinner goldenAnswer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Round getRound() {
        return round;
    }

    public void setRound(Round round) {
        this.round = round;
    }

    public Post getPostA() {
        return postA;
    }

    public void setPostA(Post postA) {
        this.postA = postA;
    }

    public Post getPostB() {
        return postB;
    }

    public void setPostB(Post postB) {
        this.postB = postB;
    }

    public PairWinner getWinner() {
        return winner;
    }

    public void setWinner(PairWinner winner) {
        this.winner = winner;
    }

    public Long getTotalStake() {
        return totalStake;
    }

    public void setTotalStake(Long totalStake) {
        this.totalStake = totalStake;
    }

    public Long getReward() {
        return reward;
    }

    public void setReward(Long reward) {
        this.reward = reward;
    }

    public Boolean getIsGolden() {
        return isGolden;
    }

    public void setIsGolden(Boolean isGolden) {
        this.isGolden = isGolden;
    }

    public Boolean getIsAudit() {
        return isAudit;
    }

    public void setIsAudit(Boolean isAudit) {
        this.isAudit = isAudit;
    }

    public PairWinner getGoldenAnswer() {
        return goldenAnswer;
    }

    public void setGoldenAnswer(PairWinner goldenAnswer) {
        this.goldenAnswer = goldenAnswer;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(OffsetDateTime settledAt) {
        this.settledAt = settledAt;
    }
}
