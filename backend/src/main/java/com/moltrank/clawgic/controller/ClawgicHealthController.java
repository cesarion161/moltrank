package com.moltrank.clawgic.controller;

import com.moltrank.clawgic.dto.ClawgicHealthResponse;
import com.moltrank.clawgic.service.ClawgicHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Clawgic endpoint to verify package wiring before domain logic lands.
 */
@RestController
@RequestMapping("/api/clawgic")
public class ClawgicHealthController {

    private final ClawgicHealthService clawgicHealthService;

    public ClawgicHealthController(ClawgicHealthService clawgicHealthService) {
        this.clawgicHealthService = clawgicHealthService;
    }

    @GetMapping("/health")
    public ResponseEntity<ClawgicHealthResponse> health() {
        return ResponseEntity.ok(clawgicHealthService.health());
    }
}
