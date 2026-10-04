package org.rowny.domain;

import java.util.List;

/** Storage port. Mutations must atomically enforce active-team and finished-match rules. */
public interface MatchRepository {
    Match startMatch(String homeTeam, String awayTeam, int homeScore, int awayScore);

    Match updateScore(long id, Integer homeScore, Integer awayScore);

    Match finishMatch(long id);

    /** Matches ordered by total score descending, then start order descending. */
    List<Match> findActive();

    MatchHistoryPage findHistory(int page, int size);
}
