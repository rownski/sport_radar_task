# World Cup scoreboard

A Java 21 scoreboard library with a Spring Boot REST adapter. The project started as an empty Maven project generated in IntelliJ; the requirements and design below were clarified before implementation.

## Agreed requirements

- Support multiple matches in progress. A team cannot play against itself or participate in another active match, in either position.
- Start with independently optional scores, defaulting to `0–0`.
- Replace supplied scores on update; omitted scores remain unchanged. Corrections may decrease scores. Negative scores, explicit nulls, and empty updates are invalid.
- Finish removes a match and returns its structured final details. Missing or already-finished matches return `404`.
- Summary sorts by total score descending, then most recently started. Return structured match objects containing IDs, team names, and scores. Return `[]` when empty.
- Accept canonical FIFA team names case-insensitively, without aliases or whitespace normalization. Return canonical names.
- Enable all FIFA teams by default. An optional tournament list must contain exactly 48 distinct supported teams. The API contract still lists every supported team.

## Contract

The OpenAPI document in `src/main/resources/openapi/scoreboard.yaml` is the API source of truth. Maven generates the API interfaces, transport models, enums, and validation annotations into `target/`; generated Java is not handwritten or committed. Swagger UI is intentionally excluded.

Versions: Java **21**, Spring Boot **4.1.1**, OpenAPI Generator **7.25.0**, OpenAPI **3.1.2**. The initially selected OpenAPI 3.2.1 was rejected by the generator's parser; the user approved 3.1.2 rather than disabling contract validation. The generator warns that 3.1 support is beta; this contract's generated behavior is covered by API tests.

| Method | Path | Success |
| --- | --- | --- |
| POST | `/matches` | `201`, structured created match |
| PATCH | `/matches/{id}/score` | `200`, structured updated match |
| DELETE | `/matches/{id}` | `200`, structured final match |
| GET | `/matches` | `200`, ordered array of structured matches |

Errors: `400` for invalid input, `404` for an unknown match, and `409` when a team already participates in an active match.

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

## Build and run

Requires JDK 21 and Maven 3.6.3 or newer. First build requires access to Maven Central.

```sh
mvn clean verify
java -jar target/wc_score_board-1.0-SNAPSHOT.jar
```

The application listens on port `8080` by default. To change it, add `--server.port=8081` to the Java command. Reimport the Maven project in IntelliJ after changing the contract; `mvn generate-sources` regenerates its Java interfaces and models.

Example lifecycle on a fresh application (the first ID is `1`):

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
```

## Structure and library usage

```text
src/main/java/org/rowny/
  Main.java          Spring Boot entry point
  api/               Generated API implementation, mapping, error handling
  domain/            Scoreboard library and repository port; Java-only
  persistence/       In-memory repository adapter
  config/            Spring wiring and roster configuration
src/main/resources/
  openapi/           API contract and canonical FIFA team schema
  application.yaml   Application settings
src/test/java/org/rowny/
  api/ domain/ persistence/ config/
```

The library operates on structured, immutable `Match` records. `null` in its optional score arguments means omission, not an explicit JSON null (which is rejected by the API).

```java
var teams = new TeamCatalog(List.of("Mexico", "Canada"));
var scoreboard = new Scoreboard(new InMemoryMatchRepository(), teams);
var match = scoreboard.startMatch("Mexico", "Canada", null, null);
scoreboard.updateScore(match.id(), null, 5);
List<Match> summary = scoreboard.getSummary();
Match finalMatch = scoreboard.finishMatch(match.id());
```

Application wiring supplies all 211 FIFA teams from the generated enum; the small catalogue above is only a standalone library example. One `Scoreboard` must own each repository; sharing it between separate scoreboard instances bypasses the single-owner locking guarantee.

## Team configuration

`scoreboard.teams: []` in `src/main/resources/application.yaml` enables every supported team. To restrict a tournament, replace it with a YAML list of exactly **48 distinct names** from `src/main/resources/openapi/teams.yaml`. Matching and duplicate detection are case-insensitive. Invalid size, unknown names, or duplicates fail application startup.

Use FIFA's exact spellings, such as `USA`, `Korea Republic`, `Côte d'Ivoire`, and `Türkiye`; no alternative names are inferred. Editing the supported FIFA catalogue requires regeneration and a rebuild. Configuring a restricted roster does not change the API enum.

