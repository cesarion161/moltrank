package com.moltrank.clawgic.controller;

import com.moltrank.clawgic.dto.ClawgicMatchResponses;
import com.moltrank.clawgic.dto.ClawgicTournamentResponses;
import com.moltrank.clawgic.model.ClawgicMatchStatus;
import com.moltrank.clawgic.model.ClawgicTournamentEntryStatus;
import com.moltrank.clawgic.model.ClawgicTournamentStatus;
import com.moltrank.clawgic.service.ClawgicTournamentService;
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
                .andExpect(jsonPath("$[1].tournamentId").value(secondId.toString()));
    }

    @Test
    void enterTournamentReturnsCreatedPayload() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000420");
        UUID agentId = UUID.fromString("00000000-0000-0000-0000-000000000421");
        when(clawgicTournamentService.enterTournament(any(), any()))
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
        when(clawgicTournamentService.enterTournament(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Agent is already entered"));

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournamentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "00000000-0000-0000-0000-000000000421"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void enterTournamentCapacityExceededReturnsConflict() throws Exception {
        UUID tournamentId = UUID.fromString("00000000-0000-0000-0000-000000000423");
        when(clawgicTournamentService.enterTournament(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Tournament entry capacity reached"));

        mockMvc.perform(post("/api/clawgic/tournaments/{tournamentId}/enter", tournamentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "00000000-0000-0000-0000-000000000421"
                                }
                                """))
                .andExpect(status().isConflict());
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
                OffsetDateTime.parse("2026-06-01T14:00:00Z"),
                OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                new BigDecimal("5.000000"),
                null,
                0,
                0,
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
}
