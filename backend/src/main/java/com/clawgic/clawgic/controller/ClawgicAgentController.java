package com.clawgic.clawgic.controller;

import com.clawgic.clawgic.dto.ClawgicAgentRequests;
import com.clawgic.clawgic.dto.ClawgicAgentResponses;
import com.clawgic.clawgic.service.ClawgicAgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clawgic/agents")
public class ClawgicAgentController {

    private final ClawgicAgentService clawgicAgentService;

    public ClawgicAgentController(ClawgicAgentService clawgicAgentService) {
        this.clawgicAgentService = clawgicAgentService;
    }

    @PostMapping
    public ResponseEntity<ClawgicAgentResponses.AgentDetail> createAgent(
            @Valid @RequestBody ClawgicAgentRequests.CreateAgentRequest request
    ) {
        ClawgicAgentResponses.AgentDetail createdAgent = clawgicAgentService.createAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAgent);
    }

    @GetMapping
    public ResponseEntity<List<ClawgicAgentResponses.AgentSummary>> listAgents(
            @RequestParam(required = false) String walletAddress
    ) {
        return ResponseEntity.ok(clawgicAgentService.listAgents(walletAddress));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<ClawgicAgentResponses.AgentLeaderboardPage> getLeaderboard(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return ResponseEntity.ok(clawgicAgentService.getLeaderboard(offset, limit));
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<ClawgicAgentResponses.AgentDetail> getAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(clawgicAgentService.getAgent(agentId));
    }
}
