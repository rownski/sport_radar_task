package org.rowny.config;

import org.rowny.api.generated.model.Team;
import org.rowny.domain.MatchRepository;
import org.rowny.domain.Scoreboard;
import org.rowny.domain.TeamCatalog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScoreboardProperties.class)
public class ScoreboardConfiguration {
    @Bean
    TeamCatalog teamCatalog(ScoreboardProperties properties) {
        var allTeams = new TeamCatalog(Arrays.stream(Team.values()).map(Team::getValue).toList());
        if (properties.teams().isEmpty()) {
            return allTeams;
        }
        if (properties.teams().size() != 48) {
            throw new IllegalArgumentException("A restricted tournament roster must contain exactly 48 distinct FIFA teams");
        }
        return new TeamCatalog(properties.teams().stream().map(allTeams::canonicalName).toList());
    }

    @Bean
    Scoreboard scoreboard(MatchRepository repository, TeamCatalog teams) {
        return new Scoreboard(repository, teams);
    }
}
