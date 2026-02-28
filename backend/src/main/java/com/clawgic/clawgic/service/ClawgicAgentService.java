package com.clawgic.clawgic.service;

import com.clawgic.clawgic.dto.ClawgicAgentRequests;
import com.clawgic.clawgic.dto.ClawgicAgentResponses;
import com.clawgic.clawgic.mapper.ClawgicResponseMapper;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicUser;
import com.clawgic.clawgic.repository.ClawgicAgentLeaderboardRow;
import com.clawgic.clawgic.repository.ClawgicAgentEloRepository;
import com.clawgic.clawgic.repository.ClawgicAgentRepository;
import com.clawgic.clawgic.repository.ClawgicUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class ClawgicAgentService {

    private static final Pattern WALLET_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final int MAX_LEADERBOARD_LIMIT = 100;

    private final ClawgicAgentRepository clawgicAgentRepository;
    private final ClawgicAgentEloRepository clawgicAgentEloRepository;
    private final ClawgicUserRepository clawgicUserRepository;
    private final ClawgicAgentApiKeyCryptoService clawgicAgentApiKeyCryptoService;
    private final ClawgicResponseMapper clawgicResponseMapper;

    public ClawgicAgentService(
            ClawgicAgentRepository clawgicAgentRepository,
            ClawgicAgentEloRepository clawgicAgentEloRepository,
            ClawgicUserRepository clawgicUserRepository,
            ClawgicAgentApiKeyCryptoService clawgicAgentApiKeyCryptoService,
            ClawgicResponseMapper clawgicResponseMapper
    ) {
        this.clawgicAgentRepository = clawgicAgentRepository;
        this.clawgicAgentEloRepository = clawgicAgentEloRepository;
        this.clawgicUserRepository = clawgicUserRepository;
        this.clawgicAgentApiKeyCryptoService = clawgicAgentApiKeyCryptoService;
        this.clawgicResponseMapper = clawgicResponseMapper;
    }

    @Transactional
    public ClawgicAgentResponses.AgentDetail createAgent(ClawgicAgentRequests.CreateAgentRequest request) {
        String walletAddress = normalizeWalletAddress(request.walletAddress());
        validateAvatarUrl(request.avatarUrl());
        upsertUser(walletAddress);

        ClawgicAgent agent = new ClawgicAgent();
        agent.setAgentId(UUID.randomUUID());
        agent.setWalletAddress(walletAddress);
        agent.setName(request.name().trim());
        agent.setAvatarUrl(normalizeOptional(request.avatarUrl()));
        agent.setSystemPrompt(request.systemPrompt().trim());
        agent.setSkillsMarkdown(normalizeOptional(request.skillsMarkdown()));
        agent.setPersona(normalizeOptional(request.persona()));
        agent.setAgentsMdSource(normalizeOptional(request.agentsMdSource()));
        agent.setProviderType(request.providerType());
        agent.setProviderKeyRef(normalizeOptional(request.providerKeyRef()));
        agent.setCreatedAt(OffsetDateTime.now());
        agent.setUpdatedAt(OffsetDateTime.now());

        try {
            clawgicAgentApiKeyCryptoService.applyEncryptedApiKey(agent, request.apiKey());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        ClawgicAgent savedAgent = clawgicAgentRepository.save(agent);

        ClawgicAgentElo elo = new ClawgicAgentElo();
        elo.setAgentId(savedAgent.getAgentId());
        elo.setCurrentElo(1000);
        elo.setMatchesPlayed(0);
        elo.setMatchesWon(0);
        elo.setMatchesForfeited(0);
        elo.setLastUpdated(OffsetDateTime.now());
        ClawgicAgentElo savedElo = clawgicAgentEloRepository.save(elo);

        return clawgicResponseMapper.toAgentDetailResponse(savedAgent, savedElo);
    }

    @Transactional(readOnly = true)
    public ClawgicAgentResponses.AgentDetail getAgent(UUID agentId) {
        ClawgicAgent agent = clawgicAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic agent not found: " + agentId
                ));
        ClawgicAgentElo elo = clawgicAgentEloRepository.findById(agentId).orElse(null);
        return clawgicResponseMapper.toAgentDetailResponse(agent, elo);
    }

    @Transactional(readOnly = true)
    public List<ClawgicAgentResponses.AgentSummary> listAgents(String walletAddress) {
        List<ClawgicAgent> agents;
        if (StringUtils.hasText(walletAddress)) {
            String normalizedWalletAddress = normalizeWalletAddress(walletAddress);
            agents = clawgicAgentRepository.findByWalletAddressOrderByCreatedAtDesc(normalizedWalletAddress);
        } else {
            agents = clawgicAgentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return clawgicResponseMapper.toAgentSummaryResponses(agents);
    }

    @Transactional(readOnly = true)
    public ClawgicAgentResponses.AgentLeaderboardPage getLeaderboard(int offset, int limit) {
        int normalizedOffset = normalizeLeaderboardOffset(offset);
        int normalizedLimit = normalizeLeaderboardLimit(limit);

        List<ClawgicAgentLeaderboardRow> rows =
                clawgicAgentRepository.findLeaderboardRows(normalizedLimit, normalizedOffset);
        long total = clawgicAgentRepository.countLeaderboardAgents();
        List<ClawgicAgentResponses.AgentLeaderboardEntry> entries = IntStream.range(0, rows.size())
                .mapToObj(index -> clawgicResponseMapper.toAgentLeaderboardEntry(
                        rows.get(index),
                        normalizedOffset + index + 1,
                        null
                ))
                .toList();

        boolean hasMore = normalizedOffset + entries.size() < total;
        return new ClawgicAgentResponses.AgentLeaderboardPage(
                entries,
                normalizedOffset,
                normalizedLimit,
                total,
                hasMore
        );
    }

    private void upsertUser(String walletAddress) {
        if (clawgicUserRepository.existsById(walletAddress)) {
            return;
        }
        ClawgicUser user = new ClawgicUser();
        user.setWalletAddress(walletAddress);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        clawgicUserRepository.save(user);
    }

    private String normalizeWalletAddress(String walletAddress) {
        if (!StringUtils.hasText(walletAddress)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "walletAddress is required");
        }
        String normalized = walletAddress.trim();
        if (!WALLET_ADDRESS_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "walletAddress must be a valid 0x-prefixed EVM address"
            );
        }
        return normalized.toLowerCase();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void validateAvatarUrl(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return;
        }
        try {
            URI parsed = new URI(avatarUrl.trim());
            if (!parsed.isAbsolute()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatarUrl must be an absolute URL");
            }
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatarUrl must be a valid URL", ex);
        }
    }

    private int normalizeLeaderboardOffset(int offset) {
        if (offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset must be greater than or equal to 0");
        }
        return offset;
    }

    private int normalizeLeaderboardLimit(int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be greater than 0");
        }
        if (limit > MAX_LEADERBOARD_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be less than or equal to " + MAX_LEADERBOARD_LIMIT
            );
        }
        return limit;
    }
}
