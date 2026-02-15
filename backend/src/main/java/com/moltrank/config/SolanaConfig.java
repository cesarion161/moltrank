package com.moltrank.config;

import org.p2p.solanaj.rpc.RpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolanaConfig {

    private static final Logger log = LoggerFactory.getLogger(SolanaConfig.class);

    @Value("${solana.rpc.url}")
    private String rpcUrl;

    @Value("${solana.program-id}")
    private String programId;

    @Bean
    public RpcClient solanaRpcClient() {
        log.info("Initializing Solana RPC client for {}", rpcUrl);
        return new RpcClient(rpcUrl);
    }

    @Bean
    public String solanaProgramId() {
        return programId;
    }
}
