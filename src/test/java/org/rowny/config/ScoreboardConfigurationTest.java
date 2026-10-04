package org.rowny.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rowny.api.generated.model.Team;
import org.rowny.domain.Scoreboard;
import org.rowny.domain.MatchRepository;
import org.rowny.domain.TeamCatalog;
import org.rowny.persistence.InMemoryMatchRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreboardConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ScoreboardConfiguration.class)
            .withBean(MatchRepository.class, InMemoryMatchRepository::new);

    @Test
    void defaultsToEverySupportedFifaTeam() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var catalog = context.getBean(TeamCatalog.class);
            for (Team team : Team.values()) {
                assertThat(catalog.canonicalName(team.getValue())).isEqualTo(team.getValue());
            }
        });
    }

    @Test
    void schemaAndGeneratedCatalogueContainTheSame211DistinctTeams() {
        try (var resource = getClass().getResourceAsStream("/openapi/teams.yaml")) {
            Map<?, ?> schema = new Yaml().load(resource);
            var names = ((List<?>) schema.get("enum")).stream().map(String.class::cast).toList();
            assertThat(names).hasSize(211).doesNotHaveDuplicates();
            assertThat(Arrays.stream(Team.values()).map(Team::getValue).toList())
                    .containsExactlyElementsOf(names);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    @Test
    void allGeneratedNamesRoundTripCaseInsensitively() {
        for (Team team : Team.values()) {
            assertThat(Team.fromValue(team.getValue().toUpperCase(java.util.Locale.ROOT))).isEqualTo(team);
        }
    }

    @Test
    void restrictsToExactly48TeamsAndRejectsDisabledTeams() {
        var names = firstTeams(48);
        names.set(0, names.getFirst().toUpperCase(java.util.Locale.ROOT));
        withRoster(names).run(context -> {
            assertThat(context).hasNotFailed();
            var scoreboard = context.getBean(Scoreboard.class);
            assertThat(scoreboard.startMatch(names.get(0), names.get(1), null, null).homeTeam())
                    .isEqualTo(Team.values()[0].getValue());
            assertThatThrownBy(() -> scoreboard.startMatch(Team.values()[48].getValue(),
                    names.get(2), null, null)).hasMessageContaining("Unsupported or disabled team");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 47, 49})
    void rejectsIncorrectRosterSizesAtStartup(int size) {
        withRoster(firstTeams(size)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "A restricted tournament roster must contain exactly 48 distinct FIFA teams");
        });
    }

    @Test
    void rejectsDuplicateTeamsIgnoringCaseAtStartup() {
        var names = firstTeams(48);
        names.set(47, names.getFirst().toUpperCase(java.util.Locale.ROOT));
        withRoster(names).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Team names must be non-empty and distinct");
        });
    }

    @Test
    void rejectsUnsupportedTeamsAtStartup() {
        var names = firstTeams(48);
        names.set(47, "Atlantis");
        withRoster(names).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Unsupported or disabled team: Atlantis");
        });
    }

    private ApplicationContextRunner withRoster(List<String> names) {
        String[] properties = java.util.stream.IntStream.range(0, names.size())
                .mapToObj(i -> "scoreboard.teams[" + i + "]=" + names.get(i)).toArray(String[]::new);
        return runner.withPropertyValues(properties);
    }

    private static ArrayList<String> firstTeams(int count) {
        return new ArrayList<>(Arrays.stream(Team.values()).limit(count).map(Team::getValue).toList());
    }
}
