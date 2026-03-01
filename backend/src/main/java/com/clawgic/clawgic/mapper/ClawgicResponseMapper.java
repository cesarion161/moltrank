package com.clawgic.clawgic.mapper;

import com.clawgic.clawgic.dto.ClawgicAgentResponses;
import com.clawgic.clawgic.dto.ClawgicMatchResponses;
import com.clawgic.clawgic.dto.ClawgicPaymentResponses;
import com.clawgic.clawgic.dto.ClawgicTournamentResponses;
import com.clawgic.clawgic.dto.ClawgicUserResponse;
import com.clawgic.clawgic.model.ClawgicAgent;
import com.clawgic.clawgic.model.ClawgicAgentElo;
import com.clawgic.clawgic.model.ClawgicMatch;
import com.clawgic.clawgic.model.ClawgicMatchJudgement;
import com.clawgic.clawgic.model.ClawgicPaymentAuthorization;
import com.clawgic.clawgic.model.ClawgicStakingLedger;
import com.clawgic.clawgic.model.ClawgicTournament;
import com.clawgic.clawgic.model.ClawgicTournamentEntry;
import com.clawgic.clawgic.model.ClawgicUser;
import com.clawgic.clawgic.model.TournamentEntryState;
import com.clawgic.clawgic.repository.ClawgicAgentLeaderboardRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

@Component
public class ClawgicResponseMapper {

