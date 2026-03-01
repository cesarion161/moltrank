package com.clawgic.clawgic.service;

import com.clawgic.clawgic.config.ClawgicRuntimeProperties;
import com.clawgic.clawgic.config.X402Properties;
import com.clawgic.clawgic.dto.ClawgicMatchResponses;
import com.clawgic.clawgic.dto.ClawgicTournamentRequests;
import com.clawgic.clawgic.dto.ClawgicTournamentResponses;
import com.clawgic.clawgic.mapper.ClawgicResponseMapper;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicMatch;
import com.clawgic.clawgic.model.ClawgicMatchJudgement;
import com.clawgic.clawgic.model.ClawgicMatchStatus;
import com.clawgic.clawgic.model.ClawgicPaymentAuthorization;
import com.clawgic.clawgic.model.ClawgicPaymentAuthorizationStatus;
import com.clawgic.clawgic.model.ClawgicStakingLedger;
import com.clawgic.clawgic.model.ClawgicStakingLedgerStatus;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentEntry;
import com.clawgic.clawgic.model.ClawgicTournamentEntryStatus;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.model.DebateTranscriptJsonCodec;
import com.clawgic.clawgic.model.TournamentEntryState;
import com.clawgic.clawgic.repository.ClawgicAgentEloRepository;
import com.clawgic.clawgic.repository.ClawgicAgentRepository;
import com.clawgic.clawgic.repository.ClawgicMatchJudgementRepository;
import com.clawgic.clawgic.repository.ClawgicMatchRepository;
import com.clawgic.clawgic.repository.ClawgicPaymentAuthorizationRepository;
import com.clawgic.clawgic.repository.ClawgicStakingLedgerRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentEntryRepository;
import com.clawgic.clawgic.repository.ClawgicTournamentRepository;
import com.clawgic.clawgic.web.X402PaymentRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClawgicTournamentService {

    private static final int DEFAULT_ELO = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClawgicTournamentService.class);

    private final ClawgicAgentRepository clawgicAgentRepository;
    private final ClawgicAgentEloRepository clawgicAgentEloRepository;
    private final ClawgicMatchRepository clawgicMatchRepository;
    private final ClawgicMatchJudgementRepository clawgicMatchJudgementRepository;
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
            ClawgicMatchJudgementRepository clawgicMatchJudgementRepository,
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
        this.clawgicMatchJudgementRepository = clawgicMatchJudgementRepository;
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
        OffsetDateTime now = OffsetDateTime.now();
        List<ClawgicTournament> upcomingTournaments =
                clawgicTournamentRepository.findByStatusAndStartTimeAfterOrderByStartTimeAsc(
                        ClawgicTournamentStatus.SCHEDULED,
                        now
                );
        return upcomingTournaments.stream()
                .map(tournament -> {
                    int currentEntries = (int) clawgicTournamentEntryRepository
                            .countByTournamentId(tournament.getTournamentId());
                    return toEligibilityEnrichedSummary(tournament, currentEntries, now);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClawgicTournamentResponses.TournamentSummary> listTournamentsForResults() {
        List<ClawgicTournament> tournaments = clawgicTournamentRepository.findAll(
                Sort.by(
                        Sort.Order.desc("startTime"),
                        Sort.Order.desc("createdAt")
                )
        );
        return clawgicResponseMapper.toTournamentSummaryResponses(
                tournaments.stream()
                        .limit(50)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ClawgicTournamentResponses.TournamentResults getTournamentResults(UUID tournamentId) {
        ClawgicTournament tournament = clawgicTournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic tournament not found: " + tournamentId
                ));

        List<ClawgicTournamentEntry> entries = clawgicTournamentEntryRepository
                .findByTournamentIdOrderByCreatedAtAsc(tournamentId);
        List<ClawgicMatch> matches = clawgicMatchRepository
                .findByTournamentIdOrderByBracketRoundAscBracketPositionAscCreatedAtAsc(tournamentId);
        List<ClawgicMatchJudgement> judgements = clawgicMatchJudgementRepository
                .findByTournamentIdOrderByMatchIdAscAttemptAscCreatedAtAsc(tournamentId);

        Map<UUID, List<ClawgicMatchJudgement>> judgementsByMatch = judgements.stream()
                .collect(Collectors.groupingBy(
                        ClawgicMatchJudgement::getMatchId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ClawgicMatchResponses.MatchDetail> matchDetails = matches.stream()
                .map(match -> clawgicResponseMapper.toMatchDetailResponse(
                        match,
                        judgementsByMatch.getOrDefault(match.getMatchId(), List.of())
                ))
                .toList();

        List<ClawgicTournamentEntry> sortedEntries = entries.stream()
                .sorted(Comparator
                        .comparing(
                                ClawgicTournamentEntry::getSeedPosition,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(ClawgicTournamentEntry::getCreatedAt))
                .toList();

        return new ClawgicTournamentResponses.TournamentResults(
                clawgicResponseMapper.toTournamentDetailResponse(tournament),
                clawgicResponseMapper.toTournamentEntryResponses(sortedEntries),
                matchDetails
        );
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
        UUID agentId = request.agentId();

        ClawgicTournament tournament = clawgicTournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Clawgic tournament not found: " + tournamentId
                ));

        OffsetDateTime now = OffsetDateTime.now();
        if (tournament.getStatus() != ClawgicTournamentStatus.SCHEDULED) {
            logEntryConflict("tournament_not_open", tournamentId, agentId, tournament, now);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament is not open for entries: " + tournamentId
            );
        }
        if (!now.isBefore(tournament.getEntryCloseTime())) {
            logEntryConflict("entry_window_closed", tournamentId, agentId, tournament, now);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tournament entry window is closed: " + tournamentId
            );
        }

        ClawgicTournamentEntry existingEntry = clawgicTournamentEntryRepository
                .findByTournamentIdAndAgentId(tournamentId, agentId)
                .orElse(null);
        if (existingEntry != null) {
            if (x402Properties.isEnabled()) {
                return clawgicResponseMapper.toTournamentEntryResponse(existingEntry);
            }
            logEntryConflict("already_entered", tournamentId, agentId, tournament, now);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Agent is already entered in tournament: " + tournamentId
            );
        }

        List<ClawgicTournamentEntry> currentEntries =
                clawgicTournamentEntryRepository.findByTournamentIdOrderByCreatedAtAsc(tournamentId);
        if (currentEntries.size() >= tournament.getMaxEntries()) {
            logEntryConflict("capacity_reached", tournamentId, agentId, tournament, now);
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

    private void logEntryConflict(
            String reasonCode,
            UUID tournamentId,
            UUID agentId,
            ClawgicTournament tournament,
            OffsetDateTime now
    ) {
        LOGGER.warn(
                "entry_conflict reason={} tournamentId={} agentId={} status={} entryCloseTime={} maxEntries={} observedAt={}",
                reasonCode,
                tournamentId,
                agentId,
                tournament.getStatus(),
                tournament.getEntryCloseTime(),
                tournament.getMaxEntries(),
                now
        );
    }

    private ClawgicTournamentResponses.TournamentSummary toEligibilityEnrichedSummary(
            ClawgicTournament tournament,
            int currentEntries,
            OffsetDateTime now
    ) {
        TournamentEntryState entryState;
        String entryStateReason;

        if (tournament.getStatus() != ClawgicTournamentStatus.SCHEDULED) {
            entryState = TournamentEntryState.TOURNAMENT_NOT_OPEN;
            entryStateReason = "Tournament is not open for entries (status: " + tournament.getStatus() + ")";
        } else if (!now.isBefore(tournament.getEntryCloseTime())) {
            entryState = TournamentEntryState.ENTRY_WINDOW_CLOSED;
            entryStateReason = "Entry window closed at " + tournament.getEntryCloseTime();
        } else if (currentEntries >= tournament.getMaxEntries()) {
            entryState = TournamentEntryState.CAPACITY_REACHED;
            entryStateReason = "Tournament is full (" + currentEntries + "/" + tournament.getMaxEntries() + ")";
        } else {
            entryState = TournamentEntryState.OPEN;
            entryStateReason = "Accepting entries (" + currentEntries + "/" + tournament.getMaxEntries() + ")";
        }

        boolean canEnter = entryState == TournamentEntryState.OPEN;
        return clawgicResponseMapper.toTournamentSummaryResponse(
                tournament, currentEntries, canEnter, entryState, entryStateReason
        );
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
