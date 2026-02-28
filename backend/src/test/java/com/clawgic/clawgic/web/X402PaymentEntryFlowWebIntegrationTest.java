package com.clawgic.clawgic.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.clawgic.clawgic.config.X402Properties;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicPaymentAuthorization;
import com.clawgic.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.clawgic.clawgic.model.ClawgicProviderType;
import com.clawgic.clawgic.model.ClawgicStakingLedger;
import com.clawgic.clawgic.model.ClawgicStakingLedgerStatus;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentEntry;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.model.ClawgicUser;
import com.clawgic.clawgic.repository.ClawgicAgentRepository;
import com.clawgic.clawgic.repository.ClawgicPaymentAuthorizationRepository;
import com.clawgic.clawgic.repository.ClawgicStakingLedgerRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentEntryRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentRepository;
import com.clawgic.clawgic.repository.ClawgicUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=${C10_TEST_DB_URL:jdbc:postgresql://localhost:5432/clawgic}",
        "spring.datasource.username=${C10_TEST_DB_USERNAME:clawgic}",
        "spring.datasource.password=${C10_TEST_DB_PASSWORD:changeme}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "x402.enabled=true",
        "x402.dev-bypass-enabled=false",
        "x402.chain-id=84532",
        "x402.token-address=0x0000000000000000000000000000000000000a11",
        "x402.settlement-address=0x0000000000000000000000000000000000000b22",
        "x402.eip3009-domain-name=USD Coin",
        "x402.eip3009-domain-version=2",
        "x402.token-decimals=6"
})
@AutoConfigureMockMvc
@Transactional
class X402PaymentEntryFlowWebIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PRIVATE_KEY_HEX =
            "0x59c6995e998f97a5a0044976f4f3e6f7f2ee8f87f3d4f3f9127b8fcdab8f5b7d";
    private static final ECKeyPair SIGNER_KEY_PAIR =
            ECKeyPair.create(Numeric.hexStringToByteArray(PRIVATE_KEY_HEX));
    private static final String SIGNER_WALLET = "0x" + Keys.getAddress(SIGNER_KEY_PAIR.getPublicKey());
    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository;

    @Autowired
    private ClawgicTournamentEntryRepository clawgicTournamentEntryRepository;

    @Autowired
    private ClawgicStakingLedgerRepository clawgicStakingLedgerRepository;

    @Autowired
    private X402Properties x402Properties;

    @Test
    void interceptorRouteWithValidSignedHeaderCreatesEntryAndPersistsAuthorizedRecord() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID agentId = createAgent(SIGNER_WALLET, "web x402 accepted");
        ClawgicTournament tournament = insertTournament(
                "C33 interceptor path",
                now.plusHours(2),
                now.plusHours(1)
        );

        String paymentHeader = buildSignedPaymentHeader(
                "req-c33-web-ok-01-" + RUN_ID,
                "idem-c33-web-ok-01-" + RUN_ID,
                randomAuthorizationNonceHex(),
                SIGNER_WALLET,
                x402Properties.getSettlementAddress(),
                x402Properties.getTokenAddress(),
                x402Properties.getChainId(),
                new BigInteger("5000000"),
                Instant.now().getEpochSecond() - 60,
                Instant.now().getEpochSecond() + 600
        );

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournament.getTournamentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-PAYMENT", paymentHeader)
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agentId").value(agentId.toString()));

        List<ClawgicPaymentAuthorization> authorizations =
                clawgicPaymentAuthorizationRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, authorizations.size());
        ClawgicPaymentAuthorization authorization = authorizations.getFirst();
        assertEquals(ClawgicPaymentAuthorizationStatus.AUTHORIZED, authorization.getStatus());
        assertNotNull(authorization.getVerifiedAt());

        List<ClawgicTournamentEntry> entries =
                clawgicTournamentEntryRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, entries.size());
        assertEquals(agentId, entries.getFirst().getAgentId());
        assertEquals(entries.getFirst().getEntryId(), authorization.getEntryId());

        List<ClawgicStakingLedger> ledgers =
                clawgicStakingLedgerRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, ledgers.size());
        assertEquals(ClawgicStakingLedgerStatus.ENTERED, ledgers.getFirst().getStatus());
    }

    @Test
    void malformedPaymentHeaderReturnsBadRequestInsteadOfTransactionRollbackFailure() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        createUser(SIGNER_WALLET);
        UUID agentId = createAgent(SIGNER_WALLET, "web x402 malformed");
        ClawgicTournament tournament = insertTournament(
                "C53 malformed header flow",
                now.plusHours(2),
                now.plusHours(1)
        );

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournament.getTournamentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-PAYMENT", "not-json")
                        .content("""
                                {
                                  "agentId": "%s"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("x402_malformed_payment_header"))
                .andExpect(jsonPath("$.message").value("X-PAYMENT header must be valid JSON"));

        List<ClawgicPaymentAuthorization> authorizations =
                clawgicPaymentAuthorizationRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(0, authorizations.size());

        List<ClawgicTournamentEntry> entries =
                clawgicTournamentEntryRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(0, entries.size());
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
        message.put("nonce", "0x" + Numeric.cleanHexPrefix(nonceHex));

        StructuredDataEncoder encoder = new StructuredDataEncoder(
                OBJECT_MAPPER.writeValueAsString(typedData)
        );
        byte[] digest = encoder.hashStructuredData();
        Sign.SignatureData signatureData = Sign.signMessage(digest, SIGNER_KEY_PAIR, false);
        return signatureDataToHex(signatureData);
    }

    private static ObjectNode typeField(String name, String type) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("name", name);
        node.put("type", type);
        return node;
    }

    private static String randomAuthorizationNonceHex() {
        return "0x"
                + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
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
