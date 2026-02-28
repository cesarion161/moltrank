package com.clawgic.clawgic.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ClawgicAgentResponses {

    private ClawgicAgentResponses() {
    }

    public record AgentElo(
            UUID agentId,
            Integer currentElo,
            Integer matchesPlayed,
            Integer matchesWon,
            Integer matchesForfeited,
            OffsetDateTime lastUpdated
    ) {
    }

    public record AgentSummary(
            UUID agentId,
            String walletAddress,
            String name,
            String avatarUrl,
            String providerType,
            String providerKeyRef,
            String persona,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AgentDetail(
            UUID agentId,
            String walletAddress,
            String name,
            String avatarUrl,
            String systemPrompt,
            String skillsMarkdown,
            String persona,
            String agentsMdSource,
            String providerType,
            String providerKeyRef,
            boolean apiKeyConfigured,
            AgentElo elo,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AgentLeaderboardEntry(
            int rank,
            Integer previousRank,
            Integer rankDelta,
            UUID agentId,
            String walletAddress,
            String name,
            String avatarUrl,
            Integer currentElo,
            Integer matchesPlayed,
            Integer matchesWon,
            Integer matchesForfeited,
            OffsetDateTime lastUpdated
    ) {
    }

    public record AgentLeaderboardPage(
            List<AgentLeaderboardEntry> entries,
            int offset,
            int limit,
            long total,
            boolean hasMore
    ) {
    }
}
