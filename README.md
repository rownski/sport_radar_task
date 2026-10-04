# World Cup scoreboard

A Java 21 scoreboard library with a Spring Boot REST adapter, PostgreSQL storage, and finished-match history. The project started as an empty Maven project generated in IntelliJ; requirements and design were clarified before implementation. History was added on `feature/match-history` after the initial in-memory version and structured-list correction.

## Agreed requirements

- Support multiple matches in progress. A team cannot play against itself or participate in another active match, in either position.
- Start with independently optional scores, defaulting to `0–0`.
- Replace supplied scores on update; omitted scores remain unchanged. Corrections may decrease scores. Negative scores, explicit nulls, and empty updates are invalid.
- Finish removes a match from the active scoreboard, retains its history, and returns structured final details. Updating or finishing an already-finished match returns `409`; an unknown match returns `404`.
- Summary sorts by total score descending, then most recently started. Return structured match objects containing IDs, team names, and scores. Return `[]` when empty.
- Accept canonical FIFA team names case-insensitively, without aliases or whitespace normalization. Return canonical names.
- Enable all FIFA teams by default. An optional tournament list must contain exactly 48 distinct supported teams. The API contract still lists every supported team.
- Persist both active and finished matches. History includes UTC start/finish timestamps, newest finished first, with ID descending as the tie-breaker.
- History pagination is zero-based: default `page=0`, `size=20`, maximum size `100`. A page past the end returns `200` with empty `items` and the actual `totalItems`.
- Use Spring JDBC, Docker Compose for both services, and initial SQL only—no JPA or migration framework.

## Contract

The OpenAPI document in `src/main/resources/openapi/scoreboard.yaml` is the API source of truth. Maven generates the API interfaces, transport models, enums, and validation annotations into `target/`; generated Java is not handwritten or committed. Swagger UI is intentionally excluded.

Versions: Java **21**, Spring Boot **4.1.1**, OpenAPI Generator **7.25.0**, OpenAPI **3.1.2**, PostgreSQL **18.6**. The initially selected OpenAPI 3.2.1 was rejected by the generator's parser; the user approved 3.1.2 rather than disabling contract validation. The generator warns that 3.1 support is beta; this contract's generated behavior is covered by API tests.

| Method | Path | Success |
| --- | --- | --- |
| POST | `/matches` | `201`, structured created match |
| PATCH | `/matches/{id}/score` | `200`, structured updated match |
| DELETE | `/matches/{id}` | `200`, structured final match |
| GET | `/matches` | `200`, ordered array of structured matches |
| GET | `/matches/history?page=0&size=20` | `200`, page of finished matches |

Errors: `400` for invalid input/pagination, `404` for an unknown match, and `409` for an active-team conflict or a mutation of a finished match.

Start, update, and finish return this shape:

```json
{"id":1,"homeTeam":"Mexico","awayTeam":"Canada","homeScore":0,"awayScore":5}
```

Listing returns an array of those same objects:

```json
[{"id":1,"homeTeam":"Mexico","awayTeam":"Canada","homeScore":0,"awayScore":5}]
```

Error shape:

```json
{"status":409,"message":"A team already has a match in progress"}
```

Scores are JSON integers represented as Java `int` (`0` to `2147483647`); IDs are positive Java `long` values. Fractional numbers and quoted scores are rejected. The domain repeats core invariant checks so clients using the library directly cannot bypass HTTP validation.

History response (existing endpoint fields are unchanged; timestamps appear only here):

```json
{
  "items": [
    {
      "id": 1,
      "homeTeam": "Mexico",
      "awayTeam": "Canada",
      "homeScore": 0,
      "awayScore": 5,
      "startedAt": "2026-10-04T18:00:00Z",
      "finishedAt": "2026-10-04T19:45:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1
}
```

Empty history is `{"items":[],"page":0,"size":20,"totalItems":0}`, not a bare array.

## Build and run

### Both services in Docker

Requires Docker with Compose. The app is built inside a multi-stage Dockerfile and runs as a non-root user. First build needs network access for images and Maven dependencies.

```sh
docker compose up --build -d
docker compose logs -f app
```

App: `http://localhost:8080`. PostgreSQL is exposed only on `127.0.0.1:5432` for IDE access:

```text
Database: scoreboard
User:     scoreboard
Password: scoreboard_dev
JDBC URL: jdbc:postgresql://localhost:5432/scoreboard
```

These are **local-development defaults**, not production credentials. Optionally copy `.env.example` to `.env` and change credentials or `APP_PORT`/`DB_PORT`; `.env` is ignored by Git. Database credentials/name changes do not rewrite users in an already-initialized volume.

```sh
docker compose down
```

Stopping/removing containers this way retains `postgres-data`. The volume holds active matches, history, and ID allocation; IDs survive app/database container recreation and may have gaps after rolled-back allocations.

