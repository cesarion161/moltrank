package com.moltrank.service;

import com.moltrank.model.Commitment;
import com.moltrank.model.Pair;
import com.moltrank.model.PairWinner;
import com.moltrank.model.Round;
import com.moltrank.repository.CommitmentRepository;
import com.moltrank.repository.PairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Auto-reveal service for committed votes.
 * After commit window closes, automatically decrypts and reveals votes on behalf of curators.
 * Handles:
 * - Decrypting encrypted payloads using session key
 * - Submitting reveal transactions to Solana
 * - Retry logic for failed transactions
 * - Grace period for manual reveals
 * - Non-reveal penalties (stake forfeiture)
 */
@Service
public class AutoRevealService {

    private static final Logger log = LoggerFactory.getLogger(AutoRevealService.class);
    private static final String FAILURE_HASH_MISMATCH = "HASH_MISMATCH";
    private static final String FAILURE_SOLANA_SUBMISSION = "SOLANA_SUBMISSION_FAILED";
    private static final String FAILURE_INVALID_REVEAL_PAYLOAD = "INVALID_REVEAL_PAYLOAD";
    private static final String FAILURE_UNEXPECTED_ERROR = "UNEXPECTED_ERROR";

    @Value("${moltrank.reveal.grace-period-minutes:30}")
    private int gracePeriodMinutes;

    @Value("${moltrank.reveal.max-retries:3}")
    private int maxRetries;

    @Value("${moltrank.reveal.retry-delay-seconds:5}")
    private int retryDelaySeconds;

    // For MVP: hardcoded session key (in production: use secure key management)
    @Value("${moltrank.session.encryption-key:moltrank-demo-session-key-32b}")
    private String sessionEncryptionKey;

    private final CommitmentRepository commitmentRepository;
    private final PairRepository pairRepository;

    public AutoRevealService(
            CommitmentRepository commitmentRepository,
            PairRepository pairRepository) {
        this.commitmentRepository = commitmentRepository;
        this.pairRepository = pairRepository;
    }

    /**
     * Auto-reveals all unrevealed commitments for a round.
     * Called when round transitions to REVEAL phase.
     *
     * @param round The round to process
     */
    @Transactional
    public void autoRevealCommitments(Round round) {
        log.info("Starting auto-reveal for round {}", round.getId());

        // Get all pairs for this round
        List<Pair> pairs = pairRepository.findByRoundId(round.getId());

        int totalCommitments = 0;
        int successfulReveals = 0;
        int failedReveals = 0;

        for (Pair pair : pairs) {
            // Get unrevealed commitments for this pair
            List<Commitment> commitments = commitmentRepository.findByPairIdAndRevealed(pair.getId(), false);
            totalCommitments += commitments.size();

            for (Commitment commitment : commitments) {
                try {
                    RevealAttemptResult revealResult = revealCommitment(commitment);
                    if (revealResult.success()) {
                        successfulReveals++;
                    } else {
                        failedReveals++;
                        markAutoRevealFailure(commitment, revealResult.failureReason());
                        sendManualRevealNotification(commitment);
                    }
                } catch (Exception e) {
                    log.error("Failed to auto-reveal commitment {} for curator {}",
                            commitment.getId(), commitment.getCuratorWallet(), e);
                    failedReveals++;

                    markAutoRevealFailure(commitment, FAILURE_UNEXPECTED_ERROR);

                    // Send push notification for manual reveal opportunity
                    sendManualRevealNotification(commitment);
                }
            }
        }

        log.info("Auto-reveal completed for round {}: {} total, {} successful, {} failed",
                round.getId(), totalCommitments, successfulReveals, failedReveals);
    }

