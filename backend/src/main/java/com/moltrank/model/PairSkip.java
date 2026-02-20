package com.moltrank.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pair_skip")
public class PairSkip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair_id", nullable = false)
    private Pair pair;

    @Column(name = "curator_wallet", nullable = false, length = 44)
    private String curatorWallet;

    @Column(name = "skipped_at", nullable = false, updatable = false)
    private OffsetDateTime skippedAt = OffsetDateTime.now();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Pair getPair() {
        return pair;
    }

    public void setPair(Pair pair) {
        this.pair = pair;
    }

    public String getCuratorWallet() {
        return curatorWallet;
    }

    public void setCuratorWallet(String curatorWallet) {
        this.curatorWallet = curatorWallet;
    }

    public OffsetDateTime getSkippedAt() {
        return skippedAt;
    }

    public void setSkippedAt(OffsetDateTime skippedAt) {
        this.skippedAt = skippedAt;
    }
}
