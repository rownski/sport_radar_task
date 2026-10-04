package org.rowny.api;

import org.rowny.api.generated.model.MatchDetails;
import org.rowny.api.generated.model.HistoryMatchDetails;
import org.rowny.api.generated.model.HistoryPage;
import org.rowny.api.generated.model.Team;
import org.rowny.domain.Match;
import org.rowny.domain.MatchHistoryEntry;
import org.rowny.domain.MatchHistoryPage;

import java.time.ZoneOffset;

final class MatchMapper {
    private MatchMapper() {
    }

    static MatchDetails details(Match match) {
        return new MatchDetails(match.id(), Team.fromValue(match.homeTeam()), Team.fromValue(match.awayTeam()),
                match.homeScore(), match.awayScore());
    }

    static HistoryPage history(MatchHistoryPage page) {
        return new HistoryPage(page.items().stream().map(MatchMapper::historyDetails).toList(),
                page.page(), page.size(), page.totalItems());
    }

    private static HistoryMatchDetails historyDetails(MatchHistoryEntry entry) {
        Match match = entry.match();
        return new HistoryMatchDetails(match.id(), Team.fromValue(match.homeTeam()), Team.fromValue(match.awayTeam()),
                match.homeScore(), match.awayScore(), entry.startedAt().atOffset(ZoneOffset.UTC),
                entry.finishedAt().atOffset(ZoneOffset.UTC));
    }
}
