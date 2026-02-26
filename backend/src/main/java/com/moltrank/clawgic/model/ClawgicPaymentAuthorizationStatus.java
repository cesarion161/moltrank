package com.moltrank.clawgic.model;

public enum ClawgicPaymentAuthorizationStatus {
    CHALLENGED,
    PENDING_VERIFICATION,
    AUTHORIZED,
    BYPASSED,
    REJECTED,
    REPLAY_REJECTED,
    EXPIRED
}
