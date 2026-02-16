package com.moltrank.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "commitment")
public class Commitment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pair_id", nullable = false)
    private Pair pair;

    @Column(name = "curator_wallet", nullable = false, length = 44)
    private String curatorWallet;

    @Column(nullable = false, length = 66)
    private String hash;

    @Column(nullable = false)
    private Long stake;

    @Column(name = "encrypted_reveal", nullable = false, columnDefinition = "TEXT")
    private String encryptedReveal;

    @Column(nullable = false)
    private Boolean revealed = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "pair_winner")
    private PairWinner choice;

    @Column(length = 64)
    private String nonce;

    @Column(name = "committed_at", nullable = false, updatable = false)
    private OffsetDateTime committedAt = OffsetDateTime.now();

    @Column(name = "revealed_at")
    private OffsetDateTime revealedAt;

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

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Long getStake() {
        return stake;
    }

    public void setStake(Long stake) {
        this.stake = stake;
    }

    public String getEncryptedReveal() {
        return encryptedReveal;
    }

    public void setEncryptedReveal(String encryptedReveal) {
        this.encryptedReveal = encryptedReveal;
    }

    public Boolean getRevealed() {
        return revealed;
    }

    public void setRevealed(Boolean revealed) {
        this.revealed = revealed;
    }

    public PairWinner getChoice() {
        return choice;
    }

    public void setChoice(PairWinner choice) {
        this.choice = choice;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public OffsetDateTime getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(OffsetDateTime committedAt) {
        this.committedAt = committedAt;
    }

    public OffsetDateTime getRevealedAt() {
        return revealedAt;
    }

    public void setRevealedAt(OffsetDateTime revealedAt) {
        this.revealedAt = revealedAt;
    }
}
