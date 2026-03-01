package com.clawgic.clawgic.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TournamentEntryConflictExceptionHandler {

    @ExceptionHandler(TournamentEntryConflictException.class)
    public ResponseEntity<TournamentEntryConflictErrorResponse> handle(TournamentEntryConflictException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(new TournamentEntryConflictErrorResponse(ex.getCode(), ex.getMessage()));
    }

    public record TournamentEntryConflictErrorResponse(
            String code,
            String message
    ) {
    }
}
