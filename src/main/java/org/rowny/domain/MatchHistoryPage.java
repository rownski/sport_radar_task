package org.rowny.domain;

import java.util.List;

public record MatchHistoryPage(List<MatchHistoryEntry> items, int page, int size, long totalItems) {
    public MatchHistoryPage {
        items = List.copyOf(items);
    }
}
