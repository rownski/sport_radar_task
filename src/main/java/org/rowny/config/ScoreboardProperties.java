package org.rowny.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("scoreboard")
public record ScoreboardProperties(List<String> teams) {
    public ScoreboardProperties {
        teams = teams == null ? List.of() : List.copyOf(teams);
    }
}
