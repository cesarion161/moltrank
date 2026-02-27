package com.moltrank.clawgic.service;

import com.moltrank.clawgic.model.ClawgicTournamentEntry;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ClawgicTournamentBracketBuilder {

    static final int MVP_BRACKET_SIZE = 4;
    private static final int DEFAULT_ELO = 1000;

    private static final Comparator<ClawgicTournamentEntry> SEED_ORDER_COMPARATOR =
            Comparator.comparingInt(ClawgicTournamentBracketBuilder::resolveSeedElo)
                    .reversed()
                    .thenComparing(ClawgicTournamentEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ClawgicTournamentEntry::getEntryId, Comparator.nullsLast(Comparator.naturalOrder()));

    public BracketPlan build(UUID tournamentId, List<ClawgicTournamentEntry> entries, OffsetDateTime generatedAt) {
        if (entries == null || entries.size() != MVP_BRACKET_SIZE) {
            throw new IllegalArgumentException("Deterministic MVP bracket requires exactly 4 confirmed entries");
        }

        List<ClawgicTournamentEntry> orderedEntries = new ArrayList<>(entries);
        orderedEntries.sort(SEED_ORDER_COMPARATOR);
        validateEntries(orderedEntries);

        List<SeededEntry> seededEntries = new ArrayList<>(MVP_BRACKET_SIZE);
        for (int i = 0; i < orderedEntries.size(); i++) {
            ClawgicTournamentEntry entry = orderedEntries.get(i);
            seededEntries.add(new SeededEntry(
                    entry.getEntryId(),
                    entry.getAgentId(),
                    i + 1,
                    resolveSeedElo(entry)
            ));
        }

        UUID finalMatchId = UUID.randomUUID();
        List<PlannedMatch> matches = List.of(
                new PlannedMatch(
                        UUID.randomUUID(),
                        tournamentId,
                        seededEntries.get(0).agentId(),
                        seededEntries.get(3).agentId(),
                        1,
                        1,
                        finalMatchId,
                        1,
                        generatedAt,
                        generatedAt
                ),
                new PlannedMatch(
                        UUID.randomUUID(),
                        tournamentId,
                        seededEntries.get(1).agentId(),
                        seededEntries.get(2).agentId(),
                        1,
                        2,
                        finalMatchId,
                        2,
                        generatedAt,
                        generatedAt
                ),
                new PlannedMatch(
                        finalMatchId,
                        tournamentId,
                        null,
                        null,
                        2,
                        1,
                        null,
                        null,
                        generatedAt,
                        generatedAt
                )
        );

        return new BracketPlan(List.copyOf(seededEntries), matches);
    }

    private static int resolveSeedElo(ClawgicTournamentEntry entry) {
        Integer snapshot = entry.getSeedSnapshotElo();
        return snapshot != null ? snapshot : DEFAULT_ELO;
    }

    private static void validateEntries(List<ClawgicTournamentEntry> orderedEntries) {
        Set<UUID> entryIds = new HashSet<>();
        Set<UUID> agentIds = new HashSet<>();
        for (ClawgicTournamentEntry entry : orderedEntries) {
            if (entry.getEntryId() == null) {
                throw new IllegalArgumentException("Tournament entry is missing entryId");
            }
            if (entry.getAgentId() == null) {
                throw new IllegalArgumentException("Tournament entry is missing agentId: " + entry.getEntryId());
            }
            if (!entryIds.add(entry.getEntryId())) {
                throw new IllegalArgumentException("Duplicate tournament entry id: " + entry.getEntryId());
            }
            if (!agentIds.add(entry.getAgentId())) {
                throw new IllegalArgumentException("Duplicate agent in tournament entries: " + entry.getAgentId());
            }
        }
    }

    public record BracketPlan(
            List<SeededEntry> seededEntries,
            List<PlannedMatch> matches
    ) {
    }

    public record SeededEntry(
            UUID entryId,
            UUID agentId,
            Integer seedPosition,
            Integer seedSnapshotElo
    ) {
    }

    public record PlannedMatch(
            UUID matchId,
            UUID tournamentId,
            UUID agent1Id,
            UUID agent2Id,
            Integer bracketRound,
            Integer bracketPosition,
            UUID nextMatchId,
            Integer nextMatchAgentSlot,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
