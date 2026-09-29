# Graph Report - caseware  (2026-09-28)

## Corpus Check
- 75 files · ~358,425 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 5 file(s) not represented in the graph (top: (none) 3, .properties 1, .cmd 1)

## Summary
- 506 nodes · 1416 edges · 18 communities (17 shown, 1 thin omitted)
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 189 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Configuration and Test Support
- REST API and Validation
- JPA Repositories and Transactions
- Architecture and Requirements
- Application Service Tests
- Scheduling and Transaction Adapters
- Integration and Concurrency Tests
- JPA Entities
- Fan-out Task Dispatch
- Publication Persistence
- Dispatch Workflow Diagram
- Task Lifecycle Diagram
- Spring Boot Entry Point
- Engagement File Persistence
- Ports and Adapters Diagram
- Publication Sequence Diagram
- Maven Wrapper
- Maven Project Metadata

## God Nodes (most connected - your core abstractions)
1. `TemplatePublishWorkerIntegrationTest` - 32 edges
2. `FanOutTaskStorePort` - 22 edges
3. `TaskLease` - 21 edges
4. `TemplatePublicationJpaEntity` - 20 edges
5. `EngagementFile` - 20 edges
6. `TemplatePublication` - 20 edges
7. `PersistenceAdapterTest` - 19 edges
8. `JpaPublicationAdapter` - 18 edges
9. `EngagementFileCommand` - 18 edges
10. `TemplatePublicationCommand` - 18 edges

## Surprising Connections (you probably didn't know these)
- `Template-publish Fan-out Worker Requirement` --rationale_for--> `Caseware Template Publish Worker`  [INFERRED]
  docs/Staff_Java_Developer_-_Take-Home_Test.pdf → README.md
- `Dependency Inversion Through Ports` --semantically_similar_to--> `Ports and Adapters Architecture`  [INFERRED] [semantically similar]
  docs/diagrams/hexagonal-architecture.html → README.md
- `Transactional Fan-out Page` --semantically_similar_to--> `Durable Paginated Fan-out`  [INFERRED] [semantically similar]
  docs/diagrams/publication-fanout-sequence.html → README.md
- `Production Capacity Controls` --conceptually_related_to--> `Capacity-constrained Dispatch`  [INFERRED]
  docs/JHONNATAN_DUQUE_RAMOS_Architecture_Solution.pdf → README.md
- `Regional Event-driven Projection` --semantically_similar_to--> `Regional Metadata Projection`  [INFERRED] [semantically similar]
  docs/JHONNATAN_DUQUE_RAMOS_Architecture_Solution.pdf → docs/architecture.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Bounded Worker Implementation** — readme_caseware_template_publish_worker, readme_durable_fan_out, readme_capacity_constrained_dispatch, readme_h2_local_persistence [EXTRACTED 1.00]
