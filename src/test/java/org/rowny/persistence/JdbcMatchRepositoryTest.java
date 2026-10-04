package org.rowny.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rowny.domain.Match;
import org.rowny.domain.MatchHistoryEntry;
import org.rowny.domain.ScoreboardException;
import org.rowny.support.PostgresTestSupport;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.rowny.domain.ScoreboardException.Reason.MATCH_FINISHED;
import static org.rowny.domain.ScoreboardException.Reason.MATCH_NOT_FOUND;
import static org.rowny.domain.ScoreboardException.Reason.TEAM_IN_USE;

class JdbcMatchRepositoryTest extends PostgresTestSupport {
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMatchRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = newRepository();
        // Only this class's disposable Testcontainers database is cleared.
        jdbc.update("DELETE FROM active_match_teams");
        jdbc.update("DELETE FROM matches");
    }

    @Test
    void activeAndFinishedMatchesRemainWhenRepositoryIsRecreated() {
        Match finished = repository.startMatch("Mexico", "Canada", 0, 5);
        Match active = repository.startMatch("Spain", "Brazil", 2, 1);
        repository.finishMatch(finished.id());

        JdbcMatchRepository recreated = newRepository();
        assertThat(recreated.findActive()).containsExactly(active);
        var history = recreated.findHistory(0, 20);
        assertThat(history.totalItems()).isEqualTo(1);
        assertThat(history.items()).extracting(MatchHistoryEntry::match).containsExactly(finished);
        assertThat(history.items().getFirst().finishedAt()).isAfterOrEqualTo(history.items().getFirst().startedAt());
        assertThatThrownBy(() -> recreated.updateScore(finished.id(), 1, null))
                .isInstanceOfSatisfying(ScoreboardException.class, error -> assertThat(error.reason()).isEqualTo(MATCH_FINISHED));
        assertThatThrownBy(() -> recreated.finishMatch(finished.id()))
                .isInstanceOfSatisfying(ScoreboardException.class, error -> assertThat(error.reason()).isEqualTo(MATCH_FINISHED));
    }

    @Test
    void conflictRollsBackTheMatchAndFirstTeamReservation() {
        Match existing = repository.startMatch("Mexico", "Canada", 0, 0);
        // Argentina is reserved before Mexico, which is already in use.
        assertThatThrownBy(() -> repository.startMatch("Argentina", "Mexico", 0, 0))
                .isInstanceOfSatisfying(ScoreboardException.class, error -> assertThat(error.reason()).isEqualTo(TEAM_IN_USE));
        assertThat(repository.findActive()).containsExactly(existing);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matches", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT team_name FROM active_match_teams ORDER BY team_name", String.class))
                .containsExactly("Canada", "Mexico");
        assertThat(repository.startMatch("Argentina", "Brazil", 0, 0).id()).isGreaterThan(existing.id());
    }

    @Test
    void failureReleasingTeamsRollsBackTheFinishTimestamp() {
        Match match = repository.startMatch("Mexico", "Canada", 0, 5);
        var failingJdbc = new JdbcTemplate(dataSource) {
            @Override
            public int update(String sql, Object... args) {
                if (sql.startsWith("DELETE FROM active_match_teams")) {
                    throw new DataIntegrityViolationException("Injected team-release failure");
                }
                return super.update(sql, args);
            }
        };
        var failingRepository = new JdbcMatchRepository(failingJdbc, new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> failingRepository.finishMatch(match.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findActive()).containsExactly(match);
        assertThat(repository.findHistory(0, 20).totalItems()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM active_match_teams", Long.class)).isEqualTo(2);
    }

    @Test
    void databaseConstraintsRejectNegativeScoresAndSelfMatches() {
        String sql = "INSERT INTO matches (home_team, away_team, home_score, away_score) VALUES (?, ?, ?, ?)";
        assertThatThrownBy(() -> jdbc.update(sql, "Mexico", "Canada", -1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, "Mexico", "Canada", 0, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, "Mexico", "MEXICO", 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeTeamConstraintIgnoresCaseAndSpansHomeAndAway() {
        repository.startMatch("Mexico", "Canada", 0, 0);
        assertThatThrownBy(() -> repository.startMatch("Spain", "mexico", 0, 0))
                .isInstanceOfSatisfying(ScoreboardException.class, error -> assertThat(error.reason()).isEqualTo(TEAM_IN_USE));
        assertThat(repository.findActive()).hasSize(1);
    }

    @Test
    void historyTiesUseIdAndTimestampsRoundTripAsInstants() {
        Match first = repository.startMatch("Mexico", "Canada", 0, 5);
        Match second = repository.startMatch("Spain", "Brazil", 2, 1);
        repository.finishMatch(second.id());
        repository.finishMatch(first.id());
        jdbc.update("""
                UPDATE matches SET started_at = '2026-10-04T13:00:00+01:00'::timestamptz,
                finished_at = '2026-10-04T15:00:00+01:00'::timestamptz
                """);

        var history = repository.findHistory(0, 1);
        assertThat(history.totalItems()).isEqualTo(2);
        assertThat(history.items()).containsExactly(new MatchHistoryEntry(second,
                Instant.parse("2026-10-04T12:00:00Z"), Instant.parse("2026-10-04T14:00:00Z")));
        assertThat(repository.findHistory(1, 1).items()).extracting(MatchHistoryEntry::match).containsExactly(first);
        assertThat(repository.findHistory(Integer.MAX_VALUE, 100).items()).isEmpty();
        assertThat(repository.findHistory(Integer.MAX_VALUE, 100).totalItems()).isEqualTo(2);
    }

    @Test
    void activeSummaryUsesStartTimeAndWidenedScoreTotals() {
        Match first = repository.startMatch("Mexico", "Canada", Integer.MAX_VALUE, Integer.MAX_VALUE);
        Match second = repository.startMatch("Spain", "Brazil", Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(repository.findActive()).containsExactly(second, first);
        repository.updateScore(first.id(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(repository.findActive()).containsExactly(second, first);
        // Start order is a timestamp, not an assumption about sequence allocation/commit order.
        jdbc.update("UPDATE matches SET started_at = started_at + interval '1 day' WHERE id = ?", first.id());
        assertThat(repository.findActive()).containsExactly(first, second);
    }

    @Test
    void unknownMatchesAreDistinctFromFinishedMatches() {
        assertThatThrownBy(() -> repository.updateScore(Long.MAX_VALUE, 1, null))
                .isInstanceOfSatisfying(ScoreboardException.class, error -> assertThat(error.reason()).isEqualTo(MATCH_NOT_FOUND));
        assertThatThrownBy(() -> repository.finishMatch(Long.MAX_VALUE))
                .isInstanceOfSatisfying(ScoreboardException.class, error -> assertThat(error.reason()).isEqualTo(MATCH_NOT_FOUND));
    }

    @Test
    void historyCountAndItemsShareASnapshotDuringConcurrentFinish() throws Exception {
        Match first = repository.startMatch("Mexico", "Canada", 0, 5);
        Match second = repository.startMatch("Spain", "Brazil", 2, 1);
        repository.finishMatch(first.id());
        var counted = new CountDownLatch(1);
        var continueRead = new CountDownLatch(1);
        var pausedJdbc = new JdbcTemplate(dataSource) {
            @Override
            public <T> T queryForObject(String sql, Class<T> type) {
                T result = super.queryForObject(sql, type);
                counted.countDown();
                try {
                    if (!continueRead.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent finish");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return result;
            }
        };
        var pausedRepository = new JdbcMatchRepository(pausedJdbc, new DataSourceTransactionManager(dataSource));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var read = executor.submit(() -> pausedRepository.findHistory(0, 20));
            try {
                assertThat(counted.await(10, TimeUnit.SECONDS)).isTrue();
                repository.finishMatch(second.id());
            } finally {
                continueRead.countDown();
            }
            var page = read.get(10, TimeUnit.SECONDS);
            assertThat(page.totalItems()).isEqualTo(1);
            assertThat(page.items()).extracting(MatchHistoryEntry::match).containsExactly(first);
        }
        assertThat(repository.findHistory(0, 20).totalItems()).isEqualTo(2);
    }

    @Test
    void concurrentReversedPairingsAcrossRepositoryInstancesAllowOneMatch() throws Exception {
        int callers = 8;
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(callers)) {
            var attempts = java.util.stream.IntStream.range(0, callers).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for start");
                }
                try {
                    newRepository().startMatch(i % 2 == 0 ? "Mexico" : "Canada",
                            i % 2 == 0 ? "Canada" : "Mexico", 0, 0);
                    return true;
                } catch (ScoreboardException error) {
                    assertThat(error.reason()).isEqualTo(TEAM_IN_USE);
                    return false;
                }
            })).toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            int succeeded = 0;
            for (var attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM matches", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM active_match_teams", Long.class)).isEqualTo(2);
        }
    }

    @Test
    void concurrentPartialUpdatesAcrossRepositoryInstancesDoNotLoseScores() throws Exception {
        Match match = repository.startMatch("Mexico", "Canada", 0, 0);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var home = executor.submit(() -> {
                start.await();
                return newRepository().updateScore(match.id(), 2, null);
            });
            var away = executor.submit(() -> {
                start.await();
                return newRepository().updateScore(match.id(), null, 5);
            });
            start.countDown();
            home.get(10, TimeUnit.SECONDS);
            away.get(10, TimeUnit.SECONDS);
            assertThat(repository.findActive()).containsExactly(new Match(match.id(), "Mexico", "Canada", 2, 5));
        }
    }

    @Test
    void concurrentFinishAndUpdateCannotChangeArchivedScores() throws Exception {
        Match match = repository.startMatch("Mexico", "Canada", 0, 0);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> {
                start.await();
                try {
                    return newRepository().updateScore(match.id(), 1, null);
                } catch (ScoreboardException error) {
                    assertThat(error.reason()).isEqualTo(MATCH_FINISHED);
                    return null;
                }
            });
            var finish = executor.submit(() -> {
                start.await();
                return newRepository().finishMatch(match.id());
            });
            start.countDown();
            Match updated = update.get(10, TimeUnit.SECONDS);
            Match finished = finish.get(10, TimeUnit.SECONDS);
            assertThat(finished.homeScore()).isEqualTo(updated == null ? 0 : 1);
            assertThat(repository.findActive()).isEmpty();
            assertThat(repository.findHistory(0, 20).items()).extracting(MatchHistoryEntry::match).containsExactly(finished);
        }
    }

    private JdbcMatchRepository newRepository() {
        return new JdbcMatchRepository(jdbc, new DataSourceTransactionManager(dataSource));
    }
}
