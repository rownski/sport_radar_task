package org.rowny.domain;

import java.util.Objects;

import static org.rowny.domain.ScoreboardException.Reason.INVALID_INPUT;

public record Match(long id, String homeTeam, String awayTeam, int homeScore, int awayScore) {
    public Match {
        Objects.requireNonNull(homeTeam);
        Objects.requireNonNull(awayTeam);
        if (id < 1 || homeTeam.isBlank() || awayTeam.isBlank()
                || homeTeam.equalsIgnoreCase(awayTeam) || homeScore < 0 || awayScore < 0) {
            throw new ScoreboardException(INVALID_INPUT, "Invalid match details");
        }
    }

    public long totalScore() {
        return (long) homeScore + awayScore;
    }

    public boolean includes(String team) {
        return homeTeam.equalsIgnoreCase(team) || awayTeam.equalsIgnoreCase(team);
    }

    public Match withScores(Integer homeScore, Integer awayScore) {
        return new Match(id, homeTeam, awayTeam,
                homeScore == null ? this.homeScore : homeScore,
                awayScore == null ? this.awayScore : awayScore);
    }
}
