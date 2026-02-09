package com.moltrank.controller;

import com.moltrank.model.Curator;
import com.moltrank.model.CuratorId;
import com.moltrank.repository.CuratorRepository;
import com.moltrank.service.CuratorScoringService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for curator profiles and leaderboard.
 */
@RestController
@RequestMapping("/api")
public class CuratorController {

    private final CuratorRepository curatorRepository;
    private final CuratorScoringService curatorScoringService;

    public CuratorController(CuratorRepository curatorRepository,
                             CuratorScoringService curatorScoringService) {
        this.curatorRepository = curatorRepository;
        this.curatorScoringService = curatorScoringService;
    }

    /**
     * Get curator profile with CuratorScore.
     *
     * @param wallet The curator's wallet address
     * @param marketId Optional market ID (defaults to 1)
     * @return Curator profile including score, calibration rate, etc.
     */
    @GetMapping("/curators/{wallet}")
    public ResponseEntity<Curator> getCuratorProfile(
            @PathVariable String wallet,
            @RequestParam(defaultValue = "1") Integer marketId) {

        CuratorId id = new CuratorId(wallet, marketId);

        Curator curator = curatorRepository.findById(id)
                .orElse(null);

        if (curator == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(curator);
    }

    /**
     * Get leaderboard ranked by CuratorScore.
     *
     * @param marketId Optional market ID (defaults to 1)
     * @param limit Optional result limit (defaults to 100)
     * @return List of curators ranked by curator score (descending)
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<Curator>> getLeaderboard(
            @RequestParam(defaultValue = "1") Integer marketId,
            @RequestParam(defaultValue = "100") Integer limit) {

        PageRequest pageRequest = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.DESC, "curatorScore"));

        List<Curator> curators = curatorRepository.findByMarketId(marketId, pageRequest);

        return ResponseEntity.ok(curators);
    }
}