    /**
     * Reveals a single commitment by decrypting and submitting to Solana.
     *
     * @param commitment The commitment to reveal
     * @return reveal outcome with success flag and failure reason (if any)
     */
    private RevealAttemptResult revealCommitment(Commitment commitment) {
        log.debug("Auto-revealing commitment {} for curator {}",
                commitment.getId(), commitment.getCuratorWallet());

        try {
            RevealPayload payload = decodeRevealPayload(commitment.getEncryptedReveal());

            // Verify the commitment hash matches
            if (!verifyCommitmentHash(commitment, payload)) {
                log.error("Commitment hash mismatch for commitment {}", commitment.getId());
                return RevealAttemptResult.failed(FAILURE_HASH_MISMATCH);
            }

            // Submit reveal transaction to Solana (with retries)
            boolean submitted = submitRevealToSolana(commitment, payload);

            if (submitted) {
                // Update commitment as revealed
                commitment.setRevealed(true);
                commitment.setChoice(payload.choice);
                commitment.setNonce(payload.nonceHex);
                commitment.setRevealedAt(OffsetDateTime.now());
                commitment.setAutoRevealFailed(false);
                commitment.setAutoRevealFailureReason(null);
                commitment.setAutoRevealFailedAt(null);
                commitmentRepository.save(commitment);

                log.info("Successfully revealed commitment {} with choice {}",
                        commitment.getId(), payload.choice);
                return RevealAttemptResult.succeeded();
            } else {
                log.error("Failed to submit reveal to Solana for commitment {}", commitment.getId());
                return RevealAttemptResult.failed(FAILURE_SOLANA_SUBMISSION);
            }

        } catch (IllegalArgumentException e) {
            log.error("Reveal payload is invalid for commitment {}", commitment.getId(), e);
            return RevealAttemptResult.failed(FAILURE_INVALID_REVEAL_PAYLOAD);
        } catch (Exception e) {
            log.error("Error during auto-reveal for commitment {}", commitment.getId(), e);
            return RevealAttemptResult.failed(FAILURE_UNEXPECTED_ERROR);
        }
    }

    private void markAutoRevealFailure(Commitment commitment, String failureReason) {
        commitment.setAutoRevealFailed(true);
        commitment.setAutoRevealFailureReason(failureReason);
        commitment.setAutoRevealFailedAt(OffsetDateTime.now());
        commitmentRepository.save(commitment);
    }

    /**
     * Decode canonical payload first, then fallback to legacy AES payload format.
     */
    private RevealPayload decodeRevealPayload(String encodedPayload) throws Exception {
        try {
            CommitmentCodec.RevealPayload canonical = CommitmentCodec.decodeRevealPayloadBase64(encodedPayload);
            byte[] rawPayload = Base64.getDecoder().decode(encodedPayload);
            return new RevealPayload(canonical.choice(), canonical.nonceHex(), canonical.nonce(), rawPayload);
        } catch (IllegalArgumentException canonicalError) {
            String decryptedLegacyPayload = decryptLegacyReveal(encodedPayload);
            return parseLegacyRevealPayload(decryptedLegacyPayload);
        }
    }

    /**
     * Decrypts legacy encrypted payloads using the session key.
     */
    private String decryptLegacyReveal(String encryptedPayload) throws Exception {
        // For MVP: simple AES decryption
        // In production: use proper key derivation and secure key storage

        byte[] keyBytes = sessionEncryptionKey.getBytes(StandardCharsets.UTF_8);
        // Ensure key is exactly 32 bytes for AES-256
        byte[] normalizedKey = new byte[32];
        System.arraycopy(keyBytes, 0, normalizedKey, 0, Math.min(keyBytes.length, 32));

        SecretKey secretKey = new SecretKeySpec(normalizedKey, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPayload);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Parses legacy reveal payload.
     * Expected format: "choice:nonce" (e.g., "A:abc123...").
     */
    private RevealPayload parseLegacyRevealPayload(String payload) {
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid reveal payload format: " + payload);
        }

        PairWinner choice = PairWinner.valueOf(parts[0]);
        if (choice == PairWinner.TIE) {
            throw new IllegalArgumentException("Legacy reveal payload choice must be A or B");
        }
        String nonceValue = parts[1];
        if (nonceValue.matches("^[0-9a-fA-F]{64}$")) {
            byte[] nonceBytes = parseHex(nonceValue);
            byte[] rawPayload = new byte[1 + nonceBytes.length];
            rawPayload[0] = choice == PairWinner.A ? (byte) 0 : (byte) 1;
            System.arraycopy(nonceBytes, 0, rawPayload, 1, nonceBytes.length);
            return new RevealPayload(choice, nonceValue.toLowerCase(), nonceBytes, rawPayload);
        }

        return new RevealPayload(choice, null, null, null);
    }

