package com.clawgic.clawgic.dto;

import com.clawgic.clawgic.model.ClawgicProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ClawgicAgentRequests {

    private ClawgicAgentRequests() {
    }

    public record CreateAgentRequest(
            @NotBlank(message = "walletAddress is required")
            @Pattern(
                    regexp = "^0x[a-fA-F0-9]{40}$",
                    message = "walletAddress must be a valid 0x-prefixed EVM address"
            )
            String walletAddress,

            @NotBlank(message = "name is required")
            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @Size(max = 2048, message = "avatarUrl must be at most 2048 characters")
            String avatarUrl,

            @NotBlank(message = "systemPrompt is required")
            String systemPrompt,

            String skillsMarkdown,
            String persona,

            @Size(max = 50000, message = "agentsMdSource must be at most 50000 characters")
            String agentsMdSource,

            @NotNull(message = "providerType is required")
            ClawgicProviderType providerType,

            @Size(max = 255, message = "providerKeyRef must be at most 255 characters")
            String providerKeyRef,

            @NotBlank(message = "apiKey is required")
            String apiKey
    ) {
    }
}
