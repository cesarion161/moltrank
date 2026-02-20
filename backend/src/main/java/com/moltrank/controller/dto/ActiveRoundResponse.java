package com.moltrank.controller.dto;

import com.moltrank.model.Round;
import com.moltrank.model.RoundStatus;

import java.time.OffsetDateTime;

public record ActiveRoundResponse(
        Integer id,
        Integer roundId,
        RoundStatus status,
        OffsetDateTime commitDeadline,
        OffsetDateTime revealDeadline,
        Integer totalPairs,
        Integer remainingPairs
) {
    public static ActiveRoundResponse from(Round round, Integer totalPairs, Integer remainingPairs) {
        return new ActiveRoundResponse(
                round.getId(),
                round.getId(),
                round.getStatus(),
                round.getCommitDeadline(),
                round.getRevealDeadline(),
                totalPairs,
                remainingPairs
        );
    }
}
