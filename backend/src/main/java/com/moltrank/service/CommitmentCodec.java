package com.moltrank.service;

import com.moltrank.model.PairWinner;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Canonical commitment hash + reveal payload codec shared across frontend/backend/anchor.
 */
public final class CommitmentCodec {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE58_RADIX = BigInteger.valueOf(58);
    private static final int SOLANA_PUBKEY_BYTES = 32;

    public static final int NONCE_BYTES = 32;
    public static final int REVEAL_PAYLOAD_BYTES = 1 + NONCE_BYTES;

    private CommitmentCodec() {
    }

    public static RevealPayload decodeRevealPayloadBase64(String encodedPayload) {
        byte[] payload = Base64.getDecoder().decode(encodedPayload);
        if (payload.length != REVEAL_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "Invalid reveal payload length: expected " + REVEAL_PAYLOAD_BYTES + ", got " + payload.length);
        }

        int voteByte = Byte.toUnsignedInt(payload[0]);
        PairWinner choice = switch (voteByte) {
            case 0 -> PairWinner.A;
            case 1 -> PairWinner.B;
            default -> throw new IllegalArgumentException("Invalid reveal payload choice byte: " + voteByte);
        };

        byte[] nonce = Arrays.copyOfRange(payload, 1, payload.length);
        return new RevealPayload(choice, nonce, toHex(nonce));
    }

    public static String computeCommitmentHash(
            String wallet,
            int pairId,
            PairWinner choice,
            long stakeAmount,
            byte[] nonce) {

        if (choice != PairWinner.A && choice != PairWinner.B) {
            throw new IllegalArgumentException("Choice must be A or B");
        }
        if (pairId < 0) {
            throw new IllegalArgumentException("pairId must be non-negative");
        }
        if (stakeAmount < 0) {
            throw new IllegalArgumentException("stakeAmount must be non-negative");
        }
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("Nonce must be " + NONCE_BYTES + " bytes");
        }

        byte[] walletBytes = decodeBase58(wallet);
        if (walletBytes.length != SOLANA_PUBKEY_BYTES) {
            throw new IllegalArgumentException("Wallet must decode to 32 bytes");
        }

        byte[] pairIdBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(pairId)
                .array();
        byte[] stakeBytes = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(stakeAmount)
                .array();
        byte[] choiceByte = new byte[]{choice == PairWinner.A ? (byte) 0 : (byte) 1};

        byte[] preimage = new byte[walletBytes.length + pairIdBytes.length + choiceByte.length + stakeBytes.length + nonce.length];
        int offset = 0;
        System.arraycopy(walletBytes, 0, preimage, offset, walletBytes.length);
        offset += walletBytes.length;
        System.arraycopy(pairIdBytes, 0, preimage, offset, pairIdBytes.length);
        offset += pairIdBytes.length;
        System.arraycopy(choiceByte, 0, preimage, offset, choiceByte.length);
        offset += choiceByte.length;
        System.arraycopy(stakeBytes, 0, preimage, offset, stakeBytes.length);
        offset += stakeBytes.length;
        System.arraycopy(nonce, 0, preimage, offset, nonce.length);

        Keccak.Digest256 digest = new Keccak.Digest256();
        byte[] hash = digest.digest(preimage);
        return "0x" + toHex(hash);
    }

    public static String normalizeCommitmentHash(String hash) {
        if (hash == null) {
            throw new IllegalArgumentException("Commitment hash is required");
        }

        String trimmed = hash.trim().toLowerCase();
        String withoutPrefix = trimmed.startsWith("0x") ? trimmed.substring(2) : trimmed;
        if (!HEX_64.matcher(withoutPrefix).matches()) {
            throw new IllegalArgumentException("Commitment hash must be 64 hex characters");
        }
        return "0x" + withoutPrefix;
    }

    private static byte[] decodeBase58(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Wallet is required");
        }

        BigInteger decoded = BigInteger.ZERO;
        for (int i = 0; i < value.length(); i++) {
            int index = BASE58_ALPHABET.indexOf(value.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Wallet contains invalid base58 character");
            }
            decoded = decoded.multiply(BASE58_RADIX).add(BigInteger.valueOf(index));
        }

        byte[] decodedBytes = decoded.toByteArray();
        if (decodedBytes.length > 0 && decodedBytes[0] == 0) {
            decodedBytes = Arrays.copyOfRange(decodedBytes, 1, decodedBytes.length);
        }

        int leadingZeros = 0;
        while (leadingZeros < value.length() && value.charAt(leadingZeros) == '1') {
            leadingZeros++;
        }

        byte[] out = new byte[leadingZeros + decodedBytes.length];
        System.arraycopy(decodedBytes, 0, out, leadingZeros, decodedBytes.length);
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

    public record RevealPayload(PairWinner choice, byte[] nonce, String nonceHex) {
    }
}
