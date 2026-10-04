package org.rowny.api;

import org.rowny.api.generated.model.MatchDetails;
import org.rowny.api.generated.model.Team;
import org.rowny.domain.Match;

final class MatchMapper {
    private MatchMapper() {
    }

    static MatchDetails details(Match match) {
        return new MatchDetails(match.id(), Team.fromValue(match.homeTeam()), Team.fromValue(match.awayTeam()),
                match.homeScore(), match.awayScore());
    }
}
