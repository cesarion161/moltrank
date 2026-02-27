package com.moltrank.clawgic.controller;

import com.moltrank.clawgic.dto.ClawgicTournamentRequests;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.dto.ClawgicMatchResponses;
import com.moltrank.clawgic.service.ClawgicTournamentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clawgic/tournaments")
public class ClawgicTournamentController {

    private final ClawgicTournamentService clawgicTournamentService;

    public ClawgicTournamentController(ClawgicTournamentService clawgicTournamentService) {
        this.clawgicTournamentService = clawgicTournamentService;
    }

    @PostMapping
    public ResponseEntity<ClawgicTournamentResponses.TournamentDetail> createTournament(
            @Valid @RequestBody ClawgicTournamentRequests.CreateTournamentRequest request
    ) {
        ClawgicTournamentResponses.TournamentDetail tournament = clawgicTournamentService.createTournament(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tournament);
    }

    @GetMapping
    public ResponseEntity<List<ClawgicTournamentResponses.TournamentSummary>> listUpcomingTournaments() {
        return ResponseEntity.ok(clawgicTournamentService.listUpcomingTournaments());
    }

    @PostMapping("/{tournamentId}/enter")
    public ResponseEntity<ClawgicTournamentResponses.TournamentEntry> enterTournament(
            @PathVariable UUID tournamentId,
            @Valid @RequestBody ClawgicTournamentRequests.EnterTournamentRequest request
    ) {
        ClawgicTournamentResponses.TournamentEntry entry =
                clawgicTournamentService.enterTournament(tournamentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @PostMapping("/{tournamentId}/bracket")
    public ResponseEntity<List<ClawgicMatchResponses.MatchSummary>> createMvpBracket(
            @PathVariable UUID tournamentId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clawgicTournamentService.createMvpBracket(tournamentId));
    }
}
