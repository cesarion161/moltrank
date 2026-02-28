package com.clawgic.clawgic.repository;

import com.clawgic.clawgic.model.ClawgicAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClawgicAgentRepository extends JpaRepository<ClawgicAgent, UUID> {
    List<ClawgicAgent> findByWalletAddress(String walletAddress);
    List<ClawgicAgent> findByWalletAddressOrderByCreatedAtDesc(String walletAddress);

    @Query(value = """
            SELECT
                agent.agent_id AS agentId,
                agent.wallet_address AS walletAddress,
                agent.name AS name,
                agent.avatar_url AS avatarUrl,
                COALESCE(elo.current_elo, 1000) AS currentElo,
                COALESCE(elo.matches_played, 0) AS matchesPlayed,
                COALESCE(elo.matches_won, 0) AS matchesWon,
                COALESCE(elo.matches_forfeited, 0) AS matchesForfeited,
                elo.last_updated AS lastUpdated,
                agent.created_at AS createdAt
            FROM clawgic_agents agent
            LEFT JOIN clawgic_agent_elo elo ON elo.agent_id = agent.agent_id
            ORDER BY
                COALESCE(elo.current_elo, 1000) DESC,
                COALESCE(elo.matches_played, 0) DESC,
                agent.created_at ASC,
                agent.agent_id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ClawgicAgentLeaderboardRow> findLeaderboardRows(
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = "SELECT COUNT(*) FROM clawgic_agents", nativeQuery = true)
    long countLeaderboardAgents();
}
