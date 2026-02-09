package com.moltrank.controller;

import com.moltrank.model.GoldenSetItem;
import com.moltrank.service.GoldenSetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing golden set items.
 */
@RestController
@RequestMapping("/api/golden-set")
public class GoldenSetController {

    private final GoldenSetService goldenSetService;

    public GoldenSetController(GoldenSetService goldenSetService) {
        this.goldenSetService = goldenSetService;
    }

    /**
     * Get all golden set items.
     *
     * @return List of all golden set items
     */
    @GetMapping
    public ResponseEntity<List<GoldenSetItem>> getAllGoldenSetItems() {
        List<GoldenSetItem> items = goldenSetService.getAllGoldenSetItems();
        return ResponseEntity.ok(items);
    }

    /**
     * Add a new golden set item.
     *
     * @param item The golden set item to add
     * @return The created golden set item
     */
    @PostMapping
    public ResponseEntity<GoldenSetItem> addGoldenSetItem(@RequestBody GoldenSetItem item) {
        GoldenSetItem created = goldenSetService.addGoldenSetItem(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Delete a golden set item.
     *
     * @param id The ID of the golden set item to delete
     * @return No content response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoldenSetItem(@PathVariable Integer id) {
        goldenSetService.deleteGoldenSetItem(id);
        return ResponseEntity.noContent().build();
    }
}
