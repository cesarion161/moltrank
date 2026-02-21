package com.moltrank.controller;

import com.moltrank.controller.dto.CreateSubscriptionRequest;
import com.moltrank.controller.dto.SubscriptionResponse;
import com.moltrank.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for subscriptions.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Create subscription.
     *
     * @param request The subscription request
     * @return Created subscription
     */
    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionResponse> createSubscription(@RequestBody CreateSubscriptionRequest request) {
        try {
            SubscriptionService.SubscriptionCreationResult result =
                    subscriptionService.createSubscription(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(SubscriptionResponse.from(result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
