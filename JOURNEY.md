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

## JPA + H2

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
