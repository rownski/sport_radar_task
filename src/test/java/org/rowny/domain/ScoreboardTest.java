package org.rowny.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.rowny.persistence.InMemoryMatchRepository;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.rowny.domain.ScoreboardException.Reason.INVALID_INPUT;
import static org.rowny.domain.ScoreboardException.Reason.MATCH_NOT_FOUND;
import static org.rowny.domain.ScoreboardException.Reason.TEAM_IN_USE;

class ScoreboardTest {
    private Scoreboard scoreboard;

    @BeforeEach
    void setUp() {
        scoreboard = new Scoreboard(new InMemoryMatchRepository(), new TeamCatalog(List.of(
                "Mexico", "Canada", "Spain", "Brazil", "Germany", "France",
                "Uruguay", "Italy", "Argentina", "Australia")));
    }

    @Test
    void startsWithCanonicalTeamsAndDefaultScores() {
        Match match = scoreboard.startMatch("mExIcO", "CANADA", null, null);

        assertThat(match).isEqualTo(new Match(1, "Mexico", "Canada", 0, 0));
        assertThat(scoreboard.getSummary()).containsExactly(match);
    }

    @Test
    void supportsEitherInitialScoreIndependently() {
        Match first = scoreboard.startMatch("Mexico", "Canada", 3, null);
        Match second = scoreboard.startMatch("Spain", "Brazil", null, 4);

        assertThat(first.homeScore()).isEqualTo(3);
        assertThat(first.awayScore()).isZero();
        assertThat(second.homeScore()).isZero();
        assertThat(second.awayScore()).isEqualTo(4);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Atlantis", " Mexico", "Mexico ", "MEX"})
    void rejectsUnknownAndNonExactNames(String name) {
        assertReason(INVALID_INPUT, () -> scoreboard.startMatch(name, "Canada", null, null));
        assertThat(scoreboard.getSummary()).isEmpty();
    }

    @Test
    void rejectsPlayingAgainstTheSameTeamIgnoringCase() {
        assertReason(INVALID_INPUT, () -> scoreboard.startMatch("Mexico", "MEXICO", 0, 0));
    }

    @ParameterizedTest
    @CsvSource({"Mexico, Spain", "Spain, Mexico", "Canada, Spain", "Spain, Canada", "Canada, Mexico"})
    void rejectsTeamConflictsInEitherPosition(String home, String away) {
        scoreboard.startMatch("Mexico", "Canada", null, null);
        assertReason(TEAM_IN_USE, () -> scoreboard.startMatch(home, away, null, null));
        assertThat(scoreboard.getSummary()).hasSize(1);
    }

    @Test
    void rejectedStartsDoNotConsumeAnId() {
        assertReason(INVALID_INPUT, () -> scoreboard.startMatch("Mexico", "Canada", -1, 0));
        assertThat(scoreboard.startMatch("Mexico", "Canada", null, null).id()).isEqualTo(1);
    }

    @Test
    void replacesScoresAndAllowsCorrectionsWithoutMutatingSnapshots() {
        Match initial = scoreboard.startMatch("Mexico", "Canada", 2, 5);
        List<Match> snapshot = scoreboard.getSummary();

        Match updated = scoreboard.updateScore(initial.id(), 1, null);
        assertThat(updated).isEqualTo(new Match(initial.id(), "Mexico", "Canada", 1, 5));
        assertThat(scoreboard.updateScore(initial.id(), null, 0).awayScore()).isZero();
        assertThat(scoreboard.updateScore(initial.id(), 0, 2)).isEqualTo(
                new Match(initial.id(), "Mexico", "Canada", 0, 2));
        assertThat(snapshot).containsExactly(initial);
        assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEmptyAndNegativeUpdatesWithoutChangingState() {
        Match initial = scoreboard.startMatch("Mexico", "Canada", 2, 5);

        assertReason(INVALID_INPUT, () -> scoreboard.updateScore(initial.id(), null, null));
        assertReason(INVALID_INPUT, () -> scoreboard.updateScore(initial.id(), -1, 0));
        assertReason(INVALID_INPUT, () -> scoreboard.updateScore(initial.id(), 0, -1));
        assertThat(scoreboard.getSummary()).containsExactly(initial);
    }

    @Test
    void rejectsNegativeInitialScoresInEitherPosition() {
        assertReason(INVALID_INPUT, () -> scoreboard.startMatch("Mexico", "Canada", -1, null));
        assertReason(INVALID_INPUT, () -> scoreboard.startMatch("Mexico", "Canada", null, -1));
    }

    @Test
    void sortsByTotalThenStartOrderAndDoesNotTreatUpdateAsNewStart() {
        Match mexico = scoreboard.startMatch("Mexico", "Canada", 0, 5);
        Match spain = scoreboard.startMatch("Spain", "Brazil", 10, 2);
        Match germany = scoreboard.startMatch("Germany", "France", 2, 2);
        Match uruguay = scoreboard.startMatch("Uruguay", "Italy", 6, 6);
        Match argentina = scoreboard.startMatch("Argentina", "Australia", 3, 1);

        assertThat(scoreboard.getSummary()).extracting(Match::id)
                .containsExactly(uruguay.id(), spain.id(), mexico.id(), argentina.id(), germany.id());
        scoreboard.updateScore(spain.id(), 9, 3);
        assertThat(scoreboard.getSummary()).extracting(Match::id)
                .containsExactly(uruguay.id(), spain.id(), mexico.id(), argentina.id(), germany.id());
    }

    @Test
    void comparesTotalsWithoutIntegerOverflow() {
        Match largest = scoreboard.startMatch("Mexico", "Canada", Integer.MAX_VALUE, Integer.MAX_VALUE);
        Match other = scoreboard.startMatch("Spain", "Brazil", 1, 1);

        assertThat(largest.totalScore()).isEqualTo(4294967294L);
        assertThat(scoreboard.getSummary()).containsExactly(largest, other);
    }

    @Test
    void finishingReturnsFinalDetailsRemovesMatchAndReleasesTeams() {
        Match match = scoreboard.startMatch("Mexico", "Canada", 0, 5);

        assertThat(scoreboard.finishMatch(match.id())).isEqualTo(match);
        assertThat(scoreboard.getSummary()).isEmpty();
        assertReason(MATCH_NOT_FOUND, () -> scoreboard.finishMatch(match.id()));
        assertReason(MATCH_NOT_FOUND, () -> scoreboard.updateScore(match.id(), 1, null));
        assertThat(scoreboard.startMatch("Canada", "Mexico", null, null).id()).isGreaterThan(match.id());
    }

    @Test
    void missingMatchesAreNotFound() {
        assertReason(MATCH_NOT_FOUND, () -> scoreboard.finishMatch(99));
        assertReason(MATCH_NOT_FOUND, () -> scoreboard.updateScore(99, 1, null));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveIds(long id) {
        assertReason(INVALID_INPUT, () -> scoreboard.finishMatch(id));
        assertReason(INVALID_INPUT, () -> scoreboard.updateScore(id, 1, null));
    }

    @Test
    void anEmptyScoreboardHasAnEmptySummary() {
        assertThat(scoreboard.getSummary()).isEmpty();
    }

    @Test
    void concurrentConflictingStartsAllowExactlyOneMatch() throws Exception {
        int callers = 12;
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(callers)) {
            var attempts = java.util.stream.IntStream.range(0, callers).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for simultaneous start");
                }
                try {
                    scoreboard.startMatch("Mexico", "Canada", null, null);
                    return true;
                } catch (ScoreboardException exception) {
                    assertThat(exception.reason()).isEqualTo(TEAM_IN_USE);
                    return false;
                }
            })).toList();
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            int successes = 0;
            for (var attempt : attempts) {
                if (attempt.get(5, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(scoreboard.getSummary()).hasSize(1);
        }
    }

    @Test
    void concurrentPartialUpdatesPreserveBothScores() throws Exception {
        Match match = scoreboard.startMatch("Mexico", "Canada", null, null);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var home = executor.submit(() -> {
                start.await();
                return scoreboard.updateScore(match.id(), 2, null);
            });
            var away = executor.submit(() -> {
                start.await();
                return scoreboard.updateScore(match.id(), null, 5);
            });
            start.countDown();
            home.get(5, TimeUnit.SECONDS);
            away.get(5, TimeUnit.SECONDS);
            assertThat(scoreboard.getSummary()).containsExactly(new Match(match.id(), "Mexico", "Canada", 2, 5));
        }
    }

    private static void assertReason(ScoreboardException.Reason reason, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ScoreboardException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason));
    }
}
