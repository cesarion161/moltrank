package com.moltrank.clawgic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * SURGE/x402 payment flow settings for Clawgic tournament entry.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "x402")
public class X402Properties {

    /**
     * Enables live HTTP 402 challenge enforcement and signature verification flow.
     */
    private boolean enabled = false;

    /**
     * When true, local/test runs may bypass live x402 verification while preserving
     * the same entry + ledger control flow.
     */
    private boolean devBypassEnabled = true;

    private String network = "base-sepolia";
    private long chainId = 84532L;
    private String settlementAddress = "0x0000000000000000000000000000000000000000";
    private String tokenAddress = "0x0000000000000000000000000000000000000000";
    private BigDecimal defaultEntryFeeUsdc = new BigDecimal("5.00");
    private String paymentHeaderName = "X-PAYMENT";
    private long nonceTtlSeconds = 300L;
}
