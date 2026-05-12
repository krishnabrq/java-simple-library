# Journey

Concept-keyed reference of what's been learned and decided. Not chronological. Each section is a dense fact block — consult on demand, no need to read linearly.

---

## Spring Boot bootstrap

- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- Entry: a class with `@SpringBootApplication` calling `SpringApplication.run(...)`.

## REST controllers

- `@RestController` = `@Controller` + `@ResponseBody`. Method return → Jackson → JSON.
- `@GetMapping("/x")` etc.; relative to class-level `@RequestMapping`.
- `@PathVariable` name must match the path placeholder, or pass the name explicitly.

## API versioning

| Option                                                  | When                                  |
| ------------------------------------------------------- | ------------------------------------- |
| Class-level `@RequestMapping("/api/v1/books")` (chosen) | one or few controllers                |
| `WebMvcConfigurer.configurePathMatch` + `addPathPrefix` | many controllers; single config point |
| Header / query / subdomain                              | exist; URI versioning is standard     |

## Content negotiation

- Default Whitelabel Error Page is HTML for browsers (`Accept: text/html`). JSON clients (`Accept: application/json`) already get JSON.
- To force JSON for all clients: handle the relevant exceptions in `@RestControllerAdvice`.

## Package structure

- Package-by-feature (`books/`, `authors/`, `loans/`) over package-by-layer.
- Within a feature: use package-private to hide internals from other features.
- Shared infra: `storage/` (S3), `messaging/` (queues), `config/` (`@Configuration` classes), `common/` (post-second-consumer).
- Don't pre-create empty packages.

## Externalised config (.env)

- Spring Boot natively supports placeholder substitution in properties files: `${VAR:default}`. Resolves against any registered `PropertySource` — OS env vars, JVM args, imported files.
- `.env` files are read with `spring.config.import=optional:file:./.env[.properties]`.
  - `optional:` — missing file is fine (CI sets env vars directly).
  - `file:./.env` — relative to the JVM's working directory.
  - `[.properties]` — extension hint; tells Spring to parse the file as Java `.properties` format (KEY=VALUE) even though it has no `.properties` suffix.
- **Property-source precedence (high → low)**: command-line args > JVM system props > OS env vars > imported config files (`.env`) > `application.properties`. So `DB_NAME=library_test ./gradlew test` overrides whatever's in `.env` without editing it.
- `.env` format is plain `.properties`: no quoting, no `$VAR` shell expansion, no `export`. Special chars (`@`, `:`) are literal.
- Test isolation rule of thumb: things that *vary by deployment* (host, credentials, log level) → `.env`. Things that are *test invariants* (`ddl-auto=create-drop`, `show-sql=false`) → hardcoded in `src/test/resources/application.properties`. DB name straddles the line — kept as an env var here so the developer explicitly opts into the test DB, but at the cost of footgun risk (running tests with the dev `DB_NAME` will wipe dev data via `bookRepository.deleteAll()`).
- `.env` is gitignored; `.env.example` (same keys, sample values) is committed so the schema of expected env vars is discoverable.

## Soft deletes (Hibernate `@SQLDelete` + `@SQLRestriction`)

- `@SQLDelete(sql = "UPDATE <table> SET deleted_at = NOW() WHERE id = ?")` rewrites Hibernate's DELETE statement. `repository.delete*` calls flow through this — no controller/service change needed.
- `@SQLRestriction("deleted_at IS NULL")` is appended to every SELECT/JPQL query. Soft-deleted rows are invisible to `findById`, `findAll`, JPQL, derived queries — uniformly.
- `existsById` honors `@SQLRestriction`, so the existing `if (!repo.existsById) throw 404` pattern keeps working: a second DELETE on the same id returns 404 just like a hard delete would.
- Replaces the deprecated `@Where`. Both came from `org.hibernate.annotations`; `@SQLRestriction` is the supported one in Hibernate 6+.
- Audit columns (`created_at`, `updated_at`): `@CreationTimestamp` and `@UpdateTimestamp` from `org.hibernate.annotations` are the Hibernate-specific equivalents of JPA's `@PrePersist`/`@PreUpdate` lifecycle hooks. Bound to `Instant`. Columns get `nullable=false`; `created_at` also `updatable=false`.
- `deleted_at` is internal — never returned in DTOs. The `Response` record omits it.

