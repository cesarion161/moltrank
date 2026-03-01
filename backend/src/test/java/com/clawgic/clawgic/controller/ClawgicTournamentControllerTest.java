package com.clawgic.clawgic.controller;

import com.clawgic.clawgic.dto.ClawgicMatchResponses;
import com.clawgic.clawgic.dto.ClawgicTournamentResponses;
import com.clawgic.clawgic.model.ClawgicMatchJudgementStatus;
import com.clawgic.clawgic.model.ClawgicMatchStatus;
import com.clawgic.clawgic.model.ClawgicTournamentEntryStatus;
import com.clawgic.clawgic.model.ClawgicTournamentStatus;
import com.clawgic.clawgic.model.DebatePhase;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.clawgic.clawgic.service.ClawgicTournamentService;
import com.clawgic.clawgic.web.TournamentEntryConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClawgicTournamentController.class)
class ClawgicTournamentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClawgicTournamentService clawgicTournamentService;

    @Test
    void createTournamentReturnsCreatedPayload() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        when(clawgicTournamentService.createTournament(any())).thenReturn(sampleDetail(tournamentId));

        mockMvc.perform(post("/api/clawgic/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Should judges reveal chain-of-thought?",
                                  "startTime": "2026-06-01T14:00:00Z",
                                  "entryCloseTime": "2026-06-01T13:00:00Z",
                                  "baseEntryFeeUsdc": 5.25
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tournamentId").value(tournamentId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.bracketSize").value(4))
                .andExpect(jsonPath("$.baseEntryFeeUsdc").value(5.25));
    }

    @Test
    void createTournamentValidationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/clawgic/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "",
                                  "startTime": "2024-01-01T00:00:00Z",
                                  "baseEntryFeeUsdc": -1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(clawgicTournamentService, never()).createTournament(any());
    }

    @Test
    void createTournamentRejectsEntryCloseAfterStartTime() throws Exception {
        mockMvc.perform(post("/api/clawgic/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Can agents self-correct hallucinations?",
                                  "startTime": "2026-06-01T14:00:00Z",
                                  "entryCloseTime": "2026-06-01T14:30:00Z",
                                  "baseEntryFeeUsdc": 5.00
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(clawgicTournamentService, never()).createTournament(any());
    }

    @Test
    void listUpcomingTournamentsReturnsSummaries() throws Exception {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000411");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000412");
        when(clawgicTournamentService.listUpcomingTournaments())
                .thenReturn(List.of(sampleSummary(firstId, "Debate One"), sampleSummary(secondId, "Debate Two")));

        mockMvc.perform(get("/api/clawgic/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tournamentId").value(firstId.toString()))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].currentEntries").value(0))
                .andExpect(jsonPath("$[0].canEnter").value(true))
                .andExpect(jsonPath("$[0].entryState").value("OPEN"))
                .andExpect(jsonPath("$[0].entryStateReason").value("Accepting entries (0/4)"))
                .andExpect(jsonPath("$[1].tournamentId").value(secondId.toString()));
    }

    @Test
    void listTournamentsForResultsReturnsSummaries() throws Exception {
        UUID completedId = UUID.fromString("00000000-0000-0000-0000-000000000413");
        when(clawgicTournamentService.listTournamentsForResults())
                .thenReturn(List.of(sampleSummary(completedId, "Completed Debate")));

        mockMvc.perform(get("/api/clawgic/tournaments/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tournamentId").value(completedId.toString()))
                .andExpect(jsonPath("$[0].topic").value("Completed Debate"));
    }

    @Test
    void getTournamentResultsReturnsTournamentMatchesAndJudgements() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000414");
        UUID matchId = UUID.fromString("00000000-0000-0000-0000-000000000415");
        when(clawgicTournamentService.getTournamentResults(tournamentId))
                .thenReturn(sampleTournamentResults(tournamentId, matchId));

        mockMvc.perform(get("/api/clawgic/tournaments/{tournamentId}/results", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tournament.tournamentId").value(tournamentId.toString()))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].matchId").value(matchId.toString()))
                .andExpect(jsonPath("$.matches[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.matches[0].agent1EloBefore").value(1000))
                .andExpect(jsonPath("$.matches[0].agent1EloAfter").value(1016))
                .andExpect(jsonPath("$.matches[0].judgements.length()").value(1))
                .andExpect(jsonPath("$.matches[0].judgements[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.matches[0].judgements[0].resultJson").isMap())
                .andExpect(jsonPath("$.settlement").isArray());
    }

    @Test
    void enterTournamentReturnsCreatedPayload() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000420");
        UUID agentId = UUID.fromString("00000000-0000-0000-0000-000000000421");
        when(clawgicTournamentService.enterTournament(any(), any(), any()))
                .thenReturn(sampleEntry(tournamentId, agentId));

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournamentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "00000000-0000-0000-0000-000000000421"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tournamentId").value(tournamentId.toString()))
                .andExpect(jsonPath("$.agentId").value(agentId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.seedSnapshotElo").value(1000));
    }

    @Test
    void enterTournamentDuplicateEntryReturnsConflict() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000422");
        when(clawgicTournamentService.enterTournament(any(), any(), any()))
                .thenThrow(TournamentEntryConflictException.alreadyEntered("Agent is already entered in this tournament"));

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournamentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "00000000-0000-0000-0000-000000000421"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_entered"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void enterTournamentCapacityExceededReturnsConflict() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000423");
        when(clawgicTournamentService.enterTournament(any(), any(), any()))
                .thenThrow(TournamentEntryConflictException.capacityReached("Tournament is full (4/4)"));

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournamentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "00000000-0000-0000-0000-000000000421"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("capacity_reached"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createMvpBracketReturnsCreatedPayload() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000430");
        UUID semifinalOneId = UUID.fromString("00000000-0000-0000-0000-000000000431");
        UUID semifinalTwoId = UUID.fromString("00000000-0000-0000-0000-000000000432");
        UUID finalId = UUID.fromString("00000000-0000-0000-0000-000000000433");
        when(clawgicTournamentService.createMvpBracket(tournamentId))
                .thenReturn(List.of(
                        sampleMatchSummary(semifinalOneId, finalId, 1, 1),
                        sampleMatchSummary(semifinalTwoId, finalId, 1, 2),
                        sampleMatchSummary(finalId, null, 2, 1)
                ));

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/bracket", tournamentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].matchId").value(semifinalOneId.toString()))
                .andExpect(jsonPath("$[0].bracketRound").value(1))
                .andExpect(jsonPath("$[0].nextMatchId").value(finalId.toString()))
                .andExpect(jsonPath("$[2].matchId").value(finalId.toString()))
                .andExpect(jsonPath("$[2].bracketRound").value(2));
    }

    @Test
    void createMvpBracketConflictReturnsConflict() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000434");
        when(clawgicTournamentService.createMvpBracket(tournamentId))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Tournament bracket already exists"));

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/bracket", tournamentId))
                .andExpect(status().isConflict());
    }

    private static ClawgicTournamentResponses.TournamentDetail sampleDetail(UUID tournamentId) {
        OffsetDateTime created = OffsetDateTime.parse("2026-05-01T12:00:00Z");
        return new ClawgicTournamentResponses.TournamentDetail(
                tournamentId,
                "Should judges reveal chain-of-thought?",
                ClawgicTournamentStatus.SCHEDULED,
                4,
                4,
                OffsetDateTime.parse("2026-06-01T14:00:00Z"),
                OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                new BigDecimal("5.250000"),
                null,
                0,
                0,
                created,
                created,
                null,
                null
        );
    }

    private static ClawgicTournamentResponses.TournamentSummary sampleSummary(UUID tournamentId, String topic) {
        OffsetDateTime created = OffsetDateTime.parse("2026-05-01T12:00:00Z");
        return new ClawgicTournamentResponses.TournamentSummary(
                tournamentId,
                topic,
                ClawgicTournamentStatus.SCHEDULED,
                4,
                4,
                0,
                OffsetDateTime.parse("2026-06-01T14:00:00Z"),
                OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                new BigDecimal("5.000000"),
                null,
                0,
                0,
                true,
                com.clawgic.clawgic.model.TournamentEntryState.OPEN,
                "Accepting entries (0/4)",
                created,
                created
        );
    }

    private static ClawgicTournamentResponses.TournamentEntry sampleEntry(UUID tournamentId, UUID agentId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-02T12:00:00Z");
        return new ClawgicTournamentResponses.TournamentEntry(
                UUID.fromString("00000000-0000-0000-0000-000000000424"),
                tournamentId,
                agentId,
                "0x1111111111111111111111111111111111111111",
                ClawgicTournamentEntryStatus.CONFIRMED,
                null,
                1000,
                now,
                now
        );
    }

    private static ClawgicMatchResponses.MatchSummary sampleMatchSummary(
            UUID matchId,
            UUID nextMatchId,
            int round,
            int position
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-02T12:00:00Z");
        return new ClawgicMatchResponses.MatchSummary(
                matchId,
                UUID.fromString("00000000-0000-0000-0000-000000000430"),
                round == 2 ? null : UUID.fromString("00000000-0000-0000-0000-000000000500"),
                round == 2 ? null : UUID.fromString("00000000-0000-0000-0000-000000000501"),
                round,
                position,
                nextMatchId,
                nextMatchId == null ? null : position,
                ClawgicMatchStatus.SCHEDULED,
                null,
                null,
                now,
                now
        );
    }

    private static ClawgicTournamentResponses.TournamentResults sampleTournamentResults(
            UUID tournamentId,
            UUID matchId
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-02T12:00:00Z");
        UUID agentOne = UUID.fromString("00000000-0000-0000-0000-000000000510");
        UUID agentTwo = UUID.fromString("00000000-0000-0000-0000-000000000511");
        ClawgicMatchResponses.MatchJudgementSummary judgementSummary = new ClawgicMatchResponses.MatchJudgementSummary(
                UUID.fromString("00000000-0000-0000-0000-000000000516"),
                matchId,
                "mock-judge-primary",
                "mock-gpt4o",
                ClawgicMatchJudgementStatus.ACCEPTED,
                1,
                JsonNodeFactory.instance.objectNode().put("winner_id", agentOne.toString()),
                agentOne,
                9,
                8,
                9,
                8,
                7,
                8,
                "Agent one was more consistent in rebuttal depth.",
                now,
                now,
                now
        );
        ClawgicMatchResponses.MatchDetail matchDetail = new ClawgicMatchResponses.MatchDetail(
                matchId,
                tournamentId,
                agentOne,
                agentTwo,
                1,
                1,
                null,
                null,
                ClawgicMatchStatus.COMPLETED,
                DebatePhase.CONCLUSION,
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.objectNode().put("winner_id", agentOne.toString()),
                agentOne,
                1000,
                1016,
                1000,
                984,
                null,
                0,
                List.of(judgementSummary),
                now,
                now,
                now,
                now,
                now,
                null,
                now,
                now,
                now
        );

        return new ClawgicTournamentResponses.TournamentResults(
                sampleDetail(tournamentId),
                List.of(sampleEntry(tournamentId, agentOne), sampleEntry(tournamentId, agentTwo)),
                List.of(matchDetail),
                List.of()
        );
    }
}
