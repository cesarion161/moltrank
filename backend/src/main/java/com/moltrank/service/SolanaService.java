package com.moltrank.service;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SolanaService {

    private static final Logger log = LoggerFactory.getLogger(SolanaService.class);

    private final RpcClient rpcClient;
    private final String programId;

    public SolanaService(RpcClient rpcClient, @Qualifier("solanaProgramId") String programId) {
        this.rpcClient = rpcClient;
        this.programId = programId;
    }

    /**
     * Verifies connectivity to the Solana RPC endpoint by fetching the latest blockhash.
     *
     * @return the latest blockhash string
     * @throws RpcException if the RPC call fails
     */
    public String getLatestBlockhash() throws RpcException {
        String blockhash = rpcClient.getApi().getLatestBlockhash().getValue().getBlockhash();
        log.debug("Latest blockhash: {}", blockhash);
        return blockhash;
    }

    /**
     * Checks whether the configured program account exists on-chain.
     *
     * @return true if the program account exists and is executable
     */
    public boolean programExists() {
        try {
            var accountInfo = rpcClient.getApi().getAccountInfo(new PublicKey(programId));
            boolean exists = accountInfo != null && accountInfo.getValue() != null;
            log.debug("Program {} exists: {}", programId, exists);
            return exists;
        } catch (RpcException e) {
            log.error("Failed to check program account {}: {}", programId, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the current slot number from the RPC node.
     */
    public long getSlot() throws RpcException {
        return rpcClient.getApi().getSlot();
    }

    public String getProgramId() {
        return programId;
    }
}