- **Production Pending-update Architecture** — docs_architecture_regional_metadata_projection, docs_architecture_version_dag, docs_architecture_transactional_outbox, docs_architecture_regional_residency, docs_architecture_human_readable_summaries [EXTRACTED 1.00]
- **Worker Execution Flow** — docs_diagrams_publication_fanout_sequence_transactional_page, docs_diagrams_downstream_dispatch_workflow_lease_retry_dead_letter, docs_diagrams_task_lifecycle_task_states [INFERRED 0.95]
- **Successful Dispatch Path** — docs_diagrams_downstream_dispatch_workflow_capacity_available, docs_diagrams_downstream_dispatch_workflow_claim_task, docs_diagrams_downstream_dispatch_workflow_submit_to_executor, docs_diagrams_downstream_dispatch_workflow_evaluate_update, docs_diagrams_downstream_dispatch_workflow_succeeded [EXTRACTED 1.00]
- **Failure Recovery Path** — docs_diagrams_downstream_dispatch_workflow_evaluate_update, docs_diagrams_downstream_dispatch_workflow_downstream_failure, docs_diagrams_downstream_dispatch_workflow_retry, docs_diagrams_downstream_dispatch_workflow_dead_letter [EXTRACTED 1.00]
- **Dispatch Safeguards** — docs_diagrams_downstream_dispatch_workflow_local_capacity_limit, docs_diagrams_downstream_dispatch_workflow_transactional_lease, docs_diagrams_downstream_dispatch_workflow_stable_idempotency_key, docs_diagrams_downstream_dispatch_workflow_exponential_backoff [INFERRED 0.95]
- **Inbound Ports and Adapters Flow** — docs_diagrams_hexagonal_architecture_rest_adapters, docs_diagrams_hexagonal_architecture_schedulers, docs_diagrams_hexagonal_architecture_input_ports, docs_diagrams_hexagonal_architecture_application_services [EXTRACTED 1.00]
- **Persistence Adapter Flow** — docs_diagrams_hexagonal_architecture_output_ports, docs_diagrams_hexagonal_architecture_jpa_adapters, docs_diagrams_hexagonal_architecture_h2 [EXTRACTED 1.00]
- **Downstream Integration Flow** — docs_diagrams_hexagonal_architecture_output_ports, docs_diagrams_hexagonal_architecture_downstream_adapter, docs_diagrams_hexagonal_architecture_engagement_service [EXTRACTED 1.00]
- **Idempotent Registration Flow** — docs_diagrams_publication_fanout_sequence_api_client, docs_diagrams_publication_fanout_sequence_controller, docs_diagrams_publication_fanout_sequence_registration, docs_diagrams_publication_fanout_sequence_publications [EXTRACTED 1.00]
- **Paginated Fan-out Flow** — docs_diagrams_publication_fanout_sequence_scheduler, docs_diagrams_publication_fanout_sequence_fanout_service, docs_diagrams_publication_fanout_sequence_publications, docs_diagrams_publication_fanout_sequence_file_catalog, docs_diagrams_publication_fanout_sequence_task_store [EXTRACTED 1.00]
- **Successful Task Lifecycle** — docs_diagrams_task_lifecycle_pending, docs_diagrams_task_lifecycle_processing, docs_diagrams_task_lifecycle_succeeded [EXTRACTED 1.00]
- **Recoverable Retry Loop** — docs_diagrams_task_lifecycle_processing, docs_diagrams_task_lifecycle_retry, docs_diagrams_task_lifecycle_next_attempt, docs_diagrams_task_lifecycle_recoverable_error [EXTRACTED 1.00]
- **Terminal Task Outcomes** — docs_diagrams_task_lifecycle_succeeded, docs_diagrams_task_lifecycle_dead_letter, docs_diagrams_task_lifecycle_terminal_success, docs_diagrams_task_lifecycle_terminal_failure [EXTRACTED 1.00]

## Communities (18 total, 1 thin omitted)

### Community 0 - "Configuration and Test Support"
Cohesion: 0.07
Nodes (49): assertthat, assertthatthrownby, atomicinteger, clock, concurrenthashmap, countdownlatch, createpublicationresult, duration (+41 more)

### Community 1 - "REST API and Validation"
Cohesion: 0.05
Nodes (45): com.caseware.interview.adapter.in.web.data.request.EngagementFileRequest, com.caseware.interview.adapter.in.web.data.request.TemplatePublicationRequest, com.caseware.interview.adapter.in.web.data.response.PublicationStatusResponse, constraintviolation, map, mock, org.junit.jupiter.api.extension.ExtendWith, org.mockito.junit.jupiter.MockitoExtension (+37 more)

### Community 2 - "JPA Repositories and Transactions"
Cohesion: 0.08
Nodes (35): any, anylist, argumentcaptor, dataintegrityviolationexception, enummap, eq, hashset, instant (+27 more)

### Community 3 - "Architecture and Requirements"
Cohesion: 0.07
Nodes (36): Backfill and Reconciliation, Auditable Human-readable Summaries, Operational SLOs, Pending Product Template Updates Architecture, Regional Metadata Projection, Regional Data Residency, Transactional Outbox, Template Version DAG (+28 more)

### Community 4 - "Application Service Tests"
Cohesion: 0.12
Nodes (5): org.junit.jupiter.api.Test, Override, Override, ApplicationServiceTest, WorkerConfigurationTest

### Community 5 - "Scheduling and Transaction Adapters"
Cohesion: 0.12
Nodes (16): lombok.RequiredArgsConstructor, nonnull, org.springframework.boot.ApplicationArguments, org.springframework.boot.ApplicationRunner, org.springframework.boot.autoconfigure.condition.ConditionalOnProperty, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, org.springframework.transaction.support.TransactionOperations (+8 more)

