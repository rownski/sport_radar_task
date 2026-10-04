# AI usage record

## Starting point and tools

The user created an empty Java 21 Maven project in IntelliJ IDEA. No AI tools were used before this conversation. Existing IDE metadata is user work and is left untouched.

This implementation uses OpenCode with GPT-6.1 Sol (OpenAI) for requirements clarification, design suggestions, documentation, code, tests, and investigating tool failures. OpenCode file/search/patch tools inspect and change the repository; shell tools run Java, Maven, and read-only source extraction; web-fetch tools consult documentation. No subagents were used.

OpenAPI Generator, rather than the language model, generates API interfaces, request/response models, enums, and schema-driven validation. Maven and Java verify the result. The user selected the requirements and approved the plan; approval is not a claim of a completed human code audit.

## User prompt history

These are the task prompts available in this conversation, in order. Harness mode reminders are excluded. No earlier prompts are invented.

### 1. Documentation request

> Firstly I need to write down my assumptions, reasoning, trade offs in README.md for the whole process of writing this app.
> Create AI.md file and in that file write down how AI tools were used, include prompt history, any artifact that guided this implementation

### 2. Starting point and constraints

```text
Now there is only empty maven java 21 project which I want to implement into rest api app. I have generated it in Intellij -> there was nothing yet with AI.
Don't make assumptions when something is not clear, ask. Choose code over prose. The simpler the better approach. Use a structure for the project as it would be a bigger one with separation on api, domain, persistance, resources.
Plan of the app:
Rest api app. I would want to use open api generator and api doc in up to date version as a source of truth. Don't generate anything that can be generated via tools like openapi generator.
Endpoints must serve:
- start a new match
- update score
- finish match
- get summary of matches in progress
For each endpoint ask about any uncertainty and what approach I like.
Right now no persistance of those matches, but during design lets design with approach of persistance might be added.
```

### 3. Core requirements and initial decisions

```text
Requirements are:
1. Implement a scoreboard library, that supports multiple simultaneous matches. Core operations: 1) start a new match, 2) update the score, 3) finish a match, 4) get a summary of matches in progress -> return the matches in progress ordered by: Total score (descending), if tied most recently started match.
2. spring boot would be my choice too
3. Yes
4. no need for swagger Ui In my IDE I can see that well
5. Home team -> name string and away team string name -> names should be unique, as it is a World Cup only Countries, can we verify that? There can't be a situation where there are two matches with the same country.
6. No need for UUID, there is not that many matches that can be played
7. By default every match start 0-0 at the beginning but it should be allowed to provide score if the client is late with update.
8. yes, yes, yes
```

### 4. Teams and score updates

```text
1. all FIFA national teams lets configure, but with possibility to choose the 48 countries as that much countries play in wc
2. yep
3. In the API spec we can provide a list so the client can know what to choose, so I'd vote for exact name case insensitive
4. yes
5. it can be omited.
6. Replace as the goal can be overturned
7. Score has to be non-negative, can't allow negatives
8. Partial is alright
```

### 5. Configuration, finish, and summary

```text
1. it needs to be exactly 48
2. all supported, no need to restrict for this
3. reject
4. remove
5. 404
6. final score as return
7. In requirements there is this format: home score - away score -> Mexico 0 - Canada 5, [] for empty is ok
```

### 6. IDs in summaries and HTTP errors

```text
Literally an array of formatted strings but with id -> "1. Mexico 0 - Canada 5" is what we go with for now if a user loses id to update score, they can list all.
All fifa teams by default
eror codes are alright
```

### 7. Final contract approval

```text
1. Let's go with structured final match details, formatted string is something a client can build if needed.
2. yes
3. yes
4. yes
Versions are accepted
```

The numbered approvals refer to: structured finish response; POST/PATCH/DELETE/GET routes; optional configuration of exactly 48 distinct teams; rejecting explicit null scores while allowing omission. Accepted versions: Spring Boot 4.1.1, OpenAPI Generator 7.25.0, OpenAPI 3.2.1, subject to generation compatibility verification.

### 8. Implementation authorization

> go

### 9. Generator compatibility decision

When OpenAPI Generator 7.25.0 rejected the OpenAPI 3.2.1 document, the assistant asked whether to use 3.1.2 or investigate alternative tooling. The user selected:

> Use 3.1.2 (Recommended)

Spring Boot and generator versions remain unchanged. No schema validation bypass was approved or used.

### 10. IntelliJ ignore rules

> add idea files to gitignore

### 11. Other IDE metadata

> what about other files, should .classpath be included?

### 12. Ignore-rule authorization

> add it then

The assistant added IntelliJ metadata patterns, then `.classpath`, `.project`, and `.settings/` to `.gitignore`. The metadata files themselves were not removed.

### 13. Proposed history/database feature

> I need one last task. I need to implement one extra functionality. I think about match history that is accessible even after finished matches. I want a postgresql docker database.

The assistant asked about persistence scope, database access, history responses, timestamps, pagination, Docker scope, and finished-match behavior. These choices were not settled and the feature was not implemented.

### 14. Pause history and correct listing

> Put the planning of this feature for now, there is a mistake, I have asked for a structured response in listing

The assistant proposed changing `GET /matches` to an array of the existing `MatchDetails` objects, retaining ordering and empty `[]`, and updating the contract, generated interface, mapping, tests, and documentation.

### 15. Correction authorization

> fix

## Suggestions accepted, changed, or excluded

