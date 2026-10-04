package org.rowny.api;

import org.rowny.api.generated.MatchesApi;
import org.rowny.api.generated.model.MatchDetails;
import org.rowny.api.generated.model.StartMatchRequest;
import org.rowny.api.generated.model.UpdateScoreRequest;
import org.rowny.domain.Scoreboard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
public class MatchesController implements MatchesApi {
    private final Scoreboard scoreboard;

    public MatchesController(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    @Override
    public ResponseEntity<MatchDetails> startMatch(StartMatchRequest request) {
        var match = scoreboard.startMatch(request.getHomeTeam().getValue(), request.getAwayTeam().getValue(),
                request.getHomeScore(), request.getAwayScore());
        return ResponseEntity.created(URI.create("/matches/" + match.id())).body(MatchMapper.details(match));
    }

    @Override
    public ResponseEntity<MatchDetails> updateScore(Long id, UpdateScoreRequest request) {
        return ResponseEntity.ok(MatchMapper.details(scoreboard.updateScore(id,
                request.getHomeScore(), request.getAwayScore())));
    }

    @Override
    public ResponseEntity<MatchDetails> finishMatch(Long id) {
        return ResponseEntity.ok(MatchMapper.details(scoreboard.finishMatch(id)));
    }

    @Override
    public ResponseEntity<List<String>> getSummary() {
        return ResponseEntity.ok(scoreboard.getSummary().stream().map(MatchMapper::summary).toList());
    }
}
