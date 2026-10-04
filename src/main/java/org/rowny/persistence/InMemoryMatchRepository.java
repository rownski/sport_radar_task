package org.rowny.persistence;

import org.rowny.domain.Match;
import org.rowny.domain.MatchRepository;
import org.rowny.domain.MatchHistoryEntry;
import org.rowny.domain.MatchHistoryPage;
import org.rowny.domain.ScoreboardException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.rowny.domain.ScoreboardException.Reason.MATCH_FINISHED;
import static org.rowny.domain.ScoreboardException.Reason.MATCH_NOT_FOUND;
import static org.rowny.domain.ScoreboardException.Reason.TEAM_IN_USE;

/** Java-only adapter for standalone library use and unit tests, not application storage. */
public final class InMemoryMatchRepository implements MatchRepository {
    private final Map<Long, Match> matches = new HashMap<>();
    private final Map<Long, Instant> started = new HashMap<>();
    private final Map<Long, MatchHistoryEntry> history = new HashMap<>();
    private final Clock clock;
    private long lastId;

    public InMemoryMatchRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryMatchRepository(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    public synchronized Match startMatch(String homeTeam, String awayTeam, int homeScore, int awayScore) {
        if (matches.values().stream().anyMatch(match -> match.includes(homeTeam) || match.includes(awayTeam))) {
            throw new ScoreboardException(TEAM_IN_USE, "A team already has a match in progress");
        }
        var match = new Match(Math.incrementExact(lastId), homeTeam, awayTeam, homeScore, awayScore);
        lastId = match.id();
        matches.put(match.id(), match);
        started.put(match.id(), clock.instant());
        return match;
    }

    @Override
    public synchronized Match updateScore(long id, Integer homeScore, Integer awayScore) {
        Match updated = requireActive(id).withScores(homeScore, awayScore);
        matches.put(id, updated);
        return updated;
    }

    @Override
    public synchronized Match finishMatch(long id) {
        Match match = requireActive(id);
        history.put(id, new MatchHistoryEntry(match, started.remove(id), clock.instant()));
        matches.remove(id);
        return match;
    }

    @Override
    public synchronized List<Match> findActive() {
        return matches.values().stream().sorted(Comparator.comparingLong(Match::totalScore).reversed()
                .thenComparing(Comparator.comparingLong(Match::id).reversed())).toList();
    }

    @Override
    public synchronized MatchHistoryPage findHistory(int page, int size) {
        var items = history.values().stream().sorted(Comparator.comparing(MatchHistoryEntry::finishedAt).reversed()
                        .thenComparing(Comparator.comparingLong((MatchHistoryEntry entry) -> entry.match().id()).reversed()))
                .skip((long) page * size).limit(size).toList();
        return new MatchHistoryPage(items, page, size, history.size());
    }

    private Match requireActive(long id) {
        if (history.containsKey(id)) {
            throw new ScoreboardException(MATCH_FINISHED, "Match " + id + " is already finished");
        }
        Match match = matches.get(id);
        if (match == null) {
            throw new ScoreboardException(MATCH_NOT_FOUND, "No match with ID " + id);
        }
        return match;
    }
}
