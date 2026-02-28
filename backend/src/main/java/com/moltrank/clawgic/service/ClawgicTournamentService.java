package com.moltrank.clawgic.service;

import com.moltrank.clawgic.config.ClawgicRuntimeProperties;
import com.moltrank.clawgic.config.X402Properties;
import com.moltrank.clawgic.dto.ClawgicMatchResponses;
import com.moltrank.clawgic.dto.ClawgicTournamentRequests;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.mapper.ClawgicResponseMapper;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicAgentElo;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorization;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.moltrank.clawgic.model.ClawgicStakingLedger;
import com.moltrank.clawgic.model.ClawgicStakingLedgerStatus;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentEntry;
import com.moltrank.clawgic.model.ClawgicTournamentEntryStatus;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.model.DebateTranscriptJsonCodec;
import com.moltrank.clawgic.repository.ClawgicAgentEloRepository;
import com.moltrank.clawgic.repository.ClawgicAgentRepository;
import com.moltrank.clawgic.repository.ClawgicMatchRepository;
import com.moltrank.clawgic.repository.ClawgicPaymentAuthorizationRepository;
import com.moltrank.clawgic.repository.ClawgicStakingLedgerRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentEntryRepository;
import com.moltrank.clawgic.repository.ClawgicTournamentRepository;
import com.moltrank.clawgic.web.X402PaymentRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClawgicTournamentService {

    private static final int DEFAULT_ELO = 1000;

    private final ClawgicAgentRepository clawgicAgentRepository;
    private final ClawgicAgentEloRepository clawgicAgentEloRepository;
    private final ClawgicMatchRepository clawgicMatchRepository;
    private final ClawgicTournamentRepository clawgicTournamentRepository;
    private final ClawgicTournamentEntryRepository clawgicTournamentEntryRepository;
    private final ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository;
    private final ClawgicStakingLedgerRepository clawgicStakingLedgerRepository;
    private final ClawgicTournamentBracketBuilder clawgicTournamentBracketBuilder;
    private final ClawgicResponseMapper clawgicResponseMapper;
    private final ClawgicRuntimeProperties clawgicRuntimeProperties;
    private final X402Properties x402Properties;
    private final X402PaymentAuthorizationAttemptService x402PaymentAuthorizationAttemptService;

    public ClawgicTournamentService(
            ClawgicAgentRepository clawgicAgentRepository,
            ClawgicAgentEloRepository clawgicAgentEloRepository,
            ClawgicMatchRepository clawgicMatchRepository,
            ClawgicTournamentRepository clawgicTournamentRepository,
            ClawgicTournamentEntryRepository clawgicTournamentEntryRepository,
            ClawgicPaymentAuthorizationRepository clawgicPaymentAuthorizationRepository,
            ClawgicStakingLedgerRepository clawgicStakingLedgerRepository,
            ClawgicTournamentBracketBuilder clawgicTournamentBracketBuilder,
            ClawgicResponseMapper clawgicResponseMapper,
            ClawgicRuntimeProperties clawgicRuntimeProperties,
            X402Properties x402Properties,
            X402PaymentAuthorizationAttemptService x402PaymentAuthorizationAttemptService
    ) {
        this.clawgicAgentRepository = clawgicAgentRepository;
        this.clawgicAgentEloRepository = clawgicAgentEloRepository;
        this.clawgicMatchRepository = clawgicMatchRepository;
        this.clawgicTournamentRepository = clawgicTournamentRepository;
        this.clawgicTournamentEntryRepository = clawgicTournamentEntryRepository;
        this.clawgicPaymentAuthorizationRepository = clawgicPaymentAuthorizationRepository;
        this.clawgicStakingLedgerRepository = clawgicStakingLedgerRepository;
        this.clawgicTournamentBracketBuilder = clawgicTournamentBracketBuilder;
        this.clawgicResponseMapper = clawgicResponseMapper;
        this.clawgicRuntimeProperties = clawgicRuntimeProperties;
        this.x402Properties = x402Properties;
        this.x402PaymentAuthorizationAttemptService = x402PaymentAuthorizationAttemptService;
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

    @Transactional(noRollbackFor = X402PaymentRequestException.class)
    public ClawgicTournamentResponses.TournamentEntry enterTournament(
            UUID tournamentId,
            ClawgicTournamentRequests.EnterTournamentRequest request
    ) {
        return enterTournament(tournamentId, request, null);
    }

    @Transactional(noRollbackFor = X402PaymentRequestException.class)
    public ClawgicTournamentResponses.TournamentEntry enterTournament(
            UUID tournamentId,
            ClawgicTournamentRequests.EnterTournamentRequest request,
            String paymentHeaderValue
    ) {

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
        ClawgicTournamentEntry existingEntry = clawgicTournamentEntryRepository
                .findByTournamentIdAndAgentId(tournamentId, agentId)
                .orElse(null);
        if (existingEntry != null) {
            if (x402Properties.isEnabled()) {
                return clawgicResponseMapper.toTournamentEntryResponse(existingEntry);
            }
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

        if (x402Properties.isEnabled()) {
            ClawgicPaymentAuthorization authorization =
                    x402PaymentAuthorizationAttemptService.recordPendingVerificationAttempt(
                            tournamentId,
                            agentId,
                            agent.getWalletAddress(),
                            tournament.getBaseEntryFeeUsdc(),
                            paymentHeaderValue
                    );
            ClawgicPaymentAuthorization verifiedAuthorization =
                    x402PaymentAuthorizationAttemptService.verifyAndPersistAuthorizationOutcome(
                            authorization.getPaymentAuthorizationId(),
                            agent.getWalletAddress(),
                            tournament.getBaseEntryFeeUsdc()
                    );

            return createConfirmedEntryWithAuthorization(
                    tournament,
                    agent,
                    seedSnapshotElo,
                    now,
                    verifiedAuthorization,
                    "X402_AUTHORIZED"
            );
        }

        requireDevBypassModeEnabled();

        ClawgicTournamentEntry savedEntry = createTournamentEntry(
                tournament.getTournamentId(),
                agentId,
                agent.getWalletAddress(),
                seedSnapshotElo,
                now
        );

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

        createOrUpdateStakingLedger(
                savedEntry,
                savedAuthorization,
                now,
                scaleUsdc(tournament.getBaseEntryFeeUsdc()),
                "BYPASS_ACCEPTED (x402.enabled=false)"
        );

        return clawgicResponseMapper.toTournamentEntryResponse(savedEntry);
    }

    @Transactional
    public List<ClawgicMatchResponses.MatchSummary> createMvpBracket(UUID tournamentId) {
        ClawgicTournament tournament = clawgicTournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic tournament not found: " + tournamentId
                ));

        if (tournament.getStatus() != ClawgicTournamentStatus.SCHEDULED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament is not ready for bracket generation: " + tournamentId
            );
        }
        if (clawgicMatchRepository.existsByTournamentId(tournamentId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament bracket already exists: " + tournamentId
            );
        }

        int bracketSize = tournament.getBracketSize() != null ? tournament.getBracketSize() : 0;
        if (bracketSize != ClawgicTournamentBracketBuilder.MVP_BRACKET_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament bracket size is unsupported for MVP builder: " + bracketSize
            );
        }

        List<ClawgicTournamentEntry> entries =
                clawgicTournamentEntryRepository.findByTournamentIdOrderByCreatedAtAsc(tournamentId);
        if (entries.size() != bracketSize) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament requires exactly " + bracketSize + " confirmed entries to build a bracket"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        ClawgicTournamentBracketBuilder.BracketPlan plan;
        try {
            plan = clawgicTournamentBracketBuilder.build(tournamentId, entries, now);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }

        applySeedPositions(entries, plan.seededEntries(), now);
        clawgicTournamentEntryRepository.saveAll(entries);

        plan.matches().stream()
                .sorted(Comparator
                        .comparing((ClawgicTournamentBracketBuilder.PlannedMatch match) -> match.nextMatchId() == null ? 0 : 1)
                        .thenComparing(ClawgicTournamentBracketBuilder.PlannedMatch::bracketRound)
                        .thenComparing(ClawgicTournamentBracketBuilder.PlannedMatch::bracketPosition))
                .map(this::toMatchEntity)
                .map(clawgicMatchRepository::save)
                .toList();

        tournament.setStatus(ClawgicTournamentStatus.LOCKED);
        tournament.setUpdatedAt(now);
        clawgicTournamentRepository.save(tournament);

        return clawgicResponseMapper.toMatchSummaryResponses(
                clawgicMatchRepository.findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(tournamentId)
        );
    }

    private void applySeedPositions(
            List<ClawgicTournamentEntry> entries,
            List<ClawgicTournamentBracketBuilder.SeededEntry> seededEntries,
            OffsetDateTime now
    ) {
        Map<UUID, Integer> seedsByEntryId = seededEntries.stream()
                .collect(Collectors.toMap(
                        ClawgicTournamentBracketBuilder.SeededEntry::entryId,
                        ClawgicTournamentBracketBuilder.SeededEntry::seedPosition
                ));
        for (ClawgicTournamentEntry entry : entries) {
            Integer seedPosition = seedsByEntryId.get(entry.getEntryId());
            if (seedPosition == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Missing seed assignment for entry: " + entry.getEntryId()
                );
            }
            entry.setSeedPosition(seedPosition);
            entry.setUpdatedAt(now);
        }
    }

    private ClawgicMatch toMatchEntity(ClawgicTournamentBracketBuilder.PlannedMatch plannedMatch) {
        ClawgicMatch match = new ClawgicMatch();
        match.setMatchId(plannedMatch.matchId());
        match.setTournamentId(plannedMatch.tournamentId());
        match.setAgent1Id(plannedMatch.agent1Id());
        match.setAgent2Id(plannedMatch.agent2Id());
        match.setBracketRound(plannedMatch.bracketRound());
        match.setBracketPosition(plannedMatch.bracketPosition());
        match.setNextMatchId(plannedMatch.nextMatchId());
        match.setNextMatchAgentSlot(plannedMatch.nextMatchAgentSlot());
        match.setStatus(ClawgicMatchStatus.SCHEDULED);
        match.setTranscriptJson(DebateTranscriptJsonCodec.emptyTranscript());
        match.setJudgeRetryCount(0);
        match.setCreatedAt(plannedMatch.createdAt());
        match.setUpdatedAt(plannedMatch.updatedAt());
        return match;
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

    private ClawgicTournamentResponses.TournamentEntry createConfirmedEntryWithAuthorization(
            ClawgicTournament tournament,
            ClawgicAgent agent,
            int seedSnapshotElo,
            OffsetDateTime now,
            ClawgicPaymentAuthorization verifiedAuthorization,
            String settlementNote
    ) {
        if (verifiedAuthorization.getStatus() != ClawgicPaymentAuthorizationStatus.AUTHORIZED) {
            throw X402PaymentRequestException.verificationPending();
        }

        ClawgicTournamentEntry savedEntry = createTournamentEntry(
                tournament.getTournamentId(),
                agent.getAgentId(),
                agent.getWalletAddress(),
                seedSnapshotElo,
                now
        );

        verifiedAuthorization.setEntryId(savedEntry.getEntryId());
        verifiedAuthorization.setUpdatedAt(now);
        ClawgicPaymentAuthorization linkedAuthorization =
                clawgicPaymentAuthorizationRepository.saveAndFlush(verifiedAuthorization);

        createOrUpdateStakingLedger(
                savedEntry,
                linkedAuthorization,
                now,
                linkedAuthorization.getAmountAuthorizedUsdc(),
                settlementNote
        );

        return clawgicResponseMapper.toTournamentEntryResponse(savedEntry);
    }

    private ClawgicTournamentEntry createTournamentEntry(
            UUID tournamentId,
            UUID agentId,
            String walletAddress,
            int seedSnapshotElo,
            OffsetDateTime now
    ) {
        ClawgicTournamentEntry entry = new ClawgicTournamentEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setTournamentId(tournamentId);
        entry.setAgentId(agentId);
        entry.setWalletAddress(walletAddress);
        entry.setStatus(ClawgicTournamentEntryStatus.CONFIRMED);
        entry.setSeedSnapshotElo(seedSnapshotElo);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        return clawgicTournamentEntryRepository.save(entry);
    }

    private void createOrUpdateStakingLedger(
            ClawgicTournamentEntry entry,
            ClawgicPaymentAuthorization authorization,
            OffsetDateTime now,
            BigDecimal amountStaked,
            String settlementNote
    ) {
        ClawgicStakingLedger stakingLedger = clawgicStakingLedgerRepository
                .findByPaymentAuthorizationId(authorization.getPaymentAuthorizationId())
                .orElseGet(ClawgicStakingLedger::new);

        if (stakingLedger.getStakeId() == null) {
            stakingLedger.setStakeId(UUID.randomUUID());
            stakingLedger.setCreatedAt(now);
        }

        stakingLedger.setTournamentId(entry.getTournamentId());
        stakingLedger.setEntryId(entry.getEntryId());
        stakingLedger.setPaymentAuthorizationId(authorization.getPaymentAuthorizationId());
        stakingLedger.setAgentId(entry.getAgentId());
        stakingLedger.setWalletAddress(entry.getWalletAddress());
        stakingLedger.setAmountStaked(scaleUsdc(amountStaked));
        stakingLedger.setJudgeFeeDeducted(scaleUsdc(BigDecimal.ZERO));
        stakingLedger.setSystemRetention(scaleUsdc(BigDecimal.ZERO));
        stakingLedger.setRewardPayout(scaleUsdc(BigDecimal.ZERO));
        stakingLedger.setStatus(ClawgicStakingLedgerStatus.ENTERED);
        stakingLedger.setSettlementNote(settlementNote);
        if (stakingLedger.getAuthorizedAt() == null) {
            stakingLedger.setAuthorizedAt(
                    authorization.getVerifiedAt() != null ? authorization.getVerifiedAt() : now
            );
        }
        if (stakingLedger.getEnteredAt() == null) {
            stakingLedger.setEnteredAt(now);
        }
        stakingLedger.setUpdatedAt(now);
        clawgicStakingLedgerRepository.save(stakingLedger);
    }
}