    /**
     * Verifies that the commitment hash matches the revealed choice and nonce.
     */
    private boolean verifyCommitmentHash(Commitment commitment, RevealPayload payload) {
        final String storedHash;
        try {
            storedHash = CommitmentCodec.normalizeCommitmentHash(commitment.getHash());
        } catch (IllegalArgumentException ex) {
            log.warn("Commitment {} has invalid stored hash format", commitment.getId(), ex);
            return false;
        }

        if (payload.nonceBytes != null && payload.choice != PairWinner.TIE) {
            String canonicalHash = CommitmentCodec.computeCommitmentHash(
                    commitment.getCuratorWallet(),
                    commitment.getPair().getId(),
                    payload.choice,
                    commitment.getStake(),
                    payload.nonceBytes
            );
            if (canonicalHash.equals(storedHash)) {
                return true;
            }
        }

        if (payload.rawPayload != null) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String legacyHash = "0x" + toHex(digest.digest(payload.rawPayload));
                if (legacyHash.equals(storedHash)) {
                    log.warn("Commitment {} matched legacy SHA-256 format; canonical keccak expected", commitment.getId());
                    return true;
                }
            } catch (Exception ex) {
                log.warn("Unable to compute legacy SHA-256 fallback hash for commitment {}", commitment.getId(), ex);
            }
        }

        return false;
    }

    /**
     * Submits a reveal transaction to Solana with retry logic.
     */
    private boolean submitRevealToSolana(Commitment commitment, RevealPayload payload) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Submitting reveal to Solana (attempt {}/{})", attempt, maxRetries);

                // TODO: Implement actual Solana transaction submission
                // For MVP: simulate success
                boolean success = simulateSolanaTransaction(commitment, payload);

                if (success) {
                    log.info("Successfully submitted reveal to Solana for commitment {}", commitment.getId());
                    return true;
                } else {
                    log.warn("Solana transaction failed for commitment {} (attempt {})", commitment.getId(), attempt);
                }

            } catch (Exception e) {
                log.error("Error submitting reveal to Solana (attempt {}): {}", attempt, e.getMessage());
            }

            // Retry delay
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryDelaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        log.error("Failed to submit reveal to Solana after {} attempts", maxRetries);
        return false;
    }

    /**
     * Simulates Solana transaction for MVP.
     * In production: use Solana Web3 client to submit actual transaction.
     */
    private boolean simulateSolanaTransaction(Commitment commitment, RevealPayload payload) {
        // For MVP: assume success
        log.debug("SIMULATION: Reveal transaction for commitment {} with choice {}",
                commitment.getId(), payload.choice);
        return true;
    }

    /**
     * Sends a push notification to curator for manual reveal opportunity.
     * Called when auto-reveal fails.
     */
    private void sendManualRevealNotification(Commitment commitment) {
        log.info("Sending manual reveal notification to curator: {}", commitment.getCuratorWallet());

        // TODO: Implement actual push notification
        // For MVP: just log the notification

        OffsetDateTime gracePeriodEnd = commitment.getCommittedAt().plusMinutes(gracePeriodMinutes);
        log.info("Curator {} has until {} to manually reveal commitment {}",
                commitment.getCuratorWallet(), gracePeriodEnd, commitment.getId());
    }

    /**
     * Checks for commitments that exceeded grace period without revealing.
     * Called periodically to enforce non-reveal penalties.
     */
    @Transactional
    public void enforceNonRevealPenalties() {
        OffsetDateTime gracePeriodCutoff = OffsetDateTime.now().minusMinutes(gracePeriodMinutes);

        List<Commitment> unrevealedCommitments = commitmentRepository.findByRevealed(false);

        for (Commitment commitment : unrevealedCommitments) {
            if (commitment.getCommittedAt().isBefore(gracePeriodCutoff)) {
                log.warn("Commitment {} exceeded grace period, forfeiting stake of {}",
                        commitment.getId(), commitment.getStake());

                // TODO: Implement stake forfeiture logic
                // For MVP: just log the penalty

                // Mark as revealed to prevent repeated penalties
                commitment.setRevealed(true);
                commitment.setRevealedAt(OffsetDateTime.now());
                commitmentRepository.save(commitment);
            }
        }
    }

    /**
     * Internal class for parsed reveal payload.
     */
    private record RevealPayload(PairWinner choice, String nonceHex, byte[] nonceBytes, byte[] rawPayload) {
    }

    private record RevealAttemptResult(boolean success, String failureReason) {
        private static RevealAttemptResult succeeded() {
            return new RevealAttemptResult(true, null);
        }

        private static RevealAttemptResult failed(String failureReason) {
            return new RevealAttemptResult(false, failureReason);
        }
    }

    private static byte[] parseHex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int start = i * 2;
            out[i] = (byte) Integer.parseInt(value.substring(start, start + 2), 16);
        }
        return out;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            out.append(Character.forDigit(value & 0x0f, 16));
        }
        return out.toString();
    }
}
