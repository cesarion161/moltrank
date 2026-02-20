package com.moltrank.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SkipPairRequest(
        @JsonAlias("curatorWallet") String wallet
) {
    public boolean isValid() {
        return wallet != null && !wallet.isBlank();
    }
}