- Accepted: Spring Boot; API-first Maven generation; independent domain library; replaceable in-memory repository; structured start/update/finish responses.
- Clarified by user: initial scores may be supplied; partial replacements may decrease scores; team conflicts are forbidden; finishing removes data; a restricted roster must have exactly 48 entries.
- Initially selected: formatted summary strings with stable match IDs. Superseded by prompt 14: listing now returns structured `MatchDetails` objects and leaves display formatting to clients.
- Changed after tool verification and explicit user approval: contract version 3.2.1 to 3.1.2 because the released generator parser rejected 3.2.1.
- Excluded: Swagger UI, UUIDs, external runtime team lookups, historical match storage, and handwritten transport models.
- Suggested implementation details in the approved plan: immutable matches, single-instance synchronization, monotonic ID-based start ordering, one module with package boundaries, domain and API tests.

## Artifacts guiding implementation

- The original IntelliJ scaffold: `pom.xml` and the empty `src/main/java/org/rowny/Main.java`.
- The requirements and approvals above; the conversational plan (no separate plan file).
- `README.md`: confirmed behavior, design rationale, and trade-offs, updated as work is verified.
- `src/main/resources/openapi/scoreboard.yaml`: API source of truth.
- FIFA's [member associations page](https://inside.fifa.com/associations): its embedded `__NEXT_DATA__` provides 211 unique canonical team names. Read-only extraction supplies the local schema, avoiding an ISO-country approximation.
- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) and the published [4.1.1 parent POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/4.1.1/spring-boot-starter-parent-4.1.1.pom).
- [Generator release metadata](https://repo.maven.apache.org/maven2/org/openapitools/openapi-generator-maven-plugin/maven-metadata.xml), [7.25.0 Spring generator options](https://raw.githubusercontent.com/OpenAPITools/openapi-generator/v7.25.0/docs/generators/spring.md), and [OpenAPI 3.2.1](https://spec.openapis.org/oas/v3.2.1.html).
- Generated files under `target/generated-sources/openapi/` and automated tests are implementation/verification artifacts, not manually authored API definitions.
- `src/main/resources/openapi/teams.yaml`: extracted canonical catalogue, checked against the FIFA page and against the generated enum. The Wikipedia FIFA-code list was also consulted, but not used as the catalogue source once FIFA's own embedded data was located.
- Test source: `ScoreboardTest`, `MatchesApiTest`, `ScoreboardConfigurationTest`, and `InMemoryMatchRepositoryTest`. These were written after the initial implementation, not presented as test-driven development.
- Maven test reports: `target/surefire-reports/`; executable artifact: `target/wc_score_board-1.0-SNAPSHOT.jar`. Both are build output and excluded from Git.

## Verification log

- Environment inspected: Java 21.0.10, Maven 3.9.16.
- FIFA page extraction found 211 unique names. An initial extraction treated a content list as an object; recursive traversal corrected the read-only extraction.
- `mvn -B generate-sources` failed for OpenAPI 3.2.1: the bundled parser rejected the version and fell back to Swagger 2 validation (`openapi is unexpected`, `swagger is missing`). User approved switching the contract to 3.1.2, not suppressing validation.
- After the approved change, `mvn -B generate-sources` and `mvn -B compile` succeeded. Generated sources were inspected for case-insensitive enum parsing, defaults, schema constraints, and `@JsonSetter(nulls = Nulls.FAIL)`; they were not manually edited.
- First `mvn -B test`: 72 tests passed. Further checks added Unicode API names, missing positive IDs, schema/enum round-trips, error media types, and stronger assertions for startup failures.
- Initial complete `mvn -B clean verify`: **75 tests, 0 failures, 0 errors, 0 skipped**. Fresh generation, compilation, test execution, and executable-JAR packaging succeeded.
- The Java-only domain compiled separately using `javac --release 21 -d target/domain-library-check src/main/java/org/rowny/domain/*.java`, with no framework dependencies on the classpath.
- A foreground Python smoke harness launched the executable JAR bound to localhost on an ephemeral port, checked all four endpoints, canonical names, validation errors, removal/404, and stable ID summaries, then attempted eight conflicting starts concurrently: exactly one succeeded and seven returned `409`. The harness terminated its own server afterward.
- A read-only comparison against FIFA's embedded data confirmed all 211 local names, including order. Network access is not part of the application or automated tests.
- `git diff --check` passed during verification. Existing IDE metadata and pre-existing staged files were not staged, discarded, or committed by the assistant.
- Remaining non-failing tool warnings: generator's OpenAPI 3.1 support is labelled beta, generated Spring nullable annotations trigger deprecation notices, and the default Spring test/Mockito integration self-attaches a Java agent. Generated output and framework test machinery were not patched to conceal warnings.
- Listing correction: updated the OpenAPI response items to reference `MatchDetails`, reused existing object mapping, removed string formatting, and changed API assertions to strict structured-array comparisons. Added an explicit empty-list response test.
- Correction verification: `mvn -B clean verify` regenerated the API as `ResponseEntity<List<MatchDetails>>` and passed **76 tests, 0 failures, 0 errors, 0 skipped**, then packaged the executable JAR. No generated sources were edited manually.
- A live HTTP smoke of the corrected JAR verified structured match arrays, stable IDs, score/start-order sorting, partial score corrections, removal, and empty `[]`; its temporary localhost server was terminated afterward. `git diff --check` also passed. The history/PostgreSQL feature remains paused and storage is unchanged.

## Review boundaries

The assistant inspected the generated code, source changes, configuration behavior, and test results. This is AI review plus automated verification, not evidence of a completed independent human review or production certification. The user approved requirements and the version adjustment. Authentication, distributed deployment, database storage, and historical match records are outside the implemented scope.
