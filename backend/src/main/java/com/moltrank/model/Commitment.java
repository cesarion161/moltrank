package com.moltrank.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Setter
@Getter
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

    @Column(name = "auto_reveal_failed", nullable = false)
    private Boolean autoRevealFailed = false;

    @Column(name = "auto_reveal_failure_reason", length = 64)
    private String autoRevealFailureReason;

    @Column(name = "auto_reveal_failed_at")
    private OffsetDateTime autoRevealFailedAt;

}
