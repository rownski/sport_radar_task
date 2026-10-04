package org.rowny.persistence;

import org.rowny.domain.Match;
import org.rowny.domain.MatchRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Access is serialized by the owning Scoreboard, not by individual map operations. */
public final class InMemoryMatchRepository implements MatchRepository {
    private final Map<Long, Match> matches = new HashMap<>();
    private long lastId;

    @Override
    public long nextId() {
        lastId = Math.incrementExact(lastId);
        return lastId;
    }

    @Override
    public void save(Match match) {
        matches.put(match.id(), match);
    }

    @Override
    public Optional<Match> findById(long id) {
        return Optional.ofNullable(matches.get(id));
    }

    @Override
    public List<Match> findAll() {
        return List.copyOf(matches.values());
    }

    @Override
    public void deleteById(long id) {
        matches.remove(id);
    }
}