### Tests and local Java development

Requires JDK 21, Maven 3.6.3+, and a working Docker daemon for PostgreSQL integration tests. Testcontainers creates isolated databases from the same `db/init.sql`; it never uses the Compose database or IDE data.

```sh
mvn clean verify
docker compose up -d database
java -jar target/wc_score_board-1.0-SNAPSHOT.jar
```

Use the last two commands instead of running the Compose app. The local app uses `DB_URL`, `DB_USER`, and `DB_PASSWORD`, defaulting to the IDE settings above. For custom settings, export those variables explicitly; `.env` is read by Compose, not by a standalone Java process.

Java-only unit tests without a Docker daemon:

```sh
mvn -Dtest=ScoreboardTest,ScoreboardConfigurationTest,InMemoryMatchRepositoryTest test
```

The Docker image build skips tests because it has no Docker daemon; `mvn verify` on the host runs the complete suite. Reimport Maven in IntelliJ after contract changes; `mvn generate-sources` regenerates Java interfaces and models.

Example lifecycle on a fresh database (the first ID is `1`; otherwise use the returned ID):

```sh
curl -i -X POST http://localhost:8080/matches \
  -H 'Content-Type: application/json' \
  -d '{"homeTeam":"Mexico","awayTeam":"Canada"}'

curl -X PATCH http://localhost:8080/matches/1/score \
  -H 'Content-Type: application/json' \
  -d '{"awayScore":5}'

curl http://localhost:8080/matches
# [{"id":1,"homeTeam":"Mexico","awayTeam":"Canada","homeScore":0,"awayScore":5}]

curl -X DELETE http://localhost:8080/matches/1

curl 'http://localhost:8080/matches/history?page=0&size=20'
```

## Database initialization

`src/main/resources/db/init.sql` is mounted into PostgreSQL's `/docker-entrypoint-initdb.d/`. It runs **only when the data volume is empty**. Spring SQL auto-initialization is disabled; Flyway/Liquibase are intentionally absent.

Changing `init.sql` does not migrate an existing volume. Apply reviewed SQL changes explicitly to an existing database, with a backup where needed. Never remove a populated volume just to apply schema changes unless its data is intentionally disposable.

```text
matches
  id, home_team, away_team, home_score, away_score, started_at, finished_at
  finished_at IS NULL means active

active_match_teams
  team_name, match_id
  unique case-insensitive country key across both team positions
```

Start inserts the match and both reservations in one transaction. Update/finish lock the match row; finish timestamps it and releases both reservations atomically. History remains immutable through the API.

## Structure and library usage

```text
src/main/java/org/rowny/
  Main.java          Spring Boot entry point
  api/               Generated API implementation, mapping, error handling
  domain/            Scoreboard library and repository port; Java-only
  persistence/       JDBC adapter and standalone in-memory adapter
  config/            Spring wiring and roster configuration
src/main/resources/
  openapi/           API contract and canonical FIFA team schema
  db/init.sql        First-time PostgreSQL schema
  application.yaml   Application settings
src/test/java/org/rowny/
  api/ domain/ persistence/ config/ support/
Dockerfile           Multi-stage application image
compose.yaml         App, PostgreSQL, health check, persistent volume
```

The library operates on structured, immutable `Match` records. `null` in its optional score arguments means omission, not an explicit JSON null (which is rejected by the API).

```java
var teams = new TeamCatalog(List.of("Mexico", "Canada"));
var scoreboard = new Scoreboard(new InMemoryMatchRepository(), teams);
var match = scoreboard.startMatch("Mexico", "Canada", null, null);
scoreboard.updateScore(match.id(), null, 5);
List<Match> summary = scoreboard.getSummary();
Match finalMatch = scoreboard.finishMatch(match.id());
MatchHistoryPage history = scoreboard.getHistory(0, 20);
```

Application wiring supplies all 211 FIFA teams from the generated enum and uses `JdbcMatchRepository`; the small in-memory catalogue above is only a standalone library example. Repository mutation methods own atomicity, so sharing a repository between scoreboard instances no longer depends on a single service lock. The in-memory adapter is synchronized but is not durable application storage.

## Team configuration

`scoreboard.teams: []` in `src/main/resources/application.yaml` enables every supported team. To restrict a tournament, replace it with a YAML list of exactly **48 distinct names** from `src/main/resources/openapi/teams.yaml`. Matching and duplicate detection are case-insensitive. Invalid size, unknown names, or duplicates fail application startup.

