# Caseware Template Publish Worker

Bounded Java implementation for the Caseware Staff Java Developer take-home exercise. The service accepts Product Template publication events, discovers affected Engagement Files from a lightweight catalog, creates durable work items, and invokes a capacity-constrained downstream service.

The local implementation uses Java 21, Spring Boot 4.1, Maven, JDBC, and H2. H2 runs as a file database for local execution and as an in-memory database in tests.

## Design goals

- At-least-once event delivery is safe. `publicationId` is the event idempotency key, and `(publicationId, fileId)` is unique for generated work.
- Fan-out is paginated with keyset pagination, so a publication does not load all affected files into memory.
- Work is durable before the downstream call starts.
- A configurable semaphore caps concurrent one-minute downstream calls.
- Failed calls use exponential backoff and move to `DEAD_LETTER` after the configured attempt limit.
- Expiring leases recover tasks and fan-out pages left in progress after a worker crash.
- Every downstream retry carries the same `<publicationId>:<fileId>` idempotency key.
- The catalog contains metadata only; no confidential working-paper content is copied into this service.

See [docs/architecture.md](docs/architecture.md) for the system design, scale calculations, production evolution, data residency, and human-readable summary strategy.

## Run the project

Requirements: Java 21 or newer. The Maven wrapper downloads Maven 3.9.12 on first use.

```sh
./mvnw test
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. H2 persists under `./data`, and its development console is available at `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:file:./data/caseware`, user `sa`, and an empty password.

Health and metrics are exposed through `/actuator/health` and `/actuator/metrics`. Worker settings are under `worker` in `src/main/resources/application.yml`.

## Try the flow

Create two lightweight Engagement File catalog entries:

```sh
curl -X PUT http://localhost:8080/api/v1/engagement-files/file-001 \
  -H 'Content-Type: application/json' \
  -d '{"firmId":"firm-1","templateId":"audit-ca","templateVersion":"v5","market":"CA","region":"CANADA"}'

curl -X PUT http://localhost:8080/api/v1/engagement-files/file-002 \
  -H 'Content-Type: application/json' \
  -d '{"firmId":"firm-1","templateId":"audit-ca","templateVersion":"v6","market":"CA","region":"CANADA"}'
```

Publish a new template version:

```sh
curl -i -X POST http://localhost:8080/api/v1/template-publications \
  -H 'Content-Type: application/json' \
  -d '{"publicationId":"pub-audit-ca-v7","templateId":"audit-ca","targetVersion":"v7","market":"CA","publishedAt":"2026-09-28T15:00:00Z"}'
```

Submitting the exact request again returns `202 Accepted` with `X-Idempotency-Result: DUPLICATE`. Reusing the publication id with a different payload returns `409 Conflict`.

Inspect progress:

```sh
curl http://localhost:8080/api/v1/template-publications/pub-audit-ca-v7
```

The bundled downstream adapter logs successful calls immediately. A production adapter would apply a network timeout shorter than the task lease and send the provided idempotency key to the owning service.

## Main implementation choices and trade-offs

`TemplatePublishFanOutWorker` claims one publication page in a database transaction. It inserts missing tasks and advances the scan cursor atomically. A crash before commit repeats the page safely; a crash after commit resumes from the next cursor.

`DownstreamTaskDispatcher` claims durable tasks and submits only as many calls as its semaphore allows. The limit is local to an application instance. In production, instance count multiplied by `worker.max-concurrency` must remain below the downstream team's agreed global capacity; a shared rate limiter or queue concurrency setting should enforce that global contract.

H2 keeps the exercise self-contained and makes persistence behavior testable. It is not the proposed multi-region production database: it cannot provide shared durable state across a horizontally scaled fleet. The production mapping in the architecture document uses one regional durable store and queue per residency boundary while retaining the same ports and idempotency rules.

The implementation tests pagination, duplicate events, conflicting event ids, bounded concurrency, and retry idempotency:

```sh
./mvnw test
```

## Git Flow

The repository uses `main` for stable releases and `develop` for integration. Create new work from `develop` using `feature/<name>` or `bugfix/<name>`. Create `release/<version>` from `develop`, and create urgent `hotfix/<name>` branches from `main`. Releases and hotfixes merge back into both long-lived branches. Release tags use the `v1.0.0` format.

The current implementation branch is `feature/template-publish-worker`.
