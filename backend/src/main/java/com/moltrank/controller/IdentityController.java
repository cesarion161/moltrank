package com.moltrank.controller;

import com.moltrank.model.Identity;
import com.moltrank.repository.IdentityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * REST API for identity linking.
 */
@RestController
@RequestMapping("/api/identity")
public class IdentityController {

    private final IdentityRepository identityRepository;

    public IdentityController(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    /**
     * Link wallet to X account.
     *
     * @param identity The identity linking request
     * @return Created or updated identity
     */
    @PostMapping("/link")
    public ResponseEntity<Identity> linkIdentity(@RequestBody Identity identity) {
        // Check if identity already exists for this wallet
        Identity existing = identityRepository.findByWallet(identity.getWallet())
                .orElse(null);

        if (existing != null) {
            // Update existing identity
            existing.setXAccount(identity.getXAccount());
            existing.setVerified(identity.getVerified() != null ? identity.getVerified() : false);
            existing.setUpdatedAt(OffsetDateTime.now());
            Identity updated = identityRepository.save(existing);
            return ResponseEntity.ok(updated);
        } else {
            // Create new identity
            identity.setCreatedAt(OffsetDateTime.now());
            identity.setUpdatedAt(OffsetDateTime.now());
            if (identity.getVerified() == null) {
                identity.setVerified(false);
            }
            Identity created = identityRepository.save(identity);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }
    }
}