## Partial unique indexes (Postgres)

- A *partial* unique index applies the uniqueness rule only to rows matching a `WHERE` predicate. Standard SQL doesn't have it; Postgres does: `CREATE UNIQUE INDEX ... ON tbl (col) WHERE predicate`.
- Why it matters with soft deletes: a full unique index on `email` would block a user from re-registering after their previous account was soft-deleted. A partial index (`WHERE deleted_at IS NULL`) only enforces uniqueness against *active* rows.
- JPA can't express it: `@UniqueConstraint` and `@Index` accept column lists only, no predicate. Three workarounds:
  - `src/main/resources/import.sql` — runs after Hibernate's schema generation on every boot. Use `IF NOT EXISTS` for idempotency. *Chosen for now.*
  - Flyway/Liquibase migrations — versioned, repeatable, the "right" answer for production.
  - Native query in a `@PostConstruct` hook — works but couples app startup to DDL.

## Immutable fields via DTO split

- ISBN is immutable: settable only at creation. Enforcement options:
  - **DTO split** (chosen): `WriteRequest` (POST, includes isbn) vs `UpdateRequest` (PUT, no isbn). Wire contract states immutability by construction — clients literally can't send a new ISBN through PUT. Plays well with MapStruct (separate `toEntity` and `updateFromUpdateRequest`).
  - **Service check**: keep one DTO, reject PUT bodies whose ISBN differs from stored. Less code but a more confusing contract — the API says ISBN is settable, then refuses to set it.
