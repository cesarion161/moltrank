package com.clawgic.clawgic.repository;

import java.time.Instant;
import java.util.UUID;

public interface ClawgicAgentLeaderboardRow {

    UUID getAgentId();

    String getWalletAddress();

    String getName();

    String getAvatarUrl();

    Integer getCurrentElo();

    Integer getMatchesPlayed();

    Integer getMatchesWon();

    Integer getMatchesForfeited();

    Instant getLastUpdated();

    Instant getCreatedAt();
}
