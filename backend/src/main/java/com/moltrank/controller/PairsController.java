package com.moltrank.controller;

import com.moltrank.controller.dto.CommitPairRequest;
import com.moltrank.controller.dto.PairResponse;
import com.moltrank.controller.dto.SkipPairRequest;
import com.moltrank.model.Commitment;
import com.moltrank.model.Pair;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.IdentityRepository;
import com.moltrank.repository.PairRepository;
import com.moltrank.service.CommitmentCodec;
import com.moltrank.service.PairSelectionService;
import com.moltrank.service.PairSkipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * REST API for pairwise curation.
 */
@RestController
@RequestMapping("/api/pairs")
public class PairsController {

    private final PairRepository pairRepository;
    private final CommitmentRepository commitmentRepository;
    private final IdentityRepository identityRepository;
    private final PairSkipService pairSkipService;
    private final PairSelectionService pairSelectionService;

    public PairsController(PairRepository pairRepository,
                           CommitmentRepository commitmentRepository,
                           IdentityRepository identityRepository,
                           PairSkipService pairSkipService,
                           PairSelectionService pairSelectionService) {
        this.pairRepository = pairRepository;
        this.commitmentRepository = commitmentRepository;
        this.identityRepository = identityRepository;
        this.pairSkipService = pairSkipService;
        this.pairSelectionService = pairSelectionService;
    }

    /**
     * Get next pair for curation.
     *
     * @param wallet The curator's wallet address
     * @param marketId Optional market ID (defaults to 1)
     * @return Next pair to curate, or 404 if no pairs available
     */
    @GetMapping("/next")
    public ResponseEntity<PairResponse> getNextPair(
            @RequestParam String wallet,
            @RequestParam(defaultValue = "1") Integer marketId) {

        // Find first uncommitted pair for this curator
        Pair nextPair = pairSelectionService.findNextPairForCurator(wallet, marketId)
                .orElse(null);

        if (nextPair == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(PairResponse.from(nextPair));
    }

    /**
     * Submit commitment for a pair.
     *
     * @param id The pair ID
     * @param request The commitment pair request data
     * @return Created commitment
     */
    @PostMapping("/{id}/commit")
    public ResponseEntity<Void> commitPair(
            @PathVariable Integer id,
            @RequestBody CommitPairRequest request) {

        // Verify pair exists
        Pair pair = pairRepository.findById(id)
                .orElse(null);

        if (pair == null) {
            return ResponseEntity.notFound().build();
        }

        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        if (identityRepository.findByWallet(request.wallet()).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String normalizedHash;
        try {
            normalizedHash = CommitmentCodec.normalizeCommitmentHash(request.commitmentHash());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        Commitment commitment = new Commitment();
        commitment.setCuratorWallet(request.wallet());
        commitment.setHash(normalizedHash);
        commitment.setStake(request.stakeAmount());
        commitment.setEncryptedReveal(request.encryptedReveal());

        // Set pair reference and timestamp
        commitment.setPair(pair);
        OffsetDateTime committedAt = OffsetDateTime.now();
        commitment.setCommittedAt(committedAt);
        commitment.setRevealed(false);

        // Save commitment
        commitmentRepository.save(commitment);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Skip a pair for this curator so it is not returned again.
     *
     * @param id The pair ID
     * @param request The skip request payload
     * @return 204 when skip is recorded (idempotent)
     */
    @PostMapping("/{id}/skip")
    public ResponseEntity<Void> skipPair(
            @PathVariable Integer id,
            @RequestBody SkipPairRequest request) {

        Pair pair = pairRepository.findById(id)
                .orElse(null);

        if (pair == null) {
            return ResponseEntity.notFound().build();
        }

        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        if (identityRepository.findByWallet(request.wallet()).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        pairSkipService.skipPair(pair, request.wallet());
        return ResponseEntity.noContent().build();
    }
}
