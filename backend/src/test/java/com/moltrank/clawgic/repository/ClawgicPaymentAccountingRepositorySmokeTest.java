package com.moltrank.clawgic.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorization;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.moltrank.clawgic.model.ClawgicStakingLedger;
import com.moltrank.clawgic.model.ClawgicStakingLedgerStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentEntry;
import com.moltrank.clawgic.model.ClawgicTournamentEntryStatus;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.ClawgicUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        "moltrank.ingestion.run-on-startup=false"
})
@Transactional
class ClawgicPaymentAccountingRepositorySmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClawgicUserRepository clawgicUserRepository;

    @Autowired
    private ClawgicAgentRepository clawgicAgentRepository;

    @Autowired
    private ClawgicTournamentRepository clawgicTournamentRepository;

    @Autowired
    private ClawgicTournamentEntryRepository clawgicTournamentEntryRepository;

    @Autowired
    private ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository;

    @Autowired
    private ClawgicStakingLedgerRepository clawgicStakingLedgerRepository;

    @Test
    void paymentAuthAndStakingLedgerRepositoriesRoundTripRecordsAndReplayGuardsAreQueryable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('clawgic_payment_authorizations', 'clawgic_staking_ledger')
                        """,
                Integer.class
        );
        assertEquals(2, tableCount);

        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x5555555555555555555555555555555555555555";
        createUser(walletAddress);
        UUID agentId = createAgent(walletAddress, "Payment Guard Agent");
        ClawgicTournament tournament = createTournament(
                "Debate: Should payment replay guards be enforced at the DB layer?",
                now.plusHours(2),
                now.plusHours(1)
        );
        ClawgicTournamentEntry entry = createEntry(tournament.getTournamentId(), agentId, walletAddress, now);

        ObjectNode paymentHeaderJson = OBJECT_MAPPER.createObjectNode();
        paymentHeaderJson.put("scheme", "x402");
        paymentHeaderJson.put("requestNonce", "req-c13-roundtrip");
        ObjectNode payload = paymentHeaderJson.putObject("payload");
        payload.put("idempotencyKey", "idem-c13-roundtrip");
        payload.put("authorizationNonce", "auth-c13-roundtrip");
        payload.put("amount", "5.000000");

        ClawgicPaymentAuthorization authorization = new ClawgicPaymentAuthorization();
        UUID paymentAuthorizationId = UUID.randomUUID();
        authorization.setPaymentAuthorizationId(paymentAuthorizationId);
        authorization.setTournamentId(tournament.getTournamentId());
        authorization.setEntryId(entry.getEntryId());
        authorization.setAgentId(agentId);
        authorization.setWalletAddress(walletAddress);
        authorization.setRequestNonce("req-c13-roundtrip");
        authorization.setIdempotencyKey("idem-c13-roundtrip");
        authorization.setAuthorizationNonce("auth-c13-roundtrip");
        authorization.setStatus(ClawgicPaymentAuthorizationStatus.AUTHORIZED);
        authorization.setPaymentHeaderJson(paymentHeaderJson);
        authorization.setAmountAuthorizedUsdc(new BigDecimal("5.000000"));
        authorization.setChainId(84532L);
        authorization.setRecipientWalletAddress("0x000000000000000000000000000000000000c13a");
        authorization.setReceivedAt(now.minusMinutes(1));
        authorization.setVerifiedAt(now);
        authorization.setCreatedAt(now.minusMinutes(1));
        authorization.setUpdatedAt(now);
        clawgicPaymentAuthorizationRepository.saveAndFlush(authorization);

        ClawgicStakingLedger stakingLedger = new ClawgicStakingLedger();
        UUID stakeId = UUID.randomUUID();
        stakingLedger.setStakeId(stakeId);
        stakingLedger.setTournamentId(tournament.getTournamentId());
        stakingLedger.setEntryId(entry.getEntryId());
        stakingLedger.setPaymentAuthorizationId(paymentAuthorizationId);
        stakingLedger.setAgentId(agentId);
        stakingLedger.setWalletAddress(walletAddress);
        stakingLedger.setAmountStaked(new BigDecimal("5.000000"));
        stakingLedger.setJudgeFeeDeducted(new BigDecimal("0.750000"));
        stakingLedger.setSystemRetention(new BigDecimal("0.250000"));
        stakingLedger.setRewardPayout(BigDecimal.ZERO);
        stakingLedger.setStatus(ClawgicStakingLedgerStatus.ENTERED);
        stakingLedger.setSettlementNote("Entry accepted; payout pending tournament resolution.");
        stakingLedger.setAuthorizedAt(now.minusMinutes(1));
        stakingLedger.setEnteredAt(now);
        stakingLedger.setCreatedAt(now.minusMinutes(1));
        stakingLedger.setUpdatedAt(now.plusSeconds(5));
        clawgicStakingLedgerRepository.saveAndFlush(stakingLedger);

        assertTrue(clawgicPaymentAuthorizationRepository.existsByWalletAddressAndRequestNonce(
                walletAddress,
                "req-c13-roundtrip"
        ));
        assertTrue(clawgicPaymentAuthorizationRepository.existsByWalletAddressAndIdempotencyKey(
                walletAddress,
                "idem-c13-roundtrip"
        ));

        List<ClawgicPaymentAuthorization> authRows =
                clawgicPaymentAuthorizationRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, authRows.size());
        ClawgicPaymentAuthorization persistedAuthorization = authRows.getFirst();
        assertEquals(paymentAuthorizationId, persistedAuthorization.getPaymentAuthorizationId());
        assertEquals(ClawgicPaymentAuthorizationStatus.AUTHORIZED, persistedAuthorization.getStatus());
        assertJsonEquals(paymentHeaderJson, persistedAuthorization.getPaymentHeaderJson());

        ClawgicStakingLedger persistedLedger =
                clawgicStakingLedgerRepository.findByPaymentAuthorizationId(paymentAuthorizationId).orElseThrow();
        assertEquals(stakeId, persistedLedger.getStakeId());
        assertEquals(ClawgicStakingLedgerStatus.ENTERED, persistedLedger.getStatus());
        assertEquals(new BigDecimal("5.000000"), persistedLedger.getAmountStaked());
        assertEquals(new BigDecimal("0.750000"), persistedLedger.getJudgeFeeDeducted());
        assertEquals(new BigDecimal("0.250000"), persistedLedger.getSystemRetention());
        assertNotNull(persistedLedger.getEnteredAt());

        List<ClawgicStakingLedger> ledgerRows =
                clawgicStakingLedgerRepository.findByTournamentIdOrderByCreatedAtAsc(tournament.getTournamentId());
        assertEquals(1, ledgerRows.size());
        assertEquals(stakeId, ledgerRows.getFirst().getStakeId());

        Integer jsonbRowCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM clawgic_payment_authorizations
                        WHERE payment_authorization_id = ?
                          AND jsonb_typeof(payment_header_json) = 'object'
                        """,
                Integer.class,
                paymentAuthorizationId
        );
        assertEquals(1, jsonbRowCount);
    }

    @Test
    void paymentAuthorizationReplayGuardRejectsDuplicateWalletAndRequestNonce() {
        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x6666666666666666666666666666666666666666";
        createUser(walletAddress);
        UUID agentId = createAgent(walletAddress, "Replay Nonce Guard Agent");
        ClawgicTournament tournament = createTournament(
                "Debate: Can nonces be optional?",
                now.plusHours(2),
                now.plusHours(1)
        );

        clawgicPaymentAuthorizationRepository.saveAndFlush(newPaymentAuthorization(
                tournament.getTournamentId(),
                agentId,
                walletAddress,
                "req-c13-dup-nonce",
                "idem-c13-dup-nonce-1",
                "auth-c13-dup-nonce-1",
                now
        ));

        assertThrows(DataIntegrityViolationException.class, () -> {
            clawgicPaymentAuthorizationRepository.save(newPaymentAuthorization(
                    tournament.getTournamentId(),
                    agentId,
                    walletAddress,
                    "req-c13-dup-nonce",
                    "idem-c13-dup-nonce-2",
                    "auth-c13-dup-nonce-2",
                    now.plusSeconds(1)
            ));
            clawgicPaymentAuthorizationRepository.flush();
        });
    }

    @Test
    void paymentAuthorizationReplayGuardRejectsDuplicateWalletAndIdempotencyKey() {
        OffsetDateTime now = OffsetDateTime.now();
        String walletAddress = "0x7777777777777777777777777777777777777777";
        createUser(walletAddress);
        UUID agentId = createAgent(walletAddress, "Idempotency Guard Agent");
        ClawgicTournament tournament = createTournament(
                "Debate: Are idempotency keys overkill?",
                now.plusHours(2),
                now.plusHours(1)
        );

        clawgicPaymentAuthorizationRepository.saveAndFlush(newPaymentAuthorization(
                tournament.getTournamentId(),
                agentId,
                walletAddress,
                "req-c13-dup-idem-1",
                "idem-c13-dup-idem",
                "auth-c13-dup-idem-1",
                now
        ));

        assertThrows(DataIntegrityViolationException.class, () -> {
            clawgicPaymentAuthorizationRepository.save(newPaymentAuthorization(
                    tournament.getTournamentId(),
                    agentId,
                    walletAddress,
                    "req-c13-dup-idem-2",
                    "idem-c13-dup-idem",
                    "auth-c13-dup-idem-2",
                    now.plusSeconds(1)
            ));
            clawgicPaymentAuthorizationRepository.flush();
        });
    }

    private static void assertJsonEquals(JsonNode expected, JsonNode actual) {
        assertNotNull(actual);
        assertEquals(expected, actual);
    }

    private void createUser(String walletAddress) {
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        clawgicUserRepository.saveAndFlush(user);
    }

    private UUID createAgent(String walletAddress, String name) {
        ClawgicAgent agent = new ClawgicAgent();
        UUID agentId = UUID.randomUUID();
        agent.setAgentId(agentId);
        agent.setWalletAddress(walletAddress);
        agent.setName(name);
        agent.setSystemPrompt("Debate clearly and prioritize deterministic testability.");
        agent.setApiKeyEncrypted("enc:test");
        clawgicAgentRepository.saveAndFlush(agent);
        return agentId;
    }

    private ClawgicTournament createTournament(String topic, OffsetDateTime startTime, OffsetDateTime entryCloseTime) {
        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(topic);
        tournament.setStatus(ClawgicTournamentStatus.SCHEDULED);
        tournament.setBracketSize(4);
        tournament.setMaxEntries(4);
        tournament.setStartTime(startTime);
        tournament.setEntryCloseTime(entryCloseTime);
        tournament.setBaseEntryFeeUsdc(new BigDecimal("5.000000"));
        tournament.setCreatedAt(entryCloseTime.minusHours(1));
        tournament.setUpdatedAt(entryCloseTime.minusHours(1));
        return clawgicTournamentRepository.saveAndFlush(tournament);
    }

    private ClawgicTournamentEntry createEntry(
            UUID tournamentId,
            UUID agentId,
            String walletAddress,
            OffsetDateTime createdAt
    ) {
        ClawgicTournamentEntry entry = new ClawgicTournamentEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setTournamentId(tournamentId);
        entry.setAgentId(agentId);
        entry.setWalletAddress(walletAddress);
        entry.setStatus(ClawgicTournamentEntryStatus.PENDING_PAYMENT);
        entry.setSeedSnapshotElo(1000);
        entry.setCreatedAt(createdAt);
        entry.setUpdatedAt(createdAt);
        return clawgicTournamentEntryRepository.saveAndFlush(entry);
    }

    private ClawgicPaymentAuthorization newPaymentAuthorization(
            UUID tournamentId,
            UUID agentId,
            String walletAddress,
            String requestNonce,
            String idempotencyKey,
            String authorizationNonce,
            OffsetDateTime now
    ) {
        ClawgicPaymentAuthorization authorization = new ClawgicPaymentAuthorization();
        authorization.setPaymentAuthorizationId(UUID.randomUUID());
        authorization.setTournamentId(tournamentId);
        authorization.setAgentId(agentId);
        authorization.setWalletAddress(walletAddress);
        authorization.setRequestNonce(requestNonce);
        authorization.setIdempotencyKey(idempotencyKey);
        authorization.setAuthorizationNonce(authorizationNonce);
        authorization.setStatus(ClawgicPaymentAuthorizationStatus.AUTHORIZED);
        authorization.setAmountAuthorizedUsdc(new BigDecimal("5.000000"));
        authorization.setChainId(84532L);
        authorization.setRecipientWalletAddress("0x000000000000000000000000000000000000c13b");
        authorization.setReceivedAt(now);
        authorization.setVerifiedAt(now);
        authorization.setCreatedAt(now);
        authorization.setUpdatedAt(now);
        return authorization;
    }
}