- Same shape will apply to other immutable identity fields later (e.g. a member's `email`, if treated as identity).

## JPA + PostgreSQL

- Driver: `org.postgresql:postgresql` (runtimeOnly). Spring Boot auto-detects the dialect from the JDBC URL — no `spring.jpa.properties.hibernate.dialect` needed.
- `spring.datasource.url=jdbc:postgresql://host:port/dbname`. `driver-class-name=org.postgresql.Driver`.
- `ddl-auto`:
  - `create-drop` → drop + recreate on startup AND shutdown. Use for tests.
  - `update` → reconcile schema with entities on startup; safe to keep data. Use for dev sandbox.
  - `validate` → check only; fail if drift. Pair with Flyway in real apps.
  - `none` → Hibernate doesn't touch schema. Production with migrations.
- Dev vs test isolation: separate physical databases (`library`, `library_test`). Test config lives in `src/test/resources/application.properties`; Spring picks it up automatically over the main file during test runs.
- Postgres-specific quirks vs H2:
  - Identifiers are case-sensitive when quoted. `@Table(name="books")` works fine (lowercase, unquoted).
  - Reserved words (`user`, `order`) need quoting. Hibernate's `globally_quoted_identifiers` handles it, but renaming the column is cleaner.
  - `bigint generated by default as identity` (used here) is standard SQL, supported by Postgres natively — no sequence to manage.

## JPA + H2 (historical, replaced)

- Annotations from `jakarta.persistence` (not `javax.persistence`).
- `@Entity`, `@Id`, `@GeneratedValue(strategy = IDENTITY)`.
- Entities can't be records: JPA needs no-arg ctor + mutability.
- Omit `setId` on entities — prevents clients pre-assigning ids via `@RequestBody`.
- `BookRepository extends JpaRepository<BookEntity, Long>` — implementation generated at runtime. Free: `findAll`, `save`, `findById`, `deleteById`, etc. Custom finders by method name (`findByTitleContainingIgnoreCase`).
- DI: constructor injection (`final` fields, explicit deps, trivial to unit test). No `@Autowired` on fields.
- `@RequestBody` → Jackson: no-arg ctor + setters. Absence of setter blocks the field.

### H2 config (application.properties)

| Key                                            | Value                 | Effect                                                      |
| ---------------------------------------------- | --------------------- | ----------------------------------------------------------- |
| `spring.datasource.url`                        | `jdbc:h2:mem:library` | in-memory; data lives only while app runs                   |
| `spring.jpa.hibernate.ddl-auto`                | `create-drop`         | schema generated from entities at boot; dropped on shutdown |
| `spring.jpa.show-sql` + `hibernate.format_sql` | `true`                | log Hibernate's SQL                                         |
| `spring.h2.console.enabled`                    | `true`                | console at `/h2-console`                                    |

H2 console: `jdbc:h2:mem:library`, user `sa`, blank password. For real apps later: `ddl-auto=none` + Flyway.

## HTTP verb semantics

| Verb   | Meaning                                 | Implementation                                                             |
| ------ | --------------------------------------- | -------------------------------------------------------------------------- |
| POST   | create                                  | 201 Created                                                                |
| PUT    | full replace; missing fields default    | load → overwrite all → save                                                |
| PATCH  | partial update; missing = "don't touch" | DTO uses wrapper types (`Integer`, not `int`) to distinguish absent from 0 |
| DELETE | remove                                  | 204 No Content on success; 404 if missing                                  |

## DTOs

- Group via interface namespace with nested records: `BookDto.WriteRequest`, `BookDto.PatchRequest`, `BookDto.Response`.
- Interface members are implicitly `public static`.
- Principle: **group by data, separate by behavior** (exceptions stay one-per-file for type-based dispatch).

## Exception handling

- Custom exceptions extend `RuntimeException`. They know nothing about HTTP. They're domain signals.
- HTTP translation lives in `@RestControllerAdvice` + `@ExceptionHandler(SomeException.class)`.
- Shared response: `common/ErrorResponse` record. Records preserve component order in JSON (`Map.of` is unordered).
- Helpers: `notFound(message)`, `badRequest(message, errors)` collapse each handler to ~1 line.

Handlers registered:

| Exception                             | Status | Cause                                                                                                                                                                       |
| ------------------------------------- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BookNotFoundException`               | 404    | domain not-found                                                                                                                                                            |
| `NoResourceFoundException`            | 404    | unknown route; replaces Whitelabel page                                                                                                                                     |
| `MethodArgumentNotValidException`     | 400    | `@Valid` body failed — emits field errors                                                                                                                                   |
| `HandlerMethodValidationException`    | 400    | `@PathVariable`/`@RequestParam` Bean Validation failed (Spring 6.1+; auto, no `@Validated` needed). API: `getParameterValidationResults()` (NOT `getAllValidationResults`). |
| `MethodArgumentTypeMismatchException` | 400    | path/query type conversion failed (e.g. `/books/hhjg` for `Long`)                                                                                                           |
| `HttpMessageNotReadableException`     | 400    | malformed JSON / wrong content-type                                                                                                                                         |

`@ResponseStatus(HttpStatus.X)` on a `void` method sets status without `ResponseEntity`.

## Bean Validation

| Annotation                                 | Behavior                                                        |
| ------------------------------------------ | --------------------------------------------------------------- |
| `@NotBlank`                                | string non-null AND non-empty AND not whitespace-only           |
| `@NotNull`                                 | non-null; use on wrapper types only (meaningless on primitives) |
| `@Size`, `@Min`, `@Max`, `@PositiveOrZero` | **null-tolerant** — skip nulls; safe on PATCH                   |

- DTO bounds must match entity bounds (defense in depth).
- For future Stripe-style string ids (`book_abc123`): `@Pattern` on the path variable is the only thing distinguishing 400-malformed from 404-not-found — every string parses as `String`, so type-mismatch handler won't fire.

## Service layer

- `@Service` ≡ `@Component` (semantic only).
- `@Transactional` — Spring AOP wraps method in DB tx. `RuntimeException` rolls back.
- Class-level `@Transactional(readOnly = true)` + method-level `@Transactional` on writes. Hibernate skips dirty-checking on read-only.
- Service takes request DTOs directly for now. Translate to internal command types only when a second caller (CLI, GraphQL, scheduled job) needs it.

## MapStruct

- Compile-time annotation processor; generates `XxxImpl` (plain getter→setter, no reflection, debuggable).
- `@Mapper(componentModel = "spring")` → generated impl is a `@Component`; injected via constructor.
- Works fine on Java 25 / Spring Boot 4.0.6 — uses standard JSR-269 APIs.

`BookMapper` methods:

| Method                                                            | Use           | Notes                                                                                      |
| ----------------------------------------------------------------- | ------------- | ------------------------------------------------------------------------------------------ |
| `toResponse(BookEntity)`                                          | GET response  |                                                                                            |
| `toResponses(List<BookEntity>)`                                   | list response | auto-delegates to `toResponse` per item                                                    |
| `toEntity(WriteRequest)`                                          | POST          | `@Mapping(target="id", ignore=true)` — DB owns id                                          |
| `updateFromWriteRequest(@MappingTarget BookEntity, WriteRequest)` | PUT           | full overwrite, id preserved                                                               |
| `updatePatch(@MappingTarget BookEntity, PatchRequest)`            | PATCH         | `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` — null fields leave target alone |

When to add MapStruct: copy-pasted the same mapping 3 times, or entity has many fields. Skip for 1–2 field DTOs.

## Logging (SLF4J)

- Pattern per class: `private static final Logger log = LoggerFactory.getLogger(X.class);` — static ensures one logger per class, named after FQN so per-package level filtering works.
- SLF4J is a facade; Logback is the backend (bundled by Spring Boot).
- Levels: `TRACE < DEBUG < INFO < WARN < ERROR`. Setting level emits that level and above.

Where this project logs what:

| Site                                         | Level | Reason                           |
| -------------------------------------------- | ----- | -------------------------------- |
| Service writes (create/replace/patch/delete) | INFO  | state changes — permanent record |
| Service reads (findAll/findById)             | DEBUG | high-frequency, off by default   |
| `GlobalExceptionHandler` 4xx                 | DEBUG | client errors, not noise-worthy  |
| Hypothetical 5xx                             | ERROR | our bug; log with throwable      |

- Parameterized: `log.info("created id={}", id)` — lazy. Never `"id=" + id` (eager).
- Expensive arg? Use `log.info("...{}", () -> expensive())` is NOT SLF4J — instead guard with `if (log.isInfoEnabled())`.
- Config: `logging.level.<pkg>=<level>` in `application.properties`.
- Runtime override: `--logging.level.X=DEBUG` (CLI) or env `LOGGING_LEVEL_X=DEBUG`.
- Structured JSON (Spring Boot 3.4+, built-in): `logging.structured.format.console={ecs|gelf|logstash}`. Off by default.
- MDC = thread-local key/value context. Structured formatters emit MDC entries as top-level JSON fields. Not used yet — add when introducing a request-ID filter.

## Entity-level constraints / DDL

What lands in generated DDL:

| Entity source                                        | DDL effect                                           |
| ---------------------------------------------------- | ---------------------------------------------------- |
| `@NotBlank` / `@NotNull` / `@Column(nullable=false)` | `NOT NULL`                                           |
| `@Size(max=N)` on String / `@Column(length=N)`       | `VARCHAR(N)`                                         |
| `@Min(a) @Max(b)` on numerics                        | `CHECK` (Hibernate; not guaranteed across providers) |
| `@NotBlank` whitespace-only rule                     | runtime only — no portable SQL                       |

- `@Entity(name="...")` sets the JPQL entity name, NOT the table name. Use `@Table(name="...")` for the table.
- Hibernate's `@Check(constraints="...")` for arbitrary DB-level rules (cross-column, regex). Not needed currently.

Generated DDL for `books`:

```sql
create table books (
	count integer not null check ((count<=100000) and (count>=0)),
	id bigint generated by default as identity,
	title varchar(1000) not null,
	primary key (id)
)
```

## Formatting (Spotless)

- Plugin: `com.diffplug.spotless` (Gradle). Wraps multiple formatters/linters into one declarative config in `build.gradle`.
- Tasks: `spotlessApply` writes formatted output; `spotlessCheck` verifies and fails on diff (CI gate).
- `./gradlew check` (and therefore `./gradlew build`) wires `spotlessCheck` in automatically — a misformatted file breaks the build.

Steps configured for Java (run in order):

| Step                      | Effect                                                            |
| ------------------------- | ----------------------------------------------------------------- |
| `importOrder()`           | Sorts imports into deterministic groups                           |
| `removeUnusedImports()`   | Drops unreferenced imports                                        |
| `forbidWildcardImports()` | Fails on `import x.y.*` — forces explicit imports                 |
| `forbidModuleImports()`   | Fails on JDK 25 `import module x.y` — keeps imports type-by-type  |
| `cleanthat()`             | Light static refactors (e.g. `Optional` use, redundant modifiers) |
| `googleJavaFormat()`      | Final pass: 2-space indent, 100-col, Google style                 |

Project workflow:

| Command             | When                                                      |
| ------------------- | --------------------------------------------------------- |
| `make format`       | Before committing — fixes everything in place             |
| `make format-check` | CI / sanity check — exits non-zero on any formatting diff |

`spotlessApply` is idempotent: re-running on already-formatted code is a no-op.

## Gotchas

- **Trailing whitespace in `.properties`** — `spring.datasource.driver-class-name=org.h2.Driver····` makes Spring load `"org.h2.Driver    "` (with spaces) and fail with "Cannot load driver class". `.properties` preserves trailing whitespace; YAML/TOML strip. Editor: `files.trimTrailingWhitespace: true`.
- **Records and `new`** — record's canonical constructor takes all components. No implicit no-arg ctor.
- **`getAllValidationResults` doesn't exist** on `HandlerMethodValidationException`. Use `getParameterValidationResults()`. When an exception type is unfamiliar, `javap` on the class in the jar is fast and authoritative.

## Decisions

- `AGENTS.md` is canonical; `CLAUDE.md` is a thin pointer. Project info lives in one file — anti-drift.
- Tracking files: AGENTS (canonical), CLAUDE (pointer), PROGRESS (tasks), JOURNEY (history). Update after meaningful milestones.
- Group by data, separate by behavior: DTOs grouped in `BookDto`; exceptions stay one-per-file.

## Pagination (Spring Data)

- `JpaRepository.findAll(Pageable)` returns `Page<T>`. `Page` exposes `getContent()`, `getTotalElements()`, `getTotalPages()`, `hasNext()`, `hasPrevious()`.
- `PageRequest.of(pageIndex, size)` builds a `Pageable`. **`pageIndex` is 0-based.** Public API is usually 1-based; convert at the boundary (controller or service) — this project does it in the service.
- Query-param defaults + bounds live on the controller method: `@RequestParam(defaultValue = "1") @Min(1) Integer page`, `@RequestParam(defaultValue = "10") @Min(10) @Max(50) Integer limit`. `HandlerMethodValidationException` → 400 via the existing handler.
- Without an explicit `Sort`, ordering is whatever the DB hands back. H2 returns insertion order in practice, but production code should pass `Sort.by(...)` to `PageRequest.of(...)` for deterministic paging.
- Meta calculation in controller: `nextPage = result.hasNext() ? page + 1 : null`, `prevPage = page > 1 ? page - 1 : null`. Nulls hidden from JSON via `@JsonInclude(NON_NULL)` on the `Meta` record.

## Root-key envelopes

- Every book payload (in and out) is wrapped under `"book"` (single) or `"books"` + `"meta"` (list). Wire format becomes self-describing and survives adding sibling top-level fields (`included`, `links`, `warnings`) without breaking clients.
- Implementation: nested record envelopes in `BookDto` (`WriteEnvelope`, `PatchEnvelope`, `ResponseEnvelope`, `ListEnvelope`). Controller accepts/returns envelopes; service stays unaware of the wire shape.
- `@Valid @NotNull` on the envelope's inner field is the trick: `@NotNull` rejects `{}` / `{"book": null}`; `@Valid` cascades Bean Validation into the wrapped DTO so `WriteRequest` constraints still fire. Field-error paths report `book.title` instead of `title` — accurate to the wire shape.
- Snake_case JSON keys (`next_page`) on camelCase Java fields (`nextPage`) via `@JsonProperty("next_page")` from `com.fasterxml.jackson.annotation`. Per-field annotation chosen over a global `PropertyNamingStrategy` to keep the rest of the API explicit.

## Testing

- **Integration Tests**: `@SpringBootTest` + `@AutoConfigureMockMvc` tests the entire application stack.
- `MockMvc`: used to perform simulated HTTP requests (`get`, `post`, etc.) and verify responses without starting a real HTTP server.
- **Unit Tests**: `@ExtendWith(MockitoExtension.class)` for fast service-layer tests isolated from the Spring context.
- `ObjectMapper`: injected to serialize/deserialize DTOs to/from JSON in test payloads.
- **Test Coverage**: Configured via `jacoco` plugin. Running `make test` executes tests and generates `html` and `xml` coverage reports under `build/reports/jacoco/test/`.