### Community 6 - "Integration and Concurrency Tests"
Cohesion: 0.14
Nodes (3): java.util.function.BooleanSupplier, Override, RecordingClient

### Community 7 - "JPA Entities"
Cohesion: 0.19
Nodes (21): accesslevel, column, enumerated, enumtype, fetchtype, generatedvalue, generationtype, id (+13 more)

### Community 8 - "Fan-out Task Dispatch"
Cohesion: 0.16
Nodes (4): Override, JpaFanOutTaskAdapter, Override, TaskLease

### Community 9 - "Publication Persistence"
Cohesion: 0.23
Nodes (5): Override, JpaPublicationAdapter, CreatePublicationResult, TemplatePublication, PersistenceAdapterTest

### Community 10 - "Dispatch Workflow Diagram"
Cohesion: 0.13
Nodes (15): Capacity Available, Claim Task, Dead Letter, Downstream Failure, Evaluate Update, Exponential Backoff, Downstream Dispatch, Capacity, and Retries Diagram, Local Semaphore Capacity Limit (+7 more)

### Community 11 - "Task Lifecycle Diagram"
Cohesion: 0.15
Nodes (13): Attempt Count, Dead Letter, Durable Task, Fan-out Task Lifecycle Diagram, Processing Lease, Next Attempt Time, Pending, Processing (+5 more)

### Community 12 - "Spring Boot Entry Point"
Cohesion: 0.23
Nodes (8): mockedstatic, mockstatic, org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, org.springframework.scheduling.annotation.EnableScheduling, springapplication, InterviewApplication, InterviewApplicationTest

### Community 13 - "Engagement File Persistence"
Cohesion: 0.30
Nodes (4): Override, JpaEngagementFileAdapter, Override, EngagementFile

### Community 14 - "Ports and Adapters Diagram"
Cohesion: 0.22
Nodes (11): Application Services, Downstream Adapter, Engagement Service, H2 Database, Ports and Adapters Architecture Diagram, Input Ports, JPA Adapters, Output Ports (+3 more)

### Community 15 - "Publication Sequence Diagram"
Cohesion: 0.20
Nodes (11): API Client, Controller, Fan-out Service, File Catalog, Idempotent Publication Registration, Publication and Fan-out Sequence Diagram, Paginated Fan-out, Publications Store (+3 more)

### Community 16 - "Maven Wrapper"
Cohesion: 0.38
Nodes (8): mvnw script, clean(), die(), exec_maven(), hash_string(), set_java_home(), trim(), verbose()

## Knowledge Gaps
- **43 isolated node(s):** `com.caseware:interview`, `CREATED`, `DUPLICATE`, `PENDING`, `FANNING_OUT` (+38 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 94 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TemplatePublishWorkerIntegrationTest` connect `Configuration and Test Support` to `Fan-out Task Dispatch`, `REST API and Validation`, `JPA Repositories and Transactions`, `Integration and Concurrency Tests`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **Why does `TaskStatus` connect `JPA Repositories and Transactions` to `Configuration and Test Support`, `JPA Entities`?**
  _High betweenness centrality (0.029) - this node is a cross-community bridge._
- **Why does `PublicationStatus` connect `REST API and Validation` to `Configuration and Test Support`, `JPA Repositories and Transactions`, `Application Service Tests`, `JPA Entities`, `Publication Persistence`?**
  _High betweenness centrality (0.026) - this node is a cross-community bridge._
- **What connects `com.caseware:interview`, `CREATED`, `DUPLICATE` to the rest of the system?**
  _43 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Configuration and Test Support` be split into smaller, more focused modules?**
  _Cohesion score 0.06740506329113924 - nodes in this community are weakly interconnected._
- **Should `REST API and Validation` be split into smaller, more focused modules?**
  _Cohesion score 0.050949367088607596 - nodes in this community are weakly interconnected._
- **Should `JPA Repositories and Transactions` be split into smaller, more focused modules?**
  _Cohesion score 0.08246753246753247 - nodes in this community are weakly interconnected._