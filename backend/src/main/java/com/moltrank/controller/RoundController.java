package com.moltrank.controller;

import com.moltrank.controller.dto.ActiveRoundResponse;
import com.moltrank.controller.dto.RoundResponse;
import com.moltrank.model.Round;
import com.moltrank.model.RoundStatus;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.RoundRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for round management.
 */
@RestController
@RequestMapping("/api/rounds")
public class RoundController {

    private static final List<RoundStatus> ACTIVE_STATUSES = List.of(
            RoundStatus.OPEN,
            RoundStatus.COMMIT,
            RoundStatus.REVEAL,
            RoundStatus.SETTLING
    );

    private final RoundRepository roundRepository;
    private final CommitmentRepository commitmentRepository;

    public RoundController(RoundRepository roundRepository,
                           CommitmentRepository commitmentRepository) {
        this.roundRepository = roundRepository;
        this.commitmentRepository = commitmentRepository;
    }

    /**
     * List all rounds with status.
     *
     * @param marketId Optional market ID filter (defaults to 1)
     * @return List of rounds sorted by created date (descending)
     */
    @GetMapping
    public ResponseEntity<List<RoundResponse>> listRounds(
            @RequestParam(defaultValue = "1") Integer marketId) {

        List<Round> rounds = roundRepository.findByMarketId(
                marketId,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<RoundResponse> response = rounds.stream()
                .map(RoundResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get round detail with settlement hash.
     *
     * @param id The round ID
     * @return Round details including status, pairs, deadlines, and settlement hash
     */
    @GetMapping("/{id}")
    public ResponseEntity<RoundResponse> getRoundDetail(@PathVariable Integer id) {
        Round round = roundRepository.findById(id)
                .orElse(null);

        if (round == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(RoundResponse.from(round));
    }

    /**
     * Get the latest active round for a market.
     *
     * @param marketId Optional market ID filter (defaults to 1)
     * @return Active round payload used by curate flow
     */
    @GetMapping("/active")
    public ResponseEntity<ActiveRoundResponse> getActiveRound(
            @RequestParam(defaultValue = "1") Integer marketId) {

        Round round = roundRepository.findTopByMarketIdAndStatusInOrderByIdDesc(
                marketId,
                ACTIVE_STATUSES
        ).orElse(null);

        if (round == null) {
            return ResponseEntity.notFound().build();
        }

        int totalPairs = round.getPairs() != null ? round.getPairs() : 0;
        long committedPairs = commitmentRepository.countDistinctCommittedPairsByRoundId(round.getId());
        int remainingPairs = (int) Math.max(0, totalPairs - committedPairs);

        return ResponseEntity.ok(ActiveRoundResponse.from(round, totalPairs, remainingPairs));
    }
}
