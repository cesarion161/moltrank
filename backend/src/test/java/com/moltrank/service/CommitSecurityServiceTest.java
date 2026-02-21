package com.moltrank.service;

import com.moltrank.controller.dto.CommitPairRequest;
import com.moltrank.model.PairWinner;
import com.moltrank.repository.CommitRequestReplayGuardRepository;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommitSecurityServiceTest {

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int PAIR_ID = 7;
    private static final long STAKE = 500L;

    private CommitRequestReplayGuardRepository replayGuardRepository;
    private CommitRevealEnvelopeCryptoService revealEnvelopeCryptoService;
    private CommitSecurityService commitSecurityService;

    @BeforeEach
    void setUp() {
        replayGuardRepository = mock(CommitRequestReplayGuardRepository.class);
        doNothing().when(replayGuardRepository).deleteByExpiresAtBefore(any());
        when(replayGuardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CommitSecurityProperties properties = new CommitSecurityProperties();
        properties.setStorageKeys(Map.of("v1", "bW9sdHJhbmstY29tbWl0LWtleS12MS0zMi1ieXRlcyE="));
        properties.setActiveStorageKeyId("v1");
        properties.setMaxSignatureAgeSeconds(300);
        properties.setMaxFutureSkewSeconds(60);
        properties.setReplayWindowSeconds(900);
        properties.setAllowLegacyUnsignedCommits(false);
        properties.setAllowLegacyRevealDecode(true);

        revealEnvelopeCryptoService = new CommitRevealEnvelopeCryptoService(properties);
        commitSecurityService = new CommitSecurityService(replayGuardRepository, revealEnvelopeCryptoService, properties);
    }

    @Test
    void secureCommitPayload_acceptsValidSignatureAndEncryptsForStorage() throws Exception {
        WalletKeyPair walletKeyPair = walletKeyPair();
        byte[] nonce = parseHex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        String requestNonce = "00112233445566778899aabbccddeeff";
        long signedAt = Instant.now().getEpochSecond();

        String commitmentHash = CommitmentCodec.computeCommitmentHash(
                walletKeyPair.wallet(),
                PAIR_ID,
                PairWinner.A,
                STAKE,
                nonce
        );

        byte[] revealPayload = buildRevealPayload(PairWinner.A, nonce);
        String authMessage = CommitSecurityService.buildAuthMessageForSigning(
                walletKeyPair.wallet(),
                PAIR_ID,
                commitmentHash,
                STAKE,
                signedAt,
                requestNonce
        );
        byte[] signature = sign(walletKeyPair.privateKey(), authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] revealIv = parseHex("0102030405060708090a0b0c");
        byte[] encryptedReveal = encryptClientReveal(signature, revealIv, revealPayload, authMessage);

        CommitPairRequest request = new CommitPairRequest(
                walletKeyPair.wallet(),
                commitmentHash,
                STAKE,
                Base64.getEncoder().encodeToString(encryptedReveal),
                Base64.getEncoder().encodeToString(revealIv),
                Base64.getEncoder().encodeToString(signature),
                signedAt,
                requestNonce
        );

        CommitSecurityService.SecuredCommitmentPayload secured = commitSecurityService.secureCommitPayload(PAIR_ID, request);
        assertEquals(commitmentHash, secured.normalizedHash());
        assertNotEquals(request.encryptedReveal(), secured.encryptedRevealForStorage());

        byte[] decryptedStoredPayload =
                revealEnvelopeCryptoService.decryptFromStorageEnvelope(secured.encryptedRevealForStorage());
        assertArrayEquals(revealPayload, decryptedStoredPayload);
    }

    @Test
    void secureCommitPayload_rejectsInvalidSignature() throws Exception {
        WalletKeyPair walletKeyPair = walletKeyPair();
        byte[] nonce = parseHex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        String requestNonce = "00112233445566778899aabbccddeeff";
        long signedAt = Instant.now().getEpochSecond();

        String commitmentHash = CommitmentCodec.computeCommitmentHash(
                walletKeyPair.wallet(),
                PAIR_ID,
                PairWinner.B,
                STAKE,
                nonce
        );
        byte[] revealPayload = buildRevealPayload(PairWinner.B, nonce);
        String authMessage = CommitSecurityService.buildAuthMessageForSigning(
                walletKeyPair.wallet(),
                PAIR_ID,
                commitmentHash,
                STAKE,
                signedAt,
                requestNonce
        );

        byte[] signature = sign(walletKeyPair.privateKey(), authMessage.getBytes(StandardCharsets.UTF_8));
        signature[0] ^= (byte) 0xff;

        byte[] revealIv = parseHex("0102030405060708090a0b0c");
        byte[] encryptedReveal = encryptClientReveal(signature, revealIv, revealPayload, authMessage);

        CommitPairRequest request = new CommitPairRequest(
                walletKeyPair.wallet(),
                commitmentHash,
                STAKE,
                Base64.getEncoder().encodeToString(encryptedReveal),
                Base64.getEncoder().encodeToString(revealIv),
                Base64.getEncoder().encodeToString(signature),
                signedAt,
                requestNonce
        );

        CommitSecurityService.CommitSecurityException exception = assertThrows(
                CommitSecurityService.CommitSecurityException.class,
                () -> commitSecurityService.secureCommitPayload(PAIR_ID, request)
        );
        assertEquals(CommitSecurityService.CommitSecurityError.UNAUTHORIZED, exception.getError());
    }

    @Test
    void secureCommitPayload_rejectsReplayNonce() throws Exception {
        WalletKeyPair walletKeyPair = walletKeyPair();
        byte[] nonce = parseHex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        String requestNonce = "00112233445566778899aabbccddeeff";
        long signedAt = Instant.now().getEpochSecond();

        AtomicBoolean firstSave = new AtomicBoolean(true);
        when(replayGuardRepository.save(any())).thenAnswer(invocation -> {
            if (firstSave.getAndSet(false)) {
                return invocation.getArgument(0);
            }
            throw new DataIntegrityViolationException("duplicate nonce");
        });

        String commitmentHash = CommitmentCodec.computeCommitmentHash(
                walletKeyPair.wallet(),
                PAIR_ID,
                PairWinner.A,
                STAKE,
                nonce
        );
        byte[] revealPayload = buildRevealPayload(PairWinner.A, nonce);
        String authMessage = CommitSecurityService.buildAuthMessageForSigning(
                walletKeyPair.wallet(),
                PAIR_ID,
                commitmentHash,
                STAKE,
                signedAt,
                requestNonce
        );
        byte[] signature = sign(walletKeyPair.privateKey(), authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] revealIv = parseHex("0102030405060708090a0b0c");
        byte[] encryptedReveal = encryptClientReveal(signature, revealIv, revealPayload, authMessage);

        CommitPairRequest request = new CommitPairRequest(
                walletKeyPair.wallet(),
                commitmentHash,
                STAKE,
                Base64.getEncoder().encodeToString(encryptedReveal),
                Base64.getEncoder().encodeToString(revealIv),
                Base64.getEncoder().encodeToString(signature),
                signedAt,
                requestNonce
        );

        commitSecurityService.secureCommitPayload(PAIR_ID, request);

        CommitSecurityService.CommitSecurityException exception = assertThrows(
                CommitSecurityService.CommitSecurityException.class,
                () -> commitSecurityService.secureCommitPayload(PAIR_ID, request)
        );
        assertEquals(CommitSecurityService.CommitSecurityError.REPLAY, exception.getError());
    }

    @Test
    void secureCommitPayload_rejectsExpiredSignature() throws Exception {
        WalletKeyPair walletKeyPair = walletKeyPair();
        byte[] nonce = parseHex("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        String requestNonce = "00112233445566778899aabbccddeeff";
        long signedAt = Instant.now().minusSeconds(600).getEpochSecond();

        String commitmentHash = CommitmentCodec.computeCommitmentHash(
                walletKeyPair.wallet(),
                PAIR_ID,
                PairWinner.A,
                STAKE,
                nonce
        );
        byte[] revealPayload = buildRevealPayload(PairWinner.A, nonce);
        String authMessage = CommitSecurityService.buildAuthMessageForSigning(
                walletKeyPair.wallet(),
                PAIR_ID,
                commitmentHash,
                STAKE,
                signedAt,
                requestNonce
        );
        byte[] signature = sign(walletKeyPair.privateKey(), authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] revealIv = parseHex("0102030405060708090a0b0c");
        byte[] encryptedReveal = encryptClientReveal(signature, revealIv, revealPayload, authMessage);

        CommitPairRequest request = new CommitPairRequest(
                walletKeyPair.wallet(),
                commitmentHash,
                STAKE,
                Base64.getEncoder().encodeToString(encryptedReveal),
                Base64.getEncoder().encodeToString(revealIv),
                Base64.getEncoder().encodeToString(signature),
                signedAt,
                requestNonce
        );

        CommitSecurityService.CommitSecurityException exception = assertThrows(
                CommitSecurityService.CommitSecurityException.class,
                () -> commitSecurityService.secureCommitPayload(PAIR_ID, request)
        );
        assertEquals(CommitSecurityService.CommitSecurityError.BAD_REQUEST, exception.getError());
    }

    private static WalletKeyPair walletKeyPair() {
        byte[] seed = parseHex("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(seed, 0);
        String wallet = encodeBase58(privateKey.generatePublicKey().getEncoded());
        return new WalletKeyPair(wallet, privateKey);
    }

    private static byte[] encryptClientReveal(byte[] signature, byte[] iv, byte[] revealPayload, String authMessage)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(signature);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(authMessage.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(revealPayload);
    }

    private static byte[] sign(Ed25519PrivateKeyParameters privateKey, byte[] message) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }

    private static byte[] buildRevealPayload(PairWinner choice, byte[] nonce) {
        byte[] payload = new byte[1 + nonce.length];
        payload[0] = choice == PairWinner.A ? (byte) 0 : (byte) 1;
        System.arraycopy(nonce, 0, payload, 1, nonce.length);
        return payload;
    }

    private static String encodeBase58(byte[] bytes) {
        BigInteger value = new BigInteger(1, bytes);
        StringBuilder result = new StringBuilder();
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = value.divideAndRemainder(BigInteger.valueOf(58));
            result.append(BASE58_ALPHABET.charAt(divRem[1].intValue()));
            value = divRem[0];
        }

        for (byte b : bytes) {
            if (b == 0) {
                result.append('1');
            } else {
                break;
            }
        }
        return result.reverse().toString();
    }

    private static byte[] parseHex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int start = i * 2;
            out[i] = (byte) Integer.parseInt(value.substring(start, start + 2), 16);
        }
        return out;
    }

    private record WalletKeyPair(String wallet, Ed25519PrivateKeyParameters privateKey) {
    }
}
