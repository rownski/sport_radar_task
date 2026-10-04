package org.rowny.domain;

import java.util.List;
import java.util.Optional;

/** Storage port. One scoreboard owns access; IDs must increase in match-start order. */
public interface MatchRepository {
    long nextId();

    void save(Match match);

    Optional<Match> findById(long id);

    List<Match> findAll();

    void deleteById(long id);
}
