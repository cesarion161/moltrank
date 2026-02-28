package com.moltrank.clawgic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.config.X402Properties;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.dto.ClawgicTournamentRequests;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorization;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.moltrank.clawgic.model.ClawgicProviderType;
import com.moltrank.clawgic.model.ClawgicStakingLedger;
import com.moltrank.clawgic.model.ClawgicStakingLedgerStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentEntryStatus;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicPaymentAuthorizationRepository;
import com.moltrank.clawgic.repository.ClawgicStakingLedgerRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentEntryRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import com.moltrank.clawgic.repository.ClawgicUserRepository;
import com.moltrank.clawgic.web.X402PaymentRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/moltrank}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:moltrank}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "moltrank.ingestion.enabled=false",
        "moltrank.ingestion.run-on-startup=false",
        "x402.enabled=true",
        "x402.dev-bypass-enabled=false",
        "x402.chain-id=84532",
        "x402.token-address=0x0000000000000000000000000000000000000a11",
        "x402.settlement-address=0x0000000000000000000000000000000000000b22",
        "x402.eip3009-domain-name=USD Coin",
        "x402.eip3009-domain-version=2",
        "x402.token-decimals=6"
})
@Transactional
class ClawgicTournamentX402AuthorizationIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PRIVATE_KEY_HEX =
            "0x59c6995e998f97a5a0044976f4f3e6f7f2ee8f87f3d4f3f9127b8fcdab8f5b7d";
    private static final ECKeyPair SIGNER_KEY_PAIR =
            ECKeyPair.create(Numeric.hexStringToByteArray(PRIVATE_KEY_HEX));
    private static final String SIGNER_WALLET = "0x" + Keys.getAddress(SIGNER_KEY_PAIR.getPublicKey());
    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private ClawgicTournamentService clawgicTournamentService;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicTournamentEntryRepository clawgicTournamentEntryRepository;

    @Autowired
    private ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository;

    @Autowired
    private X402Properties x402Properties;

    @Autowired
    private ClawgicStakingLedgerRepository clawgicStakingLedgerRepository;

    @Test
    void enterTournamentWithValidX402HeaderCreatesEntryAndLedgerWithAuthorizedPayment() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID agentId = createAgent(SIGNER_WALLET, "x402 accepted");
        ClawgicTournament tournament = insertTournament(
                "C33 accepted auth record",
                now.plusHours(2),
                now.plusHours(1)
        );

        String paymentHeader = buildSignedPaymentHeader(
                "req-c33-accepted-01-" + RUN_ID,
                "idem-c33-accepted-01-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );

        ClawgicTournamentResponses.TournamentEntry createdEntry =
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(agentId),
                        paymentHeader
                );
        assertEquals(tournament.getTournamentId(), createdEntry.tournamentId());
        assertEquals(agentId, createdEntry.agentId());
        assertEquals(ClawgicTournamentEntryStatus.CONFIRMED, createdEntry.status());

        List<ClawgicPaymentAuthorization> authorizations =
                clawgicPaymentAuthorizationRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, authorizations.size());
        ClawgicPaymentAuthorization authorization = authorizations.getFirst();
        assertEquals(ClawgicPaymentAuthorizationStatus.AUTHORIZED, authorization.getStatus());
        assertEquals(createdEntry.entryId(), authorization.getEntryId());
        assertEquals("req-c33-accepted-01-" + RUN_ID, authorization.getRequestNonce());
        assertEquals("idem-c33-accepted-01-" + RUN_ID, authorization.getIdempotencyKey());
        assertEquals(new BigDecimal("5.000000"), authorization.getAmountAuthorizedUsdc());
        assertEquals(x402Properties.getChainId(), authorization.getChainId());
        assertEquals(x402Properties.getSettlementAddress(), authorization.getRecipientWalletAddress());
        assertNotNull(authorization.getVerifiedAt());

        assertEquals(1, clawgicTournamentEntryRepository
                .findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId()).size());

        List<ClawgicStakingLedger> ledgers =
                clawgicStakingLedgerRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, ledgers.size());
        ClawgicStakingLedger ledger = ledgers.getFirst();
        assertEquals(ClawgicStakingLedgerStatus.ENTERED, ledger.getStatus());
        assertEquals(createdEntry.entryId(), ledger.getEntryId());
        assertEquals(authorization.getPaymentAuthorizationId(), ledger.getPaymentAuthorizationId());
        assertEquals(new BigDecimal("5.000000"), ledger.getAmountStaked());
        assertNotNull(ledger.getEnteredAt());
    }

    @Test
    void enterTournamentDuplicateRetryReturnsExistingEntryWithoutExtraAuthorizationRows() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID agentId = createAgent(SIGNER_WALLET, "x402 idempotent retry");
        ClawgicTournament tournament = insertTournament(
                "C34 duplicate retry",
                now.plusHours(2),
                now.plusHours(1)
        );

        String initialHeader = buildSignedPaymentHeader(
                "req-c34-idempotent-01-" + RUN_ID,
                "idem-c34-idempotent-01-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );
        ClawgicTournamentResponses.TournamentEntry firstEntry =
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(agentId),
                        initialHeader
                );

        String retryHeader = buildSignedPaymentHeader(
                "req-c34-idempotent-02-" + RUN_ID,
                "idem-c34-idempotent-02-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );
        ClawgicTournamentResponses.TournamentEntry retriedEntry =
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(agentId),
                        retryHeader
                );

        assertEquals(firstEntry.entryId(), retriedEntry.entryId());
        assertEquals(1, clawgicPaymentAuthorizationRepository
                .findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId())
                .size());
        assertEquals(1, clawgicStakingLedgerRepository
                .findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId())
                .size());
    }

    @Test
    void enterTournamentWithInvalidSignaturePersistsRejectedAuthorizationRecord() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID agentId = createAgent(SIGNER_WALLET, "x402 bad signature");
        ClawgicTournament tournament = insertTournament(
                "C33 invalid signature",
                now.plusHours(2),
                now.plusHours(1)
        );

        String validHeader = buildSignedPaymentHeader(
                "req-c33-bad-sig-01-" + RUN_ID,
                "idem-c33-bad-sig-01-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );
        String tamperedHeader = tamperSignature(validHeader);

        X402PaymentRequestException ex = assertThrows(X402PaymentRequestException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(agentId),
                        tamperedHeader
                )
        );

        assertEquals(HttpStatus.PAYMENT_REQUIRED, ex.getStatus());
        assertEquals("x402_verification_failed", ex.getCode());

        List<ClawgicPaymentAuthorization> authorizations =
                clawgicPaymentAuthorizationRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, authorizations.size());
        ClawgicPaymentAuthorization authorization = authorizations.getFirst();
        assertEquals(ClawgicPaymentAuthorizationStatus.REJECTED, authorization.getStatus());
        assertNotNull(authorization.getFailureReason());
        assertTrue(authorization.getFailureReason().contains("signature"));
    }

    @Test
    void enterTournamentRejectsMalformedX402Header() {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID agentId = createAgent(SIGNER_WALLET, "x402 malformed");
        ClawgicTournament tournament = insertTournament(
                "C32 malformed payload",
                now.plusHours(2),
                now.plusHours(1)
        );

        X402PaymentRequestException ex = assertThrows(X402PaymentRequestException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(agentId),
                        "not-json"
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("x402_malformed_payment_header", ex.getCode());
        assertTrue(clawgicPaymentAuthorizationRepository
                .findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId())
                .isEmpty());
    }

    @Test
    void enterTournamentRejectsDuplicateRequestNonceReplay() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID firstAgentId = createAgent(SIGNER_WALLET, "x402 duplicate nonce first");
        UUID secondAgentId = createAgent(SIGNER_WALLET, "x402 duplicate nonce second");
        ClawgicTournament tournament = insertTournament(
                "C32 duplicate nonce replay",
                now.plusHours(2),
                now.plusHours(1)
        );

        String firstHeader = buildSignedPaymentHeader(
                "req-c32-dup-nonce-01-" + RUN_ID,
                "idem-c32-dup-nonce-01-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );

        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(firstAgentId),
                firstHeader
        );

        String replayHeader = buildSignedPaymentHeader(
                "req-c32-dup-nonce-01-" + RUN_ID,
                "idem-c32-dup-nonce-02-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );

        X402PaymentRequestException replay = assertThrows(X402PaymentRequestException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(secondAgentId),
                        replayHeader
                )
        );

        assertEquals(HttpStatus.CONFLICT, replay.getStatus());
        assertEquals("x402_replay_rejected", replay.getCode());
        assertEquals(1, clawgicPaymentAuthorizationRepository
                .findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId())
                .size());
    }

    @Test
    void enterTournamentRejectsDuplicateIdempotencyKeyReplay() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID firstAgentId = createAgent(SIGNER_WALLET, "x402 duplicate idempotency first");
        UUID secondAgentId = createAgent(SIGNER_WALLET, "x402 duplicate idempotency second");
        ClawgicTournament tournament = insertTournament(
                "C32 duplicate idempotency replay",
                now.plusHours(2),
                now.plusHours(1)
        );

        String firstHeader = buildSignedPaymentHeader(
                "req-c32-dup-idem-01-" + RUN_ID,
                "idem-c32-dup-idem-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );

        clawgicTournamentService.enterTournament(
                tournament.getTournamentId(),
                new ClawgicTournamentRequests.EnterTournamentRequest(firstAgentId),
                firstHeader
        );

        String replayHeader = buildSignedPaymentHeader(
                "req-c32-dup-idem-02-" + RUN_ID,
                "idem-c32-dup-idem-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );

        X402PaymentRequestException replay = assertThrows(X402PaymentRequestException.class, () ->
                clawgicTournamentService.enterTournament(
                        tournament.getTournamentId(),
                        new ClawgicTournamentRequests.EnterTournamentRequest(secondAgentId),
                        replayHeader
                )
        );

        assertEquals(HttpStatus.CONFLICT, replay.getStatus());
        assertEquals("x402_replay_rejected", replay.getCode());
        assertEquals(1, clawgicPaymentAuthorizationRepository
                .findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId())
                .size());
    }

    private static String tamperSignature(String paymentHeaderJson) throws Exception {
        ObjectNode root = (ObjectNode) OBJECT_MAPPER.readTree(paymentHeaderJson);
        ObjectNode authorization = (ObjectNode) root.path("payload").path("authorization");
        String signature = authorization.path("signature").asText();
        int tamperIndex = Math.min(10, signature.length() - 1);
        char currentChar = signature.charAt(tamperIndex);
        authorization.put(
                "signature",
                signature.substring(0, tamperIndex)
                        + (currentChar == 'a' ? 'b' : 'a')
                        + signature.substring(tamperIndex + 1)
        );
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    private static String buildSignedPaymentHeader(
            String requestNonce,
            String idempotencyKey,
            String authorizationNonceHex,
            String from,
            String to,
            String tokenAddress,
            long chainId,
            BigInteger value,
            long validAfter,
            long validBefore
    ) throws Exception {
        String signatureHex = signTransferWithAuthorization(
                from,
                to,
                tokenAddress,
                chainId,
                value,
                validAfter,
                validBefore,
                authorizationNonceHex
        );

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("requestNonce", requestNonce);
        root.put("idempotencyKey", idempotencyKey);

        ObjectNode payload = root.putObject("payload");
        payload.put("authorizationNonce", authorizationNonceHex);

        ObjectNode domain = payload.putObject("domain");
        domain.put("name", "USD Coin");
        domain.put("version", "2");
        domain.put("chainId", chainId);
        domain.put("verifyingContract", tokenAddress);

        ObjectNode authorization = payload.putObject("authorization");
        authorization.put("from", from);
        authorization.put("to", to);
        authorization.put("value", value.toString());
        authorization.put("validAfter", validAfter);
        authorization.put("validBefore", validBefore);
        authorization.put("nonce", authorizationNonceHex);
        authorization.put("signature", signatureHex);

        return OBJECT_MAPPER.writeValueAsString(root);
    }

    private static String signTransferWithAuthorization(
            String from,
            String to,
            String tokenAddress,
            long chainId,
            BigInteger value,
            long validAfter,
            long validBefore,
            String nonceHex
    ) throws Exception {
        ObjectNode typedData = OBJECT_MAPPER.createObjectNode();
        ObjectNode types = typedData.putObject("types");

        ArrayNode domainTypes = types.putArray("EIP712Domain");
        domainTypes.add(typeField("name", "string"));
        domainTypes.add(typeField("version", "string"));
        domainTypes.add(typeField("chainId", "uint256"));
        domainTypes.add(typeField("verifyingContract", "address"));

        ArrayNode transferTypes = types.putArray("TransferWithAuthorization");
        transferTypes.add(typeField("from", "address"));
        transferTypes.add(typeField("to", "address"));
        transferTypes.add(typeField("value", "uint256"));
        transferTypes.add(typeField("validAfter", "uint256"));
        transferTypes.add(typeField("validBefore", "uint256"));
        transferTypes.add(typeField("nonce", "bytes32"));

        typedData.put("primaryType", "TransferWithAuthorization");

        ObjectNode domain = typedData.putObject("domain");
        domain.put("name", "USD Coin");
        domain.put("version", "2");
        domain.put("chainId", chainId);
        domain.put("verifyingContract", tokenAddress);

        ObjectNode message = typedData.putObject("message");
        message.put("from", from);
        message.put("to", to);
        message.put("value", value.toString());
        message.put("validAfter", String.valueOf(validAfter));
        message.put("validBefore", String.valueOf(validBefore));
        message.put("nonce", authorizationNonceWithPrefix(nonceHex));

        StructuredDataEncoder encoder = new StructuredDataEncoder(
                OBJECT_MAPPER.writeValueAsString(typedData)
        );
        byte[] digest = encoder.hashStructuredData();
        Sign.SignatureData signatureData = Sign.signMessage(digest, SIGNER_KEY_PAIR, false);
        return signatureDataToHex(signatureData);
    }

    private static String authorizationNonceWithPrefix(String nonceHex) {
        String clean = Numeric.cleanHexPrefix(nonceHex);
        return "0x" + clean;
    }

    private static String randomAuthorizationNonceHex() {
        return "0x"
                + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private static ObjectNode typeField(String name, String type) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("name", name);
        node.put("type", type);
        return node;
    }

    private static String signatureDataToHex(Sign.SignatureData signatureData) {
        byte[] signature = new byte[65];
        System.arraycopy(signatureData.getR(), 0, signature, 0, 32);
        System.arraycopy(signatureData.getS(), 0, signature, 32, 32);
        signature[64] = signatureData.getV()[0];
        return Numeric.toHexString(signature);
    }

    private void createUser(String walletAddress) {
        if (clawgicUserRepository.existsById(walletAddress)) {
            return;
        }
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        clawgicUserRepository.saveAndFlush(user);
    }

    private UUID createAgent(String walletAddress, String name) {
        UUID agentId = UUID.randomUUID();
        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(agentId);
        agent.setWalletAddress(walletAddress);
        agent.setName(name);
        agent.setSystemPrompt("Debate with concise deterministic structure.");
        agent.setApiKeyEncrypted("enc:test-key");
        agent.setProviderType(ClawgicProviderType.OPENAI);
        agent.setCreatedAt(OffsetDateTime.now());
        agent.setUpdatedAt(OffsetDateTime.now());
        clawgicAgentRepository.saveAndFlush(agent);
        return agentId;
    }

    private ClawgicTournament insertTournament(
            String topic,
            OffsetDateTime startTime,
            OffsetDateTime entryCloseTime
    ) {
        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(topic);
        tournament.setStatus(ClawgicTournamentStatus.SCHEDULED);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(4);
        tournament.setStartTime(startTime);
        tournament.setEntryCloseTime(entryCloseTime);
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setCreatedAt(entryCloseTime.minusMinutes(10));
        tournament.setUpdatedAt(entryCloseTime.minusMinutes(10));
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }
}
