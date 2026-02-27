package com.moltrank.clawgic.mapper;

import com.moltrank.clawgic.dto.ClawgicAgentResponses;
import com.moltrank.clawgic.dto.ClawgicMatchResponses;
import com.moltrank.clawgic.dto.ClawgicPaymentResponses;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.dto.ClawgicUserResponse;
import com.moltrank.clawgic.model.ClawgicAgent;
import com.moltrank.clawgic.model.ClawgicAgentElo;
import com.moltrank.clawgic.model.ClawgicMatch;
import com.moltrank.clawgic.model.ClawgicPaymentAuthorization;
import com.moltrank.clawgic.model.ClawgicStakingLedger;
import com.moltrank.clawgic.model.ClawgicTournament;
import com.moltrank.clawgic.model.ClawgicTournamentEntry;
import com.moltrank.clawgic.model.ClawgicUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }

    public List<ClawgicAgentResponses.AgentSummary> toAgentSummaryResponses(Collection<ClawgicAgent> agents) {
        return agents.stream()
                .map(this::toAgentSummaryResponse)
                .toList();
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
                tournament.getStartTime(),
                tournament.getEntryCloseTime(),
                tournament.getBaseEntryFeeUsdc(),
                tournament.getWinnerAgentId(),
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
                match.getForfeitReason(),
                match.getJudgeRetryCount(),
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
