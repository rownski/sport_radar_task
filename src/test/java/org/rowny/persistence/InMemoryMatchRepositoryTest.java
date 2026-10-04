package org.rowny.persistence;

import org.junit.jupiter.api.Test;
import org.rowny.domain.Match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMatchRepositoryTest {
    @Test
    void storesReplacesAndRemovesImmutableMatchValues() {
        var repository = new InMemoryMatchRepository();
        var original = new Match(repository.nextId(), "Mexico", "Canada", 0, 0);
        repository.save(original);
        var snapshot = repository.findAll();
        var updated = original.withScores(1, 2);
        repository.save(updated);

        assertThat(repository.findById(original.id())).contains(updated);
        assertThat(repository.findAll()).containsExactly(updated);
        assertThat(snapshot).containsExactly(original);
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);

        repository.deleteById(original.id());
        assertThat(repository.findById(original.id())).isEmpty();
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.nextId()).isGreaterThan(original.id());
    }
}
