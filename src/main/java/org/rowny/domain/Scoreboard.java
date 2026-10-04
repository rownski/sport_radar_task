package org.rowny.domain;

import java.util.List;
import java.util.Objects;

import static org.rowny.domain.ScoreboardException.Reason.INVALID_INPUT;

public final class Scoreboard {
    private final MatchRepository repository;
    private final TeamCatalog teams;

    public Scoreboard(MatchRepository repository, TeamCatalog teams) {
        this.repository = Objects.requireNonNull(repository);
        this.teams = Objects.requireNonNull(teams);
    }

    public Match startMatch(String homeTeam, String awayTeam, Integer homeScore, Integer awayScore) {
        String home = teams.canonicalName(homeTeam);
        String away = teams.canonicalName(awayTeam);
        validateScore(homeScore);
        validateScore(awayScore);
        if (home.equals(away)) {
            throw new ScoreboardException(INVALID_INPUT, "A team cannot play against itself");
        }
        return repository.startMatch(home, away,
                homeScore == null ? 0 : homeScore, awayScore == null ? 0 : awayScore);
    }

    public Match updateScore(long id, Integer homeScore, Integer awayScore) {
        validateId(id);
        if (homeScore == null && awayScore == null) {
            throw new ScoreboardException(INVALID_INPUT, "At least one score must be supplied");
        }
        validateScore(homeScore);
        validateScore(awayScore);
        return repository.updateScore(id, homeScore, awayScore);
    }

    public Match finishMatch(long id) {
        validateId(id);
        return repository.finishMatch(id);
    }

    public List<Match> getSummary() {
        return repository.findActive();
    }

    public MatchHistoryPage getHistory(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ScoreboardException(INVALID_INPUT, "Page must be non-negative and size must be between 1 and 100");
        }
        return repository.findHistory(page, size);
    }

    private static void validateId(long id) {
        if (id < 1) {
            throw new ScoreboardException(INVALID_INPUT, "Match ID must be positive");
        }
    }

    private static void validateScore(Integer score) {
        if (score != null && score < 0) {
            throw new ScoreboardException(INVALID_INPUT, "Scores cannot be negative");
        }
    }
}