    public ClawgicUserResponse toUserResponse(ClawgicUser user) {
        return new ClawgicUserResponse(
                user.getWalletAddress(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public ClawgicAgentResponses.AgentElo toAgentEloResponse(ClawgicAgentElo elo) {
        if (elo == null) {
            return null;
        }
        return new ClawgicAgentResponses.AgentElo(
                elo.getAgentId(),
                elo.getCurrentElo(),
                elo.getMatchesPlayed(),
                elo.getMatchesWon(),
                elo.getMatchesForfeited(),
                elo.getLastUpdated()
        );
    }

    public ClawgicAgentResponses.AgentSummary toAgentSummaryResponse(ClawgicAgent agent) {
        return new ClawgicAgentResponses.AgentSummary(
                agent.getAgentId(),
                agent.getWalletAddress(),
                agent.getName(),
                agent.getAvatarUrl(),
                agent.getProviderType() != null ? agent.getProviderType().name() : null,
                agent.getProviderKeyRef(),
                agent.getPersona(),
                StringUtils.hasText(agent.getApiKeyEncrypted()),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }

    public List<ClawgicAgentResponses.AgentSummary> toAgentSummaryResponses(Collection<ClawgicAgent> agents) {
        return agents.stream()
                .map(this::toAgentSummaryResponse)
                .toList();
    }

    public ClawgicAgentResponses.AgentLeaderboardEntry toAgentLeaderboardEntry(
            ClawgicAgentLeaderboardRow leaderboardRow,
            int rank,
            Integer previousRank
    ) {
        Integer rankDelta = previousRank == null ? null : previousRank - rank;
        OffsetDateTime lastUpdated = leaderboardRow.getLastUpdated() == null
                ? null
                : leaderboardRow.getLastUpdated().atOffset(ZoneOffset.UTC);
        return new ClawgicAgentResponses.AgentLeaderboardEntry(
                rank,
                previousRank,
                rankDelta,
                leaderboardRow.getAgentId(),
                leaderboardRow.getWalletAddress(),
                leaderboardRow.getName(),
                leaderboardRow.getAvatarUrl(),
                leaderboardRow.getCurrentElo(),
                leaderboardRow.getMatchesPlayed(),
                leaderboardRow.getMatchesWon(),
                leaderboardRow.getMatchesForfeited(),
                lastUpdated
        );
    }

    public ClawgicAgentResponses.AgentDetail toAgentDetailResponse(ClawgicAgent agent) {
        return toAgentDetailResponse(agent, null);
    }

    public ClawgicAgentResponses.AgentDetail toAgentDetailResponse(ClawgicAgent agent, ClawgicAgentElo elo) {
        return new ClawgicAgentResponses.AgentDetail(
                agent.getAgentId(),
                agent.getWalletAddress(),
                agent.getName(),
                agent.getAvatarUrl(),
                agent.getSystemPrompt(),
                agent.getSkillsMarkdown(),
                agent.getPersona(),
                agent.getAgentsMdSource(),
                agent.getProviderType() != null ? agent.getProviderType().name() : null,
                agent.getProviderKeyRef(),
                StringUtils.hasText(agent.getApiKeyEncrypted()),
                toAgentEloResponse(elo),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }

    public ClawgicTournamentResponses.TournamentSummary toTournamentSummaryResponse(ClawgicTournament tournament) {
        return new ClawgicTournamentResponses.TournamentSummary(
                tournament.getTournamentId(),
                tournament.getTopic(),
                tournament.getStatus(),
                tournament.getBracketSize(),
                tournament.getMaxEntries(),
                null,
                tournament.getStartTime(),
                tournament.getEntryCloseTime(),
                tournament.getBaseEntryFeeUsdc(),
                tournament.getWinnerAgentId(),
                tournament.getMatchesCompleted(),
                tournament.getMatchesForfeited(),
                null,
                null,
                null,
                tournament.getCreatedAt(),
                tournament.getUpdatedAt()
        );
    }

    public ClawgicTournamentResponses.TournamentSummary toTournamentSummaryResponse(
            ClawgicTournament tournament,
            int currentEntries,
            boolean canEnter,
            TournamentEntryState entryState,
            String entryStateReason
    ) {
        return new ClawgicTournamentResponses.TournamentSummary(
                tournament.getTournamentId(),
                tournament.getTopic(),
                tournament.getStatus(),
                tournament.getBracketSize(),
                tournament.getMaxEntries(),
                currentEntries,
                tournament.getStartTime(),
                tournament.getEntryCloseTime(),
                tournament.getBaseEntryFeeUsdc(),
                tournament.getWinnerAgentId(),
                tournament.getMatchesCompleted(),
                tournament.getMatchesForfeited(),
                canEnter,
                entryState,
                entryStateReason,
                tournament.getCreatedAt(),
                tournament.getUpdatedAt()
        );
    }

    public List<ClawgicTournamentResponses.TournamentSummary> toTournamentSummaryResponses(
            Collection<ClawgicTournament> tournaments
    ) {
        return tournaments.stream()
                .map(this::toTournamentSummaryResponse)
                .toList();
    }

    public ClawgicTournamentResponses.TournamentDetail toTournamentDetailResponse(ClawgicTournament tournament) {
        return new ClawgicTournamentResponses.TournamentDetail(
                tournament.getTournamentId(),
                tournament.getTopic(),
                tournament.getStatus(),
                tournament.getBracketSize(),
                tournament.getMaxEntries(),
                tournament.getStartTime(),
                tournament.getEntryCloseTime(),
                tournament.getBaseEntryFeeUsdc(),
                tournament.getWinnerAgentId(),
                tournament.getMatchesCompleted(),
                tournament.getMatchesForfeited(),
                tournament.getCreatedAt(),
                tournament.getUpdatedAt(),
                tournament.getStartedAt(),
                tournament.getCompletedAt()
        );
    }

    public ClawgicTournamentResponses.TournamentEntry toTournamentEntryResponse(ClawgicTournamentEntry entry) {
        return new ClawgicTournamentResponses.TournamentEntry(
                entry.getEntryId(),
                entry.getTournamentId(),
                entry.getAgentId(),
                entry.getWalletAddress(),
                entry.getStatus(),
                entry.getSeedPosition(),
                entry.getSeedSnapshotElo(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }

    public List<ClawgicTournamentResponses.TournamentEntry> toTournamentEntryResponses(
            Collection<ClawgicTournamentEntry> entries
    ) {
        return entries.stream()
                .map(this::toTournamentEntryResponse)
                .toList();
    }

    public ClawgicMatchResponses.MatchSummary toMatchSummaryResponse(ClawgicMatch match) {
        return new ClawgicMatchResponses.MatchSummary(
                match.getMatchId(),
                match.getTournamentId(),
                match.getAgent1Id(),
                match.getAgent2Id(),
                match.getBracketRound(),
                match.getBracketPosition(),
                match.getNextMatchId(),
                match.getNextMatchAgentSlot(),
                match.getStatus(),
                match.getPhase(),
                match.getWinnerAgentId(),
                match.getCreatedAt(),
                match.getUpdatedAt()
        );
    }

    public List<ClawgicMatchResponses.MatchSummary> toMatchSummaryResponses(Collection<ClawgicMatch> matches) {
        return matches.stream()
                .map(this::toMatchSummaryResponse)
                .toList();
    }

    public ClawgicMatchResponses.MatchDetail toMatchDetailResponse(ClawgicMatch match) {
        return toMatchDetailResponse(match, List.of());
    }

    public ClawgicMatchResponses.MatchDetail toMatchDetailResponse(
            ClawgicMatch match,
            Collection<ClawgicMatchJudgement> judgements
    ) {
        return new ClawgicMatchResponses.MatchDetail(
                match.getMatchId(),
                match.getTournamentId(),
                match.getAgent1Id(),
                match.getAgent2Id(),
                match.getBracketRound(),
                match.getBracketPosition(),
                match.getNextMatchId(),
                match.getNextMatchAgentSlot(),
                match.getStatus(),
                match.getPhase(),
                match.getTranscriptJson(),
                match.getJudgeResultJson(),
                match.getWinnerAgentId(),
                match.getAgent1EloBefore(),
                match.getAgent1EloAfter(),
                match.getAgent2EloBefore(),
                match.getAgent2EloAfter(),
                match.getForfeitReason(),
                match.getJudgeRetryCount(),
                judgements.stream()
                        .map(this::toMatchJudgementSummaryResponse)
                        .toList(),
                match.getExecutionDeadlineAt(),
                match.getJudgeDeadlineAt(),
                match.getStartedAt(),
                match.getJudgeRequestedAt(),
                match.getJudgedAt(),
                match.getForfeitedAt(),
                match.getCompletedAt(),
                match.getCreatedAt(),
                match.getUpdatedAt()
        );
    }

    public ClawgicMatchResponses.MatchJudgementSummary toMatchJudgementSummaryResponse(
            ClawgicMatchJudgement judgement
    ) {
        return new ClawgicMatchResponses.MatchJudgementSummary(
                judgement.getJudgementId(),
                judgement.getMatchId(),
                judgement.getJudgeKey(),
                judgement.getJudgeModel(),
                judgement.getStatus(),
                judgement.getAttempt(),
                judgement.getResultJson(),
                judgement.getWinnerAgentId(),
                judgement.getAgent1LogicScore(),
                judgement.getAgent1PersonaAdherenceScore(),
                judgement.getAgent1RebuttalStrengthScore(),
                judgement.getAgent2LogicScore(),
                judgement.getAgent2PersonaAdherenceScore(),
                judgement.getAgent2RebuttalStrengthScore(),
                judgement.getReasoning(),
                judgement.getJudgedAt(),
                judgement.getCreatedAt(),
                judgement.getUpdatedAt()
        );
    }

    public ClawgicPaymentResponses.PaymentAuthorizationSummary toPaymentAuthorizationSummaryResponse(
            ClawgicPaymentAuthorization authorization
    ) {
        return new ClawgicPaymentResponses.PaymentAuthorizationSummary(
                authorization.getPaymentAuthorizationId(),
                authorization.getTournamentId(),
                authorization.getEntryId(),
                authorization.getAgentId(),
                authorization.getWalletAddress(),
                authorization.getRequestNonce(),
                authorization.getIdempotencyKey(),
                authorization.getAuthorizationNonce(),
                authorization.getStatus(),
                authorization.getAmountAuthorizedUsdc(),
                authorization.getChainId(),
                authorization.getRecipientWalletAddress(),
                authorization.getChallengeExpiresAt(),
                authorization.getReceivedAt(),
                authorization.getVerifiedAt(),
                authorization.getCreatedAt(),
                authorization.getUpdatedAt()
        );
    }

    public List<ClawgicPaymentResponses.PaymentAuthorizationSummary> toPaymentAuthorizationSummaryResponses(
            Collection<ClawgicPaymentAuthorization> authorizations
    ) {
        return authorizations.stream()
                .map(this::toPaymentAuthorizationSummaryResponse)
                .toList();
    }

    public ClawgicPaymentResponses.PaymentAuthorizationDetail toPaymentAuthorizationDetailResponse(
            ClawgicPaymentAuthorization authorization
    ) {
        return new ClawgicPaymentResponses.PaymentAuthorizationDetail(
                authorization.getPaymentAuthorizationId(),
                authorization.getTournamentId(),
                authorization.getEntryId(),
                authorization.getAgentId(),
                authorization.getWalletAddress(),
                authorization.getRequestNonce(),
                authorization.getIdempotencyKey(),
                authorization.getAuthorizationNonce(),
                authorization.getStatus(),
                authorization.getPaymentHeaderJson(),
                authorization.getAmountAuthorizedUsdc(),
                authorization.getChainId(),
                authorization.getRecipientWalletAddress(),
                authorization.getFailureReason(),
                authorization.getChallengeExpiresAt(),
                authorization.getReceivedAt(),
                authorization.getVerifiedAt(),
                authorization.getCreatedAt(),
                authorization.getUpdatedAt()
        );
    }

    public ClawgicPaymentResponses.StakingLedgerSummary toStakingLedgerSummaryResponse(ClawgicStakingLedger ledger) {
        return new ClawgicPaymentResponses.StakingLedgerSummary(
                ledger.getStakeId(),
                ledger.getTournamentId(),
                ledger.getEntryId(),
                ledger.getPaymentAuthorizationId(),
                ledger.getAgentId(),
                ledger.getWalletAddress(),
                ledger.getAmountStaked(),
                ledger.getJudgeFeeDeducted(),
                ledger.getSystemRetention(),
                ledger.getRewardPayout(),
                ledger.getStatus(),
                ledger.getCreatedAt(),
                ledger.getUpdatedAt()
        );
    }

    public List<ClawgicPaymentResponses.StakingLedgerSummary> toStakingLedgerSummaryResponses(
            Collection<ClawgicStakingLedger> ledgers
    ) {
        return ledgers.stream()
                .map(this::toStakingLedgerSummaryResponse)
                .toList();
    }

    public ClawgicPaymentResponses.StakingLedgerDetail toStakingLedgerDetailResponse(ClawgicStakingLedger ledger) {
        return new ClawgicPaymentResponses.StakingLedgerDetail(
                ledger.getStakeId(),
                ledger.getTournamentId(),
                ledger.getEntryId(),
                ledger.getPaymentAuthorizationId(),
                ledger.getAgentId(),
                ledger.getWalletAddress(),
                ledger.getAmountStaked(),
                ledger.getJudgeFeeDeducted(),
                ledger.getSystemRetention(),
                ledger.getRewardPayout(),
                ledger.getStatus(),
                ledger.getSettlementNote(),
                ledger.getAuthorizedAt(),
                ledger.getEnteredAt(),
                ledger.getLockedAt(),
                ledger.getForfeitedAt(),
                ledger.getSettledAt(),
                ledger.getCreatedAt(),
                ledger.getUpdatedAt()
        );
    }
}