## Reasoning and trade-offs

- **Library first:** `domain` must not depend on Spring, HTTP, or generated transport classes. The REST adapter maps to and from the core library.
- **One Maven module:** package separation is sufficient here; additional modules would add build complexity without improving the four operations.
- **Replaceable storage:** the repository interface belongs to the domain; `persistence` provides an in-memory adapter. No database dependencies, JPA entities, or speculative database implementation are needed now.
- **Immutable matches:** updating replaces a value rather than exposing mutable state to callers.
- **Simple concurrency:** synchronize complete scoreboard operations, including team conflict checks and insertion. A concurrent map alone would not make that sequence atomic. This is deliberately single-instance; future database storage needs transactions and uniqueness constraints, not just a different map.
- **Ordering without clocks:** monotonically allocated IDs also establish start order. Updating never changes it. Use a widened total score to avoid integer overflow in sorting.
- **Local team catalogue:** a canonical FIFA schema generates the API enum and supplies runtime validation through application wiring. There is no runtime external lookup or second manually maintained team list.
- **Structured summary:** listing reuses the same generated match model as the other endpoints. Clients can format display text themselves; IDs and scores remain separately accessible. This replaces the initial formatted-string response following the user's correction.
- **Removal, not history:** finishing releases teams immediately. There is intentionally no finished-match archive.
- **No durable state:** matches and ID allocation reset on restart. IDs are not reused within one run, but are not globally unique across restarts.

## Implementation and verification

The process was: clarify each operation; document approved choices; define the contract; verify generation compatibility; implement the Java-only domain and in-memory adapter; add the REST adapter; test behavior; verify a clean build and a live executable JAR. Tests were added after the initial implementation; this is not claimed as a test-first process.

Verified with Java 21.0.10 and Maven 3.9.16:

- `mvn -B clean verify` after the structured-list correction: **76 tests passed**, no failures, errors, or skips; executable JAR built from freshly generated sources.
- Domain: 27 tests, including simultaneous team conflicts, concurrent partial updates, score corrections, immutable snapshots, finish behavior, and ordering/overflow.
- API: 39 tests using the generated interfaces and real Spring context, covering strict structured-list responses, ordering, empty arrays, payload validation, case-insensitive and Unicode names, errors, stable IDs, and lifecycle.
- Configuration: 9 tests covering all 211 schema/enum names and valid/invalid restricted rosters; repository: 1 test covering replacement, snapshots, deletion, and ID allocation.
- Executable-JAR live HTTP smoke after the correction: structured arrays, stable IDs, score/start ordering, score corrections, removal, and empty `[]`. The initial smoke also covered validation, conflicts, and eight concurrent conflicting starts (one `201`, seven `409`). Both temporary servers were stopped afterward.
- Domain sources also compiled with plain `javac --release 21`, without Spring or generated code on the classpath.
- Local catalogue compared against FIFA's published page: all 211 names match, in source order. Builds and tests do not require FIFA network access.

Match-history/PostgreSQL planning is paused at the user's request; this correction does not change storage or finish behavior.

## Reference sources

- [FIFA member associations](https://inside.fifa.com/associations): local catalogue of 211 teams, using FIFA's published names (including territories and separate UK teams), retrieved during implementation.
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html).
- [OpenAPI 3.1.2 specification](https://spec.openapis.org/oas/v3.1.2.html): selected with user approval after Generator 7.25.0 rejected 3.2.1.
- [OpenAPI Spring generator](https://openapi-generator.tech/docs/generators/spring/).

See [AI.md](AI.md) for AI usage, prompt history, and implementation artifacts.
