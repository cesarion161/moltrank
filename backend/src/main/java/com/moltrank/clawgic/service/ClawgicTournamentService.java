package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.clawgic.config.X402Properties;
import com.moltrank.clawgic.dto.ClawgicTournamentRequests;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.mapper.ClawgicResponseMapper;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicAgentElo;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorization;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.moltrank.clawgic.model.ClawgicStakingLedger;
import com.moltrank.clawgic.model.ClawgicStakingLedgerStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentEntry;
import com.moltrank.clawgic.model.ClawgicTournamentEntryStatus;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.repository.ClawgicAgentEloRepository;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicPaymentAuthorizationRepository;
import com.moltrank.clawgic.repository.ClawgicStakingLedgerRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentEntryRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ClawgicTournamentService {

    private static final int DEFAULT_ELO = 1000;

    private final ClawgicAgentRepository clawgicAgentRepository;
    private final ClawgicAgentEloRepository clawgicAgentEloRepository;
    private final ClawgicTournamentRepository clawgicTournamentRepository;
    private final ClawgicTournamentEntryRepository clawgicTournamentEntryRepository;
    private final ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository;
    private final ClawgicStakingLedgerRepository clawgicStakingLedgerRepository;
    private final ClawgicResponseMapper clawgicResponseMapper;
    private final ClawgicRuntimeProperties clawgicRuntimeProperties;
    private final X402Properties x402Properties;

    public ClawgicTournamentService(
            ClawgicAgentRepository clawgicAgentRepository,
            ClawgicAgentEloRepository clawgicAgentEloRepository,
            ClawgicTournamentRepository clawgicTournamentRepository,
            ClawgicTournamentEntryRepository clawgicTournamentEntryRepository,
            ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository,
            ClawgicStakingLedgerRepository clawgicStakingLedgerRepository,
            ClawgicResponseMapper clawgicResponseMapper,
            ClawgicRuntimeProperties clawgicRuntimeProperties,
            X402Properties x402Properties
    ) {
        this.clawgicAgentRepository = clawgicAgentRepository;
        this.clawgicAgentEloRepository = clawgicAgentEloRepository;
        this.clawgicTournamentRepository = clawgicTournamentRepository;
        this.clawgicTournamentEntryRepository = clawgicTournamentEntryRepository;
        this.clawgicPaymentAuthorizationRepository = clawgicPaymentAuthorizationRepository;
        this.clawgicStakingLedgerRepository = clawgicStakingLedgerRepository;
        this.clawgicResponseMapper = clawgicResponseMapper;
        this.clawgicRuntimeProperties = clawgicRuntimeProperties;
        this.x402Properties = x402Properties;
    }

    @Transactional
    public ClawgicTournamentResponses.TournamentDetail createTournament(
            ClawgicTournamentRequests.CreateTournamentRequest request
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startTime = request.startTime();
        OffsetDateTime entryCloseTime = resolveEntryCloseTime(startTime, request.entryCloseTime());

        if (!entryCloseTime.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "entryCloseTime must be in the future"
            );
        }

        int bracketSize = clawgicRuntimeProperties.getTournament().getMvpBracketSize();
        if (bracketSize <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "clawgic.tournament.mvp-bracket-size must be greater than 1"
            );
        }

        ClawgicTournament tournament = new ClawgicTournament();
        tournament.setTournamentId(UUID.randomUUID());
        tournament.setTopic(request.topic().trim());
        tournament.setStatus(ClawgicTournamentStatus.SCHEDULED);
        tournament.setBracketSize(bracketSize);
        tournament.setMaxEntries(bracketSize);
        tournament.setStartTime(startTime);
        tournament.setEntryCloseTime(entryCloseTime);
        tournament.setBaseEntryFeeUsdc(request.baseEntryFeeUsdc().setScale(6, RoundingMode.HALF_UP));
        tournament.setCreatedAt(now);
        tournament.setUpdatedAt(now);

        ClawgicTournament savedTournament = clawgicTournamentRepository.save(tournament);
        return clawgicResponseMapper.toTournamentDetailResponse(savedTournament);
    }

    @Transactional(readOnly = true)
    public List<ClawgicTournamentResponses.TournamentSummary> listUpcomingTournaments() {
        List<ClawgicTournament> upcomingTournaments =
                clawgicTournamentRepository.findByStatusAndStartTimeAfterOrderByStartTimeAsc(
                        ClawgicTournamentStatus.SCHEDULED,
                        OffsetDateTime.now()
                );
        return clawgicResponseMapper.toTournamentSummaryResponses(upcomingTournaments);
    }

    @Transactional
    public ClawgicTournamentResponses.TournamentEntry enterTournament(
            UUID tournamentId,
            ClawgicTournamentRequests.EnterTournamentRequest request
    ) {
        requireDevBypassModeEnabled();

        ClawgicTournament tournament = clawgicTournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic tournament not found: " + tournamentId
                ));

        OffsetDateTime now = OffsetDateTime.now();
        if (tournament.getStatus() != ClawgicTournamentStatus.SCHEDULED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament is not open for entries: " + tournamentId
            );
        }
        if (!now.isBefore(tournament.getEntryCloseTime())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament entry window is closed: " + tournamentId
            );
        }

        UUID agentId = request.agentId();
        if (clawgicTournamentEntryRepository.existsByTournamentIdAndAgentId(tournamentId, agentId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Agent is already entered in tournament: " + tournamentId
            );
        }

        List<ClawgicTournamentEntry> currentEntries =
                clawgicTournamentEntryRepository.findByTournamentIdOrderByCreatedAtAsc(tournamentId);
        if (currentEntries.size() >= tournament.getMaxEntries()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament entry capacity reached: " + tournamentId
            );
        }

        ClawgicAgent agent = clawgicAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic agent not found: " + agentId
                ));

        int seedSnapshotElo = clawgicAgentEloRepository.findById(agentId)
                .map(ClawgicAgentElo::getCurrentElo)
                .orElse(DEFAULT_ELO);

        ClawgicTournamentEntry entry = new ClawgicTournamentEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setTournamentId(tournamentId);
        entry.setAgentId(agentId);
        entry.setWalletAddress(agent.getWalletAddress());
        entry.setStatus(ClawgicTournamentEntryStatus.CONFIRMED);
        entry.setSeedSnapshotElo(seedSnapshotElo);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        ClawgicTournamentEntry savedEntry = clawgicTournamentEntryRepository.save(entry);

        ClawgicPaymentAuthorization paymentAuthorization = new ClawgicPaymentAuthorization();
        paymentAuthorization.setPaymentAuthorizationId(UUID.randomUUID());
        paymentAuthorization.setTournamentId(tournamentId);
        paymentAuthorization.setEntryId(savedEntry.getEntryId());
        paymentAuthorization.setAgentId(agentId);
        paymentAuthorization.setWalletAddress(agent.getWalletAddress());
        paymentAuthorization.setRequestNonce("dev-bypass-" + savedEntry.getEntryId());
        paymentAuthorization.setIdempotencyKey("dev-bypass-" + savedEntry.getEntryId());
        paymentAuthorization.setStatus(ClawgicPaymentAuthorizationStatus.BYPASSED);
        paymentAuthorization.setAmountAuthorizedUsdc(scaleUsdc(tournament.getBaseEntryFeeUsdc()));
        paymentAuthorization.setChainId(x402Properties.getChainId());
        paymentAuthorization.setRecipientWalletAddress(x402Properties.getSettlementAddress());
        paymentAuthorization.setReceivedAt(now);
        paymentAuthorization.setVerifiedAt(now);
        paymentAuthorization.setCreatedAt(now);
        paymentAuthorization.setUpdatedAt(now);
        ClawgicPaymentAuthorization savedAuthorization =
                clawgicPaymentAuthorizationRepository.save(paymentAuthorization);

        ClawgicStakingLedger stakingLedger = new ClawgicStakingLedger();
        stakingLedger.setStakeId(UUID.randomUUID());
        stakingLedger.setTournamentId(tournamentId);
        stakingLedger.setEntryId(savedEntry.getEntryId());
        stakingLedger.setPaymentAuthorizationId(savedAuthorization.getPaymentAuthorizationId());
        stakingLedger.setAgentId(agentId);
        stakingLedger.setWalletAddress(agent.getWalletAddress());
        stakingLedger.setAmountStaked(scaleUsdc(tournament.getBaseEntryFeeUsdc()));
        stakingLedger.setJudgeFeeDeducted(scaleUsdc(BigDecimal.ZERO));
        stakingLedger.setSystemRetention(scaleUsdc(BigDecimal.ZERO));
        stakingLedger.setRewardPayout(scaleUsdc(BigDecimal.ZERO));
        stakingLedger.setStatus(ClawgicStakingLedgerStatus.ENTERED);
        stakingLedger.setSettlementNote("BYPASS_ACCEPTED (x402.enabled=false)");
        stakingLedger.setAuthorizedAt(now);
        stakingLedger.setEnteredAt(now);
        stakingLedger.setCreatedAt(now);
        stakingLedger.setUpdatedAt(now);
        clawgicStakingLedgerRepository.save(stakingLedger);

        return clawgicResponseMapper.toTournamentEntryResponse(savedEntry);
    }

    private OffsetDateTime resolveEntryCloseTime(OffsetDateTime startTime, OffsetDateTime requestedEntryCloseTime) {
        OffsetDateTime entryCloseTime = requestedEntryCloseTime;
        if (entryCloseTime == null) {
            int defaultEntryWindowMinutes = clawgicRuntimeProperties.getTournament().getDefaultEntryWindowMinutes();
            if (defaultEntryWindowMinutes < 0) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "clawgic.tournament.default-entry-window-minutes must be non-negative"
                );
            }
            entryCloseTime = startTime.minusMinutes(defaultEntryWindowMinutes);
        }

        if (entryCloseTime.isAfter(startTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "entryCloseTime must be on or before startTime"
            );
        }
        return entryCloseTime;
    }

    private void requireDevBypassModeEnabled() {
        if (x402Properties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "x402 payment authorization is required when x402.enabled=true"
            );
        }
        if (!x402Properties.isDevBypassEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Tournament entry bypass is disabled (set x402.dev-bypass-enabled=true for local mode)"
            );
        }
    }

    private BigDecimal scaleUsdc(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
