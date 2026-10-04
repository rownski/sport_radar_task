package org.rowny.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.rowny.domain.ScoreboardException.Reason.INVALID_INPUT;
import static org.rowny.domain.ScoreboardException.Reason.MATCH_NOT_FOUND;
import static org.rowny.domain.ScoreboardException.Reason.TEAM_IN_USE;

public final class Scoreboard {
    private static final Comparator<Match> SUMMARY_ORDER = Comparator
            .comparingLong(Match::totalScore).reversed()
            .thenComparing(Comparator.comparingLong(Match::id).reversed());

    private final MatchRepository repository;
    private final TeamCatalog teams;

    public Scoreboard(MatchRepository repository, TeamCatalog teams) {
        this.repository = Objects.requireNonNull(repository);
        this.teams = Objects.requireNonNull(teams);
    }

    public synchronized Match startMatch(String homeTeam, String awayTeam,
                                          Integer homeScore, Integer awayScore) {
        String home = teams.canonicalName(homeTeam);
        String away = teams.canonicalName(awayTeam);
        validateScore(homeScore);
        validateScore(awayScore);
        if (home.equals(away)) {
            throw new ScoreboardException(INVALID_INPUT, "A team cannot play against itself");
        }
        if (repository.findAll().stream().anyMatch(match -> match.includes(home) || match.includes(away))) {
            throw new ScoreboardException(TEAM_IN_USE, "A team already has a match in progress");
        }
        var match = new Match(repository.nextId(), home, away,
                homeScore == null ? 0 : homeScore, awayScore == null ? 0 : awayScore);
        repository.save(match);
        return match;
    }

    public synchronized Match updateScore(long id, Integer homeScore, Integer awayScore) {
        if (homeScore == null && awayScore == null) {
            throw new ScoreboardException(INVALID_INPUT, "At least one score must be supplied");
        }
        validateScore(homeScore);
        validateScore(awayScore);
        Match updated = requireMatch(id).withScores(homeScore, awayScore);
        repository.save(updated);
        return updated;
    }

    public synchronized Match finishMatch(long id) {
        Match match = requireMatch(id);
        repository.deleteById(id);
        return match;
    }

    public synchronized List<Match> getSummary() {
        return repository.findAll().stream().sorted(SUMMARY_ORDER).toList();
    }

    private Match requireMatch(long id) {
        if (id < 1) {
            throw new ScoreboardException(INVALID_INPUT, "Match ID must be positive");
        }
        return repository.findById(id)
                .orElseThrow(() -> new ScoreboardException(MATCH_NOT_FOUND, "No match in progress with ID " + id));
    }

    private static void validateScore(Integer score) {
        if (score != null && score < 0) {
            throw new ScoreboardException(INVALID_INPUT, "Scores cannot be negative");
        }
    }
}