Use FIFA's exact spellings, such as `USA`, `Korea Republic`, `Côte d'Ivoire`, and `Türkiye`; no alternative names are inferred. Editing the supported FIFA catalogue requires regeneration and a rebuild. Configuring a restricted roster does not change the API enum.

## Reasoning and trade-offs

- **Library first:** `domain` must not depend on Spring, HTTP, or generated transport classes. The REST adapter maps to and from the core library.
- **One Maven module:** package separation is sufficient here; additional modules would add build complexity without improving these operations.
- **Replaceable storage:** the repository interface belongs to the domain. It originally exposed separate read/save/delete steps; history replaces these with atomic lifecycle operations so the JDBC adapter can guarantee transactions without Spring annotations in the domain.
- **JDBC rather than JPA:** explicit SQL for this small model, without entities or an ORM. The in-memory adapter remains useful for standalone library use and fast unit tests.
- **Immutable matches:** updating replaces a value rather than exposing mutable state to callers.
- **Database concurrency:** transactions, row locks, and a unique active-team reservation key replace the original single-service synchronization. Separate unique indexes on home and away columns would not prevent cross-position conflicts. Reservations are inserted in stable order to avoid reversed-pairing deadlocks.
- **Persisted ordering:** PostgreSQL records start/finish instants. Active ties use start time descending, then ID; history uses finish time descending, then ID. Score updates do not change start time. SQL widens score totals to `bigint` to avoid overflow.
- **Local team catalogue:** a canonical FIFA schema generates the API enum and supplies runtime validation through application wiring. There is no runtime external lookup or second manually maintained team list.
- **Structured summary:** listing reuses the same generated match model as the other endpoints. Clients can format display text themselves; IDs and scores remain separately accessible. This replaces the initial formatted-string response following the user's correction.
- **Retain rather than delete:** finishing releases teams but keeps scores/timestamps; repeated finish is now a conflict, not a missing record. `finished_at` encodes state without a redundant status column.
- **Offset pagination:** easy to consume and bounded to 100 records. Count and items share a repeatable-read snapshot per response; separate pages can shift as new matches finish. Cursor pagination and a retention policy are not added speculatively.
- **Init SQL only:** chosen instead of migrations by the user. Simple first-time setup, but schema evolution on existing data is explicitly manual.
- **Durable volume:** container recreation retains state; deleting or losing the volume still loses data. This is not a backup strategy or a production security setup.

## Implementation and verification

The process was: clarify each operation; document approved choices; define the contract; verify generation compatibility; implement the Java-only domain and in-memory adapter; add the REST adapter; test behavior; verify a clean build and a live executable JAR. Tests were added after the initial implementation; this is not claimed as a test-first process.

Then: correct listing to structured objects; resume history planning; approve JDBC, pagination, errors, Docker scope, and init-only SQL; create `feature/match-history`; implement atomic persistence/history; verify PostgreSQL tests and Compose container recreation. Initial in-memory data was not migrated automatically.

Verified with Java 21.0.10 and Maven 3.9.16:

- Latest `mvn -B clean verify`: **105 tests passed**, no failures, errors, or skips; executable JAR built from freshly generated sources. Earlier milestones: 75 initial tests, 76 after the structured-list correction.
- Domain: 32 tests; configuration: 9; in-memory adapter: 2.
- API: 50 tests against real PostgreSQL 18.6 via Testcontainers, including the unchanged structured active responses, history defaults/pagination/UTC timestamps, and finished-match `409` responses.
- JDBC adapter: 12 tests covering persistence across adapter recreation, start/finish rollback, database constraints, history ordering, widened totals, concurrent reversed starts, concurrent partial updates, concurrent finish/update, and consistent count/items during a concurrent finish.
- `docker compose config --quiet` and application image build passed. Live Compose smoke verified history/lifecycle/errors and IDE SQL access, then recreated both containers with the same volume: active/history data and ID allocation survived. Eight conflicting HTTP starts returned one `201` and seven `409`. Only the isolated verification project's resources were removed afterward.
- Domain sources also compiled with plain `javac --release 21`, without Spring or generated code on the classpath.
- Local catalogue compared against FIFA's published page: all 211 names match, in source order. Builds and tests do not require FIFA network access.

## Reference sources

- [FIFA member associations](https://inside.fifa.com/associations): local catalogue of 211 teams, using FIFA's published names (including territories and separate UK teams), retrieved during implementation.
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html).
- [OpenAPI 3.1.2 specification](https://spec.openapis.org/oas/v3.1.2.html): selected with user approval after Generator 7.25.0 rejected 3.2.1.
- [OpenAPI Spring generator](https://openapi-generator.tech/docs/generators/spring/).
- [PostgreSQL supported releases](https://www.postgresql.org/support/versioning/) and [official 18.6 Docker tag](https://hub.docker.com/v2/repositories/library/postgres/tags/18.6-alpine).

See [AI.md](AI.md) for AI usage, prompt history, and implementation artifacts.
