package com.clawgic.clawgic.web;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TournamentEntryConflictException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TournamentEntryConflictException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TournamentEntryConflictException tournamentNotOpen(String detail) {
        return new TournamentEntryConflictException(
                HttpStatus.CONFLICT,
                "tournament_not_open",
                detail
        );
    }

    public static TournamentEntryConflictException entryWindowClosed(String detail) {
        return new TournamentEntryConflictException(
                HttpStatus.CONFLICT,
                "entry_window_closed",
                detail
        );
    }

    public static TournamentEntryConflictException alreadyEntered(String detail) {
        return new TournamentEntryConflictException(
                HttpStatus.CONFLICT,
                "already_entered",
                detail
        );
    }

    public static TournamentEntryConflictException capacityReached(String detail) {
        return new TournamentEntryConflictException(
                HttpStatus.CONFLICT,
                "capacity_reached",
                detail
        );
    }

    public static TournamentEntryConflictException invalidAgent(String detail) {
        return new TournamentEntryConflictException(
                HttpStatus.NOT_FOUND,
                "invalid_agent",
                detail
        );
    }
}
