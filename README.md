# Caseware Template Publish Worker

Bounded Java implementation for the Caseware Staff Java Developer take-home exercise. The service accepts Product Template publication events, discovers affected Engagement Files from a lightweight catalog, creates durable work items, and invokes a capacity-constrained downstream service.

The local implementation uses Java 21, Spring Boot 4.1, Maven, Lombok, Spring Data JPA with Hibernate, and H2. H2 runs as a file database for local execution and as an in-memory database in tests.

## Design goals

- At-least-once event delivery is safe. `publicationId` is the event idempotency key, and `(publicationId, fileId)` is unique for generated work.
- Fan-out is paginated with keyset pagination, so a publication does not load all affected files into memory.
- Work is durable before the downstream call starts.
- A configurable semaphore caps concurrent one-minute downstream calls.
- Failed calls use exponential backoff and move to `DEAD_LETTER` after the configured attempt limit.
- Expiring leases recover tasks and fan-out pages left in progress after a worker crash.
- Every downstream retry carries the same `<publicationId>:<fileId>` idempotency key.
- The catalog contains metadata only; no confidential working-paper content is copied into this service.

## Project documents

- [Architecture solution (PDF)](docs/JHONNATAN_DUQUE_RAMOS_Architecture_Solution.pdf)
- [Architecture design (Markdown)](docs/architecture.md)
- [Original take-home test](docs/Staff_Java_Developer_-_Take-Home_Test.pdf)
- [Postman API collection](docs/caseware-api.postman_collection.json)
- [Interactive diagram index](docs/diagrams/README.md)
- [Ports and adapters architecture](docs/diagrams/hexagonal-architecture.html)
- [Publication and fan-out sequence](docs/diagrams/publication-fanout-sequence.html)
- [Downstream dispatch, capacity, and retries](docs/diagrams/downstream-dispatch-workflow.html)
- [Fan-out task lifecycle](docs/diagrams/task-lifecycle.html)

The architecture documents cover scale calculations, production evolution, data residency, and the human-readable summary strategy. Import the Postman collection to run the complete API example flow against `http://localhost:8080`.

## Run the project

Requirements: Java 21 or newer. The Maven wrapper downloads Maven 3.9.12 on first use.

```sh
./mvnw test
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. H2 persists under `./data`, and its development console is available at `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:file:./data/caseware`, user `sa`, and an empty password.

Health and metrics are exposed through `/actuator/health` and `/actuator/metrics`. Worker settings are under `worker` in `src/main/resources/application.yml`.

On startup, the application loads an idempotent sample dataset into H2: five Engagement Files and the `sample-publication-audit-ca-v7` publication. Three files are eligible for the update, one is already on `v7`, and one belongs to another market. Disable this behavior with `application.sample-data.enabled=false`.

Inspect the sample publication after startup:

```sh
curl http://localhost:8080/api/v1/template-publications/sample-publication-audit-ca-v7
```

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

The code follows ports and adapters (hexagonal architecture):

```text
domain/                  Framework-independent business types
application/port/in/     Use cases exposed to inbound adapters
application/port/out/    Contracts required from infrastructure
application/service/     Use-case implementations
adapter/in/web/          REST controllers and request/response models
adapter/in/scheduler/    Scheduled worker triggers
adapter/out/persistence/ Spring Data JPA and H2
adapter/out/downstream/  Downstream service implementation
config/                  Spring composition root
```

Dependencies point inward. Controllers and schedulers invoke input ports; application services depend on output ports; JPA entities and repositories remain inside the persistence adapter. The Spring composition root connects concrete adapters to use cases. Lombok generates constructors for dependency injection and the routine accessors and builders required by JPA entities.

`TemplatePublishFanOutService` claims one publication page in a database transaction. It inserts missing tasks and advances the scan cursor atomically. A crash before commit repeats the page safely; a crash after commit resumes from the next cursor.

The persistence layer consists of three Spring Data `JpaRepository` interfaces marked with `@Repository`. JPQL queries select eligible files and acquire pessimistic locks when claiming publications or tasks. Hibernate manages schema creation for this self-contained exercise.

`DownstreamTaskDispatchService` claims durable tasks and submits only as many calls as its semaphore allows. The limit is local to an application instance. In production, instance count multiplied by `worker.max-concurrency` must remain below the downstream team's agreed global capacity; a shared rate limiter or queue concurrency setting should enforce that global contract.

H2 keeps the exercise self-contained and makes persistence behavior testable. It is not the proposed multi-region production database: it cannot provide shared durable state across a horizontally scaled fleet. The production mapping in the architecture document uses one regional durable store and queue per residency boundary while retaining the same ports and idempotency rules.

The implementation tests the API and validation rules, configuration guards, repository behavior, pagination, duplicate and conflicting events, bounded concurrency, lease recovery, retry idempotency, and dead-letter behavior. JaCoCo enforces 100% line coverage and at least 90% branch coverage:

```sh
./mvnw test
./mvnw verify
```

The HTML coverage report is generated at `target/site/jacoco/index.html`.

## Git Flow

The repository uses `main` for stable releases and `develop` for integration. Create new work from `develop` using `feature/<name>` or `bugfix/<name>`. Create `release/<version>` from `develop`, and create urgent `hotfix/<name>` branches from `main`. Releases and hotfixes merge back into both long-lived branches. Release tags use the `v1.0.0` format.

The current implementation branch is `feature/template-publish-worker`.
