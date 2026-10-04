package org.rowny.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static org.rowny.domain.ScoreboardException.Reason.INVALID_INPUT;

public final class TeamCatalog {
    private final Map<String, String> names;

    public TeamCatalog(Collection<String> canonicalNames) {
        var names = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (String name : canonicalNames) {
            if (name == null || name.isBlank() || names.putIfAbsent(name, name) != null) {
                throw new IllegalArgumentException("Team names must be non-empty and distinct");
            }
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("A team catalogue cannot be empty");
        }
        this.names = Collections.unmodifiableMap(names);
    }

    public String canonicalName(String name) {
        String canonical = name == null ? null : names.get(name);
        if (canonical == null) {
            throw new ScoreboardException(INVALID_INPUT, "Unsupported or disabled team: " + name);
        }
        return canonical;
    }
}
