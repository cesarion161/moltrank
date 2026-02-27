package com.moltrank.clawgic.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ClawgicTournamentRequests {

    private ClawgicTournamentRequests() {
    }

    public record CreateTournamentRequest(
            @NotBlank(message = "topic is required")
            @Size(max = 2000, message = "topic must be at most 2000 characters")
            String topic,

            @NotNull(message = "startTime is required")
            @Future(message = "startTime must be in the future")
            OffsetDateTime startTime,

            OffsetDateTime entryCloseTime,

            @NotNull(message = "baseEntryFeeUsdc is required")
            @DecimalMin(value = "0.0", inclusive = true, message = "baseEntryFeeUsdc must be non-negative")
            @Digits(integer = 12, fraction = 6, message = "baseEntryFeeUsdc supports up to 6 decimal places")
            BigDecimal baseEntryFeeUsdc
    ) {
        @AssertTrue(message = "entryCloseTime must be on or before startTime")
        public boolean isEntryCloseTimeBeforeOrEqualStartTime() {
            if (entryCloseTime == null || startTime == null) {
                return true;
            }
            return !entryCloseTime.isAfter(startTime);
        }
    }

    public record EnterTournamentRequest(
            @NotNull(message = "agentId is required")
            UUID agentId
    ) {
    }
}
