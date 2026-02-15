package com.moltrank.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.p2p.solanaj.rpc.RpcClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Solana devnet connectivity.
 *
 * Run with:
 *   SOLANA_TEST_DEVNET=true ./gradlew test
 *
 * These tests verify end-to-end connectivity to Solana devnet RPC
 * and interaction with the deployed program.
 */
@EnabledIfEnvironmentVariable(named = "SOLANA_TEST_DEVNET", matches = "true")
class SolanaServiceTest {

    private static final String DEVNET_RPC = "https://api.devnet.solana.com";
    private static final String PROGRAM_ID = "ChLerptVUKPq814C4L4DHroiYBGJHHWCTRxbkGCa9non";

    private SolanaService solanaService;

    @BeforeEach
    void setUp() {
        RpcClient rpcClient = new RpcClient(DEVNET_RPC);
        solanaService = new SolanaService(rpcClient, PROGRAM_ID);
    }

    @Test
    void getLatestBlockhash_returnsValidHash() throws Exception {
        String blockhash = solanaService.getLatestBlockhash();
        assertNotNull(blockhash, "Blockhash should not be null");
        assertFalse(blockhash.isBlank(), "Blockhash should not be blank");
    }

    @Test
    void getSlot_returnsPositiveNumber() throws Exception {
        long slot = solanaService.getSlot();
        assertTrue(slot > 0, "Slot should be positive, got: " + slot);
    }

    @Test
    void programExists_returnsTrueForDeployedProgram() {
        boolean exists = solanaService.programExists();
        assertTrue(exists, "Program " + PROGRAM_ID + " should exist on devnet");
    }
}
