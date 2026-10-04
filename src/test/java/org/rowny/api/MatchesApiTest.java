package org.rowny.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rowny.domain.Match;
import org.rowny.domain.Scoreboard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MatchesApiTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private Scoreboard scoreboard;

    @BeforeEach
    void clearMatches() {
        scoreboard.getSummary().stream().map(Match::id).forEach(scoreboard::finishMatch);
    }

    @Test
    void startsUpdatesSummarizesAndFinishesThroughTheGeneratedApi() throws Exception {
        long id = start("""
                {"homeTeam":"mEXICO","awayTeam":"CANADA"}
                """);

        mvc.perform(patch("/matches/{id}/score", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"awayScore\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam").value("Mexico"))
                .andExpect(jsonPath("$.awayTeam").value("Canada"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(5));

        mvc.perform(get("/matches"))
                .andExpect(status().isOk())
                .andExpect(content().json("[" + matchJson(id, "Mexico", "Canada", 0, 5) + "]", JsonCompareMode.STRICT));

        mvc.perform(delete("/matches/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.homeTeam").value("Mexico"))
                .andExpect(jsonPath("$.awayTeam").value("Canada"))
                .andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(5));

        mvc.perform(get("/matches")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(delete("/matches/{id}", id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(patch("/matches/{id}/score", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));

        start("{\"homeTeam\":\"Canada\",\"awayTeam\":\"Mexico\"}");
    }

    @Test
    void startsWithOnlyOneInitialScore() throws Exception {
        long home = start("{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"homeScore\":2}");
        long away = start("{\"homeTeam\":\"Spain\",\"awayTeam\":\"Brazil\",\"awayScore\":3}");

        mvc.perform(get("/matches"))
                .andExpect(content().json("[" + matchJson(away, "Spain", "Brazil", 0, 3) + ","
                        + matchJson(home, "Mexico", "Canada", 2, 0) + "]", JsonCompareMode.STRICT));
    }

    @Test
    void preservesCanonicalUnicodeTeamNames() throws Exception {
        long id = start("{\"homeTeam\":\"côte d'ivoire\",\"awayTeam\":\"TÜRKİYE\"}");
        mvc.perform(get("/matches"))
                .andExpect(status().isOk())
                .andExpect(content().json("[" + matchJson(id, "Côte d'Ivoire", "Türkiye", 0, 0) + "]",
                        JsonCompareMode.STRICT));
    }

    @Test
    void emptySummaryIsAnEmptyJsonArray() throws Exception {
        mvc.perform(get("/matches"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    void unknownPositiveIdsReturn404() throws Exception {
        mvc.perform(delete("/matches/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(patch("/matches/{id}/score", Long.MAX_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void partialReplacementCanReduceEitherScoreToZero() throws Exception {
        long id = start("{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"homeScore\":2,\"awayScore\":5}");
        mvc.perform(patch("/matches/{id}/score", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(5));
        mvc.perform(patch("/matches/{id}/score", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"awayScore\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.homeScore").value(0))
                .andExpect(jsonPath("$.awayScore").value(0));
    }

    @Test
    void summaryUsesStableIdsAndNewestStartBreaksTotalScoreTies() throws Exception {
        long first = start("{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"awayScore\":5}");
        long second = start("{\"homeTeam\":\"Spain\",\"awayTeam\":\"Brazil\",\"homeScore\":3,\"awayScore\":2}");
        mvc.perform(patch("/matches/{id}/score", first).contentType(MediaType.APPLICATION_JSON)
                .content("{\"homeScore\":1,\"awayScore\":4}")).andExpect(status().isOk());

        mvc.perform(get("/matches"))
                .andExpect(status().isOk())
                .andExpect(content().json("[" + matchJson(second, "Spain", "Brazil", 3, 2) + ","
                        + matchJson(first, "Mexico", "Canada", 1, 4) + "]", JsonCompareMode.STRICT));
    }

    @Test
    void rejectsAnActiveTeamInTheOtherPosition() throws Exception {
        start("{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\"}");
        mvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeTeam\":\"Spain\",\"awayTeam\":\"mexico\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "{", "{\"homeTeam\":\"Mexico\"}",
            "{\"homeTeam\":null,\"awayTeam\":\"Canada\"}",
            "{\"homeTeam\":\"Atlantis\",\"awayTeam\":\"Canada\"}",
            "{\"homeTeam\":\" Mexico\",\"awayTeam\":\"Canada\"}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"MEXICO\"}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"homeScore\":-1}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"awayScore\":-1}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"homeScore\":null}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"awayScore\":null}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"homeScore\":1.5}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"homeScore\":\"2\"}",
            "{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\",\"homeScore\":2147483648}"
    })
    void rejectsInvalidStartsWithTheContractErrorShape(String body) throws Exception {
        mvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/matches")).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"homeScore\":null}", "{\"awayScore\":null}",
            "{\"homeScore\":-1}", "{\"awayScore\":-1}", "{\"homeScore\":1.5}",
            "{\"homeScore\":\"2\"}", "{\"homeScore\":2147483648}", "{\"other\":1}",
            "{\"homeScore\":1,\"awayScore\":null}"})
    void rejectsInvalidUpdatesWithoutChangingTheMatch(String body) throws Exception {
        long id = start("{\"homeTeam\":\"Mexico\",\"awayTeam\":\"Canada\"}");
        mvc.perform(patch("/matches/{id}/score", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/matches")).andExpect(content().json(
                "[" + matchJson(id, "Mexico", "Canada", 0, 0) + "]", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidIds(String id) throws Exception {
        mvc.perform(delete("/matches/{id}", id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(patch("/matches/{id}/score", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsMissingRequestBodies() throws Exception {
        mvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(patch("/matches/1/score").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    private static String matchJson(long id, String homeTeam, String awayTeam, int homeScore, int awayScore) {
        return """
                {"id":%d,"homeTeam":"%s","awayTeam":"%s","homeScore":%d,"awayScore":%d}
                """.formatted(id, homeTeam, awayTeam, homeScore, awayScore);
    }

    private long start(String body) throws Exception {
        var response = mvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse();
        Number id = JsonPath.read(response.getContentAsString(), "$.id");
        org.assertj.core.api.Assertions.assertThat(response.getHeader("Location"))
                .isEqualTo("/matches/" + id.longValue());
        return id.longValue();
    }
}
