package org.rowny.domain;

import java.time.Instant;
import java.util.Objects;

public record MatchHistoryEntry(Match match, Instant startedAt, Instant finishedAt) {
    public MatchHistoryEntry {
        Objects.requireNonNull(match);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(finishedAt);
    }
}
