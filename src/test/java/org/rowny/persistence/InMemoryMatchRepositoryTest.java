package org.rowny.persistence;

import org.junit.jupiter.api.Test;
import org.rowny.domain.MatchHistoryEntry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMatchRepositoryTest {
    @Test
    void storesReplacesAndArchivesImmutableMatchValues() {
        var repository = new InMemoryMatchRepository();
        var original = repository.startMatch("Mexico", "Canada", 0, 0);
        var snapshot = repository.findActive();
        var updated = repository.updateScore(original.id(), 1, 2);

        assertThat(repository.findActive()).containsExactly(updated);
        assertThat(snapshot).containsExactly(original);
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);

        assertThat(repository.finishMatch(original.id())).isEqualTo(updated);
        assertThat(repository.findActive()).isEmpty();
        assertThat(repository.findHistory(0, 20).items()).extracting(entry -> entry.match()).containsExactly(updated);
        assertThat(repository.startMatch("Mexico", "Canada", 0, 0).id()).isGreaterThan(original.id());
    }

    @Test
    void sameFinishTimeUsesDescendingIdAndHistorySnapshotsAreImmutable() {
        Instant time = Instant.parse("2026-10-04T12:00:00Z");
        var repository = new InMemoryMatchRepository(Clock.fixed(time, ZoneOffset.UTC));
        var first = repository.startMatch("Mexico", "Canada", 0, 5);
        var second = repository.startMatch("Spain", "Brazil", 2, 1);
        repository.finishMatch(second.id());
        repository.finishMatch(first.id());

        var page = repository.findHistory(0, 1);
        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.items()).containsExactly(new MatchHistoryEntry(second, time, time));
        assertThat(repository.findHistory(1, 1).items()).extracting(MatchHistoryEntry::match).containsExactly(first);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
