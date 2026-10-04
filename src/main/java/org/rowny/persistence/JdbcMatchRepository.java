package org.rowny.persistence;

import org.rowny.domain.Match;
import org.rowny.domain.MatchHistoryEntry;
import org.rowny.domain.MatchHistoryPage;
import org.rowny.domain.MatchRepository;
import org.rowny.domain.ScoreboardException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static org.rowny.domain.ScoreboardException.Reason.MATCH_FINISHED;
import static org.rowny.domain.ScoreboardException.Reason.MATCH_NOT_FOUND;
import static org.rowny.domain.ScoreboardException.Reason.TEAM_IN_USE;

public final class JdbcMatchRepository implements MatchRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate historyTransaction;

    public JdbcMatchRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.historyTransaction = new TransactionTemplate(transactionManager);
        historyTransaction.setReadOnly(true);
        historyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public Match startMatch(String homeTeam, String awayTeam, int homeScore, int awayScore) {
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                Match match = jdbc.queryForObject("""
                        INSERT INTO matches (home_team, away_team, home_score, away_score)
                        VALUES (?, ?, ?, ?) RETURNING *
                        """, JdbcMatchRepository::readMatch, homeTeam, awayTeam, homeScore, awayScore);
                // Stable reservation order avoids deadlocks for reversed pairings.
                for (String team : java.util.stream.Stream.of(homeTeam, awayTeam).sorted().toList()) {
                    jdbc.update("INSERT INTO active_match_teams (team_name, match_id) VALUES (?, ?)", team, match.id());
                }
                return match;
            }));
        } catch (DuplicateKeyException exception) {
            throw new ScoreboardException(TEAM_IN_USE, "A team already has a match in progress");
        }
    }

    @Override
    public Match updateScore(long id, Integer homeScore, Integer awayScore) {
        return Objects.requireNonNull(transaction.execute(status -> {
            Match updated = requireActive(id).withScores(homeScore, awayScore);
            jdbc.update("UPDATE matches SET home_score = ?, away_score = ? WHERE id = ?",
                    updated.homeScore(), updated.awayScore(), id);
            return updated;
        }));
    }

    @Override
    public Match finishMatch(long id) {
        return Objects.requireNonNull(transaction.execute(status -> {
            Match match = requireActive(id);
            jdbc.update("UPDATE matches SET finished_at = clock_timestamp() WHERE id = ?", id);
            jdbc.update("DELETE FROM active_match_teams WHERE match_id = ?", id);
            return match;
        }));
    }

    @Override
    public List<Match> findActive() {
        return List.copyOf(jdbc.query("""
                SELECT * FROM matches WHERE finished_at IS NULL
                ORDER BY (home_score::bigint + away_score::bigint) DESC, started_at DESC, id DESC
                """, JdbcMatchRepository::readMatch));
    }

    @Override
    public MatchHistoryPage findHistory(int page, int size) {
        // Count and rows use the same snapshot, even when another request finishes a match.
        return Objects.requireNonNull(historyTransaction.execute(status -> {
            long total = jdbc.queryForObject("SELECT count(*) FROM matches WHERE finished_at IS NOT NULL", Long.class);
            var items = jdbc.query("""
                    SELECT * FROM matches WHERE finished_at IS NOT NULL
                    ORDER BY finished_at DESC, id DESC LIMIT ? OFFSET ?
                    """, (rs, row) -> new MatchHistoryEntry(readMatch(rs, row),
                    rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("finished_at", OffsetDateTime.class).toInstant()), size, (long) page * size);
            return new MatchHistoryPage(items, page, size, total);
        }));
    }

    private Match requireActive(long id) {
        var rows = jdbc.query("SELECT * FROM matches WHERE id = ? FOR UPDATE", (rs, row) -> {
            if (rs.getObject("finished_at") != null) {
                throw new ScoreboardException(MATCH_FINISHED, "Match " + id + " is already finished");
            }
            return readMatch(rs, row);
        }, id);
        if (rows.isEmpty()) {
            throw new ScoreboardException(MATCH_NOT_FOUND, "No match with ID " + id);
        }
        return rows.getFirst();
    }

    private static Match readMatch(ResultSet rs, int row) throws SQLException {
        return new Match(rs.getLong("id"), rs.getString("home_team"), rs.getString("away_team"),
                rs.getInt("home_score"), rs.getInt("away_score"));
    }
}
