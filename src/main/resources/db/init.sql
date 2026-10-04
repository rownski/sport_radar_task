CREATE TABLE matches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    home_team TEXT NOT NULL,
    away_team TEXT NOT NULL,
    home_score INTEGER NOT NULL CHECK (home_score >= 0),
    away_score INTEGER NOT NULL CHECK (away_score >= 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    finished_at TIMESTAMPTZ,
    CHECK (lower(home_team) <> lower(away_team))
);

-- A single country key enforces conflicts across both home and away positions.
CREATE TABLE active_match_teams (
    team_name TEXT PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(id)
);
CREATE UNIQUE INDEX active_match_teams_case_insensitive ON active_match_teams (lower(team_name));
CREATE INDEX active_match_teams_match_id ON active_match_teams (match_id);

CREATE INDEX matches_history_order ON matches (finished_at DESC, id DESC)
    WHERE finished_at IS NOT NULL;
