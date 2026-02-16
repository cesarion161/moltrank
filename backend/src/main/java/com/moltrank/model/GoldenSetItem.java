package com.moltrank.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "golden_set_item")
public class GoldenSetItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_a", nullable = false)
    private Post postA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_b", nullable = false)
    private Post postB;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "correct_answer", nullable = false, columnDefinition = "pair_winner")
    private PairWinner correctAnswer;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public PairWinner getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(PairWinner correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
