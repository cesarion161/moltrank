package com.moltrank.controller;

import com.moltrank.controller.dto.CommitPairRequest;
import com.moltrank.controller.dto.PairResponse;
import com.moltrank.controller.dto.SkipPairRequest;
import com.moltrank.model.Commitment;
import com.moltrank.model.Pair;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.IdentityRepository;
import com.moltrank.repository.PairRepository;
import com.moltrank.service.CommitSecurityService;
import com.moltrank.service.CuratorParticipationService;
import com.moltrank.service.PairSelectionService;
import com.moltrank.service.PairSkipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final CommitSecurityService commitSecurityService;
    private final CuratorParticipationService curatorParticipationService;

    public PairsController(PairRepository pairRepository,
                           CommitmentRepository commitmentRepository,
                           IdentityRepository identityRepository,
                           PairSkipService pairSkipService,
                           PairSelectionService pairSelectionService,
                           CommitSecurityService commitSecurityService,
                           CuratorParticipationService curatorParticipationService) {
        this.pairRepository = pairRepository;
        this.commitmentRepository = commitmentRepository;
        this.identityRepository = identityRepository;
        this.pairSkipService = pairSkipService;
        this.pairSelectionService = pairSelectionService;
        this.commitSecurityService = commitSecurityService;
        this.curatorParticipationService = curatorParticipationService;
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

        if (identityRepository.findByWallet(wallet).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (!curatorParticipationService.hasRemainingCapacity(wallet, marketId)) {
            return ResponseEntity.notFound().build();
        }

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
    @Transactional
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

        Integer marketId = pair.getRound().getMarket().getId();

        try {
            CommitSecurityService.SecuredCommitmentPayload securedPayload =
                    commitSecurityService.secureCommitPayload(id, request);

            if (!curatorParticipationService.tryConsumePairEvaluationSlot(request.wallet(), marketId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            Commitment commitment = new Commitment();
            commitment.setCuratorWallet(request.wallet());
            commitment.setHash(securedPayload.normalizedHash());
            commitment.setStake(request.stakeAmount());
            commitment.setEncryptedReveal(securedPayload.encryptedRevealForStorage());

            // Set pair reference and timestamp
            commitment.setPair(pair);
            OffsetDateTime committedAt = OffsetDateTime.now();
            commitment.setCommittedAt(committedAt);
            commitment.setRevealed(false);

            // Save commitment
            commitmentRepository.save(commitment);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (CommitSecurityService.CommitSecurityException ex) {
            return switch (ex.getError()) {
                case BAD_REQUEST -> ResponseEntity.badRequest().build();
                case UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                case REPLAY -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            };
        }
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
