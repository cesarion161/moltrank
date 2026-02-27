package com.moltrank.clawgic.service;

import com.moltrank.clawgic.model.ClawgicTournamentEntry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClawgicTournamentBracketBuilderTest {

    private final ClawgicTournamentBracketBuilder bracketBuilder = new ClawgicTournamentBracketBuilder();

    @Test
    void buildSeedsByEloAndPairsSemifinalsDeterministically() {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000901");
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-02-27T12:00:00Z");

        UUID topAgent = UUID.fromString("00000000-0000-0000-0000-000000000A01");
        UUID secondAgent = UUID.fromString("00000000-0000-0000-0000-000000000A02");
        UUID thirdAgent = UUID.fromString("00000000-0000-0000-0000-000000000A03");
        UUID fourthAgent = UUID.fromString("00000000-0000-0000-0000-000000000A04");

        List<ClawgicTournamentEntry> entries = List.of(
                entry("00000000-0000-0000-0000-000000000E04", fourthAgent, 980, "2026-02-27T11:04:00Z"),
                entry("00000000-0000-0000-0000-000000000E02", secondAgent, 1205, "2026-02-27T11:02:00Z"),
                entry("00000000-0000-0000-0000-000000000E03", thirdAgent, 1108, "2026-02-27T11:03:00Z"),
                entry("00000000-0000-0000-0000-000000000E01", topAgent, 1337, "2026-02-27T11:01:00Z")
        );

        ClawgicTournamentBracketBuilder.BracketPlan plan = bracketBuilder.build(tournamentId, entries, generatedAt);

        assertEquals(4, plan.seededEntries().size());
        assertEquals(topAgent, plan.seededEntries().get(0).agentId());
        assertEquals(secondAgent, plan.seededEntries().get(1).agentId());
        assertEquals(thirdAgent, plan.seededEntries().get(2).agentId());
        assertEquals(fourthAgent, plan.seededEntries().get(3).agentId());
        assertEquals(1, plan.seededEntries().get(0).seedPosition());
        assertEquals(4, plan.seededEntries().get(3).seedPosition());

        assertEquals(3, plan.matches().size());
        ClawgicTournamentBracketBuilder.PlannedMatch semifinalOne = plan.matches().get(0);
        ClawgicTournamentBracketBuilder.PlannedMatch semifinalTwo = plan.matches().get(1);
        ClawgicTournamentBracketBuilder.PlannedMatch finalMatch = plan.matches().get(2);

        assertEquals(1, semifinalOne.bracketRound());
        assertEquals(1, semifinalOne.bracketPosition());
        assertEquals(topAgent, semifinalOne.agent1Id());
        assertEquals(fourthAgent, semifinalOne.agent2Id());
        assertEquals(finalMatch.matchId(), semifinalOne.nextMatchId());
        assertEquals(1, semifinalOne.nextMatchAgentSlot());

        assertEquals(1, semifinalTwo.bracketRound());
        assertEquals(2, semifinalTwo.bracketPosition());
        assertEquals(secondAgent, semifinalTwo.agent1Id());
        assertEquals(thirdAgent, semifinalTwo.agent2Id());
        assertEquals(finalMatch.matchId(), semifinalTwo.nextMatchId());
        assertEquals(2, semifinalTwo.nextMatchAgentSlot());

        assertEquals(2, finalMatch.bracketRound());
        assertEquals(1, finalMatch.bracketPosition());
        assertNull(finalMatch.agent1Id());
        assertNull(finalMatch.agent2Id());
        assertNull(finalMatch.nextMatchId());
        assertNull(finalMatch.nextMatchAgentSlot());
    }

    @Test
    void buildUsesCreatedAtThenEntryIdAsTieBreakers() {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000902");
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-02-27T12:00:00Z");

        UUID firstByTime = UUID.fromString("00000000-0000-0000-0000-000000000B01");
        UUID secondById = UUID.fromString("00000000-0000-0000-0000-000000000B02");
        UUID thirdById = UUID.fromString("00000000-0000-0000-0000-000000000B03");
        UUID lastByTime = UUID.fromString("00000000-0000-0000-0000-000000000B04");

        List<ClawgicTournamentEntry> entries = List.of(
                entry("00000000-0000-0000-0000-000000000F04", lastByTime, 1000, "2026-02-27T11:02:00Z"),
                entry("00000000-0000-0000-0000-000000000F03", thirdById, 1000, "2026-02-27T11:01:00Z"),
                entry("00000000-0000-0000-0000-000000000F02", secondById, 1000, "2026-02-27T11:01:00Z"),
                entry("00000000-0000-0000-0000-000000000F01", firstByTime, 1000, "2026-02-27T11:00:00Z")
        );

        ClawgicTournamentBracketBuilder.BracketPlan plan = bracketBuilder.build(tournamentId, entries, generatedAt);

        assertEquals(firstByTime, plan.seededEntries().get(0).agentId());
        assertEquals(secondById, plan.seededEntries().get(1).agentId());
        assertEquals(thirdById, plan.seededEntries().get(2).agentId());
        assertEquals(lastByTime, plan.seededEntries().get(3).agentId());

        assertEquals(firstByTime, plan.matches().get(0).agent1Id());
        assertEquals(lastByTime, plan.matches().get(0).agent2Id());
        assertEquals(secondById, plan.matches().get(1).agent1Id());
        assertEquals(thirdById, plan.matches().get(1).agent2Id());
    }

    @Test
    void buildRequiresExactlyFourEntries() {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000903");
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-02-27T12:00:00Z");

        List<ClawgicTournamentEntry> tooFewEntries = List.of(
                entry("00000000-0000-0000-0000-000000000C01", UUID.randomUUID(), 1100, "2026-02-27T11:00:00Z"),
                entry("00000000-0000-0000-0000-000000000C02", UUID.randomUUID(), 1090, "2026-02-27T11:01:00Z"),
                entry("00000000-0000-0000-0000-000000000C03", UUID.randomUUID(), 1080, "2026-02-27T11:02:00Z")
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> bracketBuilder.build(tournamentId, tooFewEntries, generatedAt)
        );

        assertEquals("Deterministic MVP bracket requires exactly 4 confirmed entries", ex.getMessage());
    }

    private static ClawgicTournamentEntry entry(String entryId, UUID agentId, int elo, String createdAt) {
        ClawgicTournamentEntry entry = new ClawgicTournamentEntry();
        entry.setEntryId(UUID.fromString(entryId));
        entry.setTournamentId(UUID.fromString("00000000-0000-0000-0000-000000000999"));
        entry.setAgentId(agentId);
        entry.setSeedSnapshotElo(elo);
        entry.setCreatedAt(OffsetDateTime.parse(createdAt));
        entry.setUpdatedAt(OffsetDateTime.parse(createdAt));
        return entry;
    }
}
