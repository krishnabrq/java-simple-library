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
- Test isolation rule of thumb: things that _vary by deployment_ (host, credentials, log level) → `.env`. Things that are _test invariants_ (`ddl-auto=create-drop`, `show-sql=false`) → hardcoded in `src/test/resources/application.properties`. DB name straddles the line — kept as an env var here so the developer explicitly opts into the test DB, but at the cost of footgun risk (running tests with the dev `DB_NAME` will wipe dev data via `bookRepository.deleteAll()`).
- `.env` is gitignored; `.env.example` (same keys, sample values) is committed so the schema of expected env vars is discoverable.

## Soft deletes (Hibernate `@SQLDelete` + `@SQLRestriction`)

- `@SQLDelete(sql = "UPDATE <table> SET deleted_at = NOW() WHERE id = ?")` rewrites Hibernate's DELETE statement. `repository.delete*` calls flow through this — no controller/service change needed.
- `@SQLRestriction("deleted_at IS NULL")` is appended to every SELECT/JPQL query. Soft-deleted rows are invisible to `findById`, `findAll`, JPQL, derived queries — uniformly.
- `existsById` honors `@SQLRestriction`, so the existing `if (!repo.existsById) throw 404` pattern keeps working: a second DELETE on the same id returns 404 just like a hard delete would.
- Replaces the deprecated `@Where`. Both came from `org.hibernate.annotations`; `@SQLRestriction` is the supported one in Hibernate 6+.
- Audit columns (`created_at`, `updated_at`): `@CreationTimestamp` and `@UpdateTimestamp` from `org.hibernate.annotations` are the Hibernate-specific equivalents of JPA's `@PrePersist`/`@PreUpdate` lifecycle hooks. Bound to `Instant`. Columns get `nullable=false`; `created_at` also `updatable=false`.
- `deleted_at` is internal — never returned in DTOs. The `Response` record omits it.

## Partial unique indexes (Postgres)

- A _partial_ unique index applies the uniqueness rule only to rows matching a `WHERE` predicate. Standard SQL doesn't have it; Postgres does: `CREATE UNIQUE INDEX ... ON tbl (col) WHERE predicate`.
- Why it matters with soft deletes: a full unique index on `email` would block a user from re-registering after their previous account was soft-deleted. A partial index (`WHERE deleted_at IS NULL`) only enforces uniqueness against _active_ rows.
- JPA can't express it: `@UniqueConstraint` and `@Index` accept column lists only, no predicate. Three workarounds:
  - `src/main/resources/import.sql` — runs after Hibernate's schema generation on every boot. Use `IF NOT EXISTS` for idempotency. _Chosen for now._
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

| Method                                                              | Use           | Notes                                                                                      |
| ------------------------------------------------------------------- | ------------- | ------------------------------------------------------------------------------------------ |
| `toResponse(BookView)`                                              | GET response  | `default` method; computes `available_count = count − activeLoanCount`                     |
| `toResponses(List<BookView>)`                                       | list response | `default` method; delegates to `toResponse(BookView)` per item                             |
| `toEntity(WriteRequest)`                                            | POST          | `@Mapping(target="id", ignore=true)` — DB owns id                                          |
| `updateFromUpdateRequest(@MappingTarget BookEntity, UpdateRequest)` | PUT           | full overwrite of mutable fields; isbn / id / audit stay as-is on loaded entity            |
| `updatePatch(@MappingTarget BookEntity, PatchRequest)`              | PATCH         | `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` — null fields leave target alone |

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

## RBAC: where the role check actually lives

- Two layers can enforce a role: `@PreAuthorize("hasRole('X')")` at the HTTP boundary (reads the JWT's authorities, throws `AccessDeniedException` → 403), and a service-layer guard that loads the user from the DB and checks the live role.
- Books mutations use `@PreAuthorize` only. JWT-authority is fine here: a STAFF user demoted to MEMBER mid-token-lifetime would briefly retain write access until their token expires (≤15 min). Acceptable for a learning project; real prod would use short access TTLs + a revocation list.
- Loan endpoints use service-layer enforcement (per project preference) — `LoanService.requireMember(...)` consults `userRepository.findById(...).getRole()` against the live DB. Catches the demotion case immediately, and keeps the rule visible at the layer that owns the domain ("only members can borrow"). The endpoints are still gated to `authenticated()` by the global filter chain.
- Two different exceptions, both 403:
  - Spring Security's `AccessDeniedException` (from `@PreAuthorize` failure) → handled by `SecurityConfig.accessDeniedHandler`.
  - `LoanNotPermittedException` (service throws it) → handled by `GlobalExceptionHandler`.
    Both render the same `ErrorResponse` JSON shape, so callers can't tell which layer rejected them.
- Identity in the controller: `@AuthenticationPrincipal Jwt jwt` injects the parsed token. `jwt.getSubject()` returns the user id as a string (that's what `JwtService.issue(...)` writes into `sub`). The controller parses to `Long` and hands off to the service; the service does the DB look-up.

## Mapping security failures to JSON

- Resource-server's default 401 is empty body + `WWW-Authenticate: Bearer error="invalid_token"` header. Default 403 is also empty. Two overrides bring them into our envelope:
  - `AuthenticationEntryPoint` (called when the chain rejects an unauthenticated request) → write `{"status":401, "error":"Unauthorized", ...}` via `ObjectMapper`.
  - `AccessDeniedHandler` (called when an authenticated request fails authority check) → write `{"status":403, "error":"Forbidden", ...}`.
- Both are configured twice: once on `oauth2ResourceServer.jwt(...).authenticationEntryPoint(...)` and once on `exceptionHandling(...)`. The first covers token-related rejections (caught by the JWT filter); the second covers post-authentication rejections (from `AuthorizationFilter` and `@PreAuthorize`).
- `ErrorResponse` is intentionally tiny (4 fields, no FieldErrors list for security failures) — security errors don't have field-level detail to surface.

## Borrow + return: state-machine in two endpoints

- `POST /api/v1/loans` body is just `{book_id}`. The caller is identified by the JWT — never trust the client to say who they are. Service inserts `borrowed_at = NOW()`, `returned_at = NULL`.
- `PATCH /api/v1/loans/{id}/return` has no body. Service flips `returned_at` from `NULL` to `NOW()`. Double-return is rejected with 409.
- "Loan belongs to another member" returns the same 404 as "loan doesn't exist". Refusing to disclose membership of other users' loans is cheap and aligns with the "don't leak existence" pattern from auth login.
- Availability check (`active < book.count`) is not atomic with the loan insert — two concurrent borrows can race. Documented inline. Real fix is `SELECT ... FOR UPDATE` on the book row (`@Lock(LockModeType.PESSIMISTIC_WRITE)` on a derived query) or a database-level constraint that counts active loans. Out of scope for this project.

## Auth flow: signup / login / refresh / logout

- Endpoints mounted at `/api/v1/auth/**` (permitted in `SecurityConfig`). `AuthController` is thin — DTO unwrap, call `AuthService`, wrap the `TokenPair` into a wire `TokenResponse` with `token_type=Bearer` and `expires_in` (seconds).
- Signup is members-only. The endpoint hardcodes `UserRole.MEMBER`; staff accounts are seeded out-of-band (manual SQL) so the public API surface can't elevate privilege. `password` field is bcrypt-hashed via `PasswordEncoder.encode(...)` before save.
- Login: `findByEmail` → `passwordEncoder.matches(rawPassword, hash)`. Both "unknown email" and "wrong password" paths throw the same exception with the same message ("Invalid email or password") — refuses to leak email existence via response differences.
- Refresh: decode the presented token with `JwtDecoder` (validates signature + expiry in one call), check the `type` claim equals `refresh` (so an access token can't be replayed against this endpoint), look up the user by `sub`, re-issue both tokens. Stateless — no DB-side refresh table for now; logout is a client-discards-tokens operation. Adding a JTI denylist later would make real revocation possible without changing this contract.
- Logout: returns 204, body empty. Pure no-op today. Documented intent — endpoint exists so the wire contract is symmetric and a future revocation list slots in without breaking clients.
- Error mapping (all in `GlobalExceptionHandler`):
  - `EmailAlreadyExistsException` → 409 Conflict.
  - `InvalidCredentialsException`, `InvalidTokenException` → 401 Unauthorized. One handler covers both via the `{}` form on `@ExceptionHandler`.
- Testing the auth flow: `@SpringBootTest` with the full security chain on (no `addFilters=false`) because `/api/v1/auth/**` is publicly permitted — no Bearer header needed. The "issued access token carries role and email claims" test decodes the JWT body by base64url'ing the middle segment and asserts on the parsed JSON — verifies the claim contract without coupling to Nimbus internals.

## Spring Security: stateless JWT bearer auth

- Two starters do most of the work: `spring-boot-starter-security` (filter chain, encoders, `@PreAuthorize`) and `spring-boot-starter-oauth2-resource-server` (`NimbusJwtDecoder` / `NimbusJwtEncoder` plus the `BearerTokenAuthenticationFilter` that pulls a Bearer token off the request and turns it into an `Authentication`).
- The "resource server" framing is OAuth2 vocabulary: a service that validates a token someone else issued. We're doing both — issuing AND validating — but using the resource-server filter for inbound auth keeps the chain off-the-shelf.
- Single HS256 secret signs and verifies. Wrapped as a Nimbus `OctetSequenceKey` for the encoder, as a raw `SecretKeySpec` for the decoder. Loaded from `${JWT_SECRET}` with no default — startup fails fast if it's missing, surfacing config errors immediately rather than at the first request.
- `SecurityFilterChain` shape:
  - `csrf().disable()` — stateless API, no session cookies, so the CSRF token machinery has nothing to protect.
  - `sessionCreationPolicy(STATELESS)` — Spring Security creates no `HttpSession` and never tries to read one.
  - `authorizeHttpRequests`: `/api/v1/auth/**` and `/error` are public; everything else `authenticated()`.
  - `oauth2ResourceServer.jwt(...)` — wires the JWT filter.
- `JwtAuthenticationConverter` is what links our `role` claim to Spring's `hasRole(...)` checks. Default converter looks at `scope` / `scp` claims (OAuth2 scopes). We override it: read the `role` claim ("MEMBER"/"STAFF") and emit a single `SimpleGrantedAuthority("ROLE_" + role)`. The `ROLE_` prefix is what `hasRole("STAFF")` strips and compares against.
- `@EnableMethodSecurity` turns on `@PreAuthorize` / `@PostAuthorize` for Phase D. Without it, those annotations are silently no-ops.
- Tokens carry `iss=library`, `sub=user.id`, `iat`, `exp`, plus `email`, `role`, and `type` (`access` | `refresh`). `type` lets endpoints reject a refresh token presented at an access-protected route (and vice versa) without DB lookups.

## `@WithMockUser` vs the resource-server filter chain

- `@WithMockUser` is the textbook way to authenticate a MockMvc test: a `TestExecutionListener` populates the per-thread `SecurityContextHolder` before the test method runs.
- Doesn't work cleanly with the OAuth2 resource-server chain. `SecurityContextHolderFilter` (early in the chain) calls `SecurityContextRepository.loadDeferredContext(request)` and then **overwrites** the per-thread context with whatever the repository returned. Default repository for stateless apps returns an empty context, so the mock user is wiped before `AuthorizationFilter` checks authorities — the request is treated as anonymous and the resource-server's entry point sends a 401 with `WWW-Authenticate: Bearer ...`.
- Two ways out:
  - **`@AutoConfigureMockMvc(addFilters = false)`** — skip the entire security chain for tests that exercise domain behavior, not security. Used for `BookControllerTest`.
  - **`SecurityMockMvcRequestPostProcessors.jwt()`** — per-request, injects a fake `Jwt` directly into the request so the resource-server filter authenticates against it. The right choice for tests that need to assert role-based behavior (Phase D).
- Lesson: `@WithMockUser` only works when the filter chain trusts `SecurityContextHolder`. Resource-server trusts the request, not the holder.

## Identity tables: one `users` over two role tables

- Started with `staffs` and `members` as separate tables (identical column shape, FK from `book_loans` pointing at `members` specifically). Merged into a single `users` table with a `role` enum column (`MEMBER` | `STAFF`).
- Driver: auth/login/refresh/logout flows are byte-for-byte identical for both roles. Two tables forces two of every auth class — repository, service, controller, password encoder wiring. One table keeps the surface honest.
- Trade: the FK-level guarantee that a loan only references a member is gone. `book_loans.user_id` now points at any user. The "only MEMBER can borrow" rule moves to the service layer (and Spring Security `@PreAuthorize`). Defense-in-depth becomes a single-line authorization check.
- Email uniqueness: collapsed from two partial indexes (one per old table) to one (`users_email_active_uidx`). Same email can no longer exist as both a staff and a member — almost certainly the desired behavior.
- `EnumType.STRING` over `EnumType.ORDINAL`: the constant's _name_ goes in the DB. Survives reordering the enum's declarations. ORDINAL writes 0, 1, 2 — silently shifts meaning if anyone moves the constants.
- Schema migration: Hibernate's `ddl-auto=update` will _add_ the `users` table and the `user_id` column on `book_loans`, but it doesn't drop the old `staffs` / `members` tables or the old `member_id` column. For a dev sandbox the cleanest move is `dropdb library && createdb library` and let Hibernate recreate from scratch. Tests run on `ddl-auto=create-drop`, so they're already hermetic — no manual step.

## Avoiding N+1 with JPQL constructor projection

- "Show the active-loan count for every book in the list" is the canonical N+1 setup: one SELECT for books, then one COUNT per book. The fix is a single query that left-joins loans and groups by book.
- JPQL supports it cleanly: `LEFT JOIN BookLoanEntity l ON l.book = b AND l.returnedAt IS NULL AND l.deletedAt IS NULL`. The explicit `ON` predicate (JPA 2.1+) is what makes it work — putting the loan filters in a `WHERE` clause would drop books with zero active loans, since the join produces no rows for them and `WHERE` evaluates after the join.
- Returning the joined shape via a constructor projection: `SELECT new com.training.library.books.BookView(b, COUNT(l)) ... GROUP BY b`. `BookView` is a record (`record BookView(BookEntity book, Long activeLoanCount)`) — Spring Data finds the matching ctor by FQN and hydrates a typed `Page<BookView>` directly. Beats `Object[]` rows or a separate `@SqlResultSetMapping`.
- `LEFT JOIN` (not inner): books with zero loans still appear with `COUNT(l) = 0`. `@SQLRestriction` on `BookLoanEntity` already filters soft-deleted loans uniformly, but keeping the predicates explicit in the JPQL `ON` is defensive — if someone later removes `@SQLRestriction`, the query still does the right thing.
- Paginated `GROUP BY` needs an explicit `countQuery`. Spring Data tries to derive a count query by stripping the `SELECT` from the original; with `GROUP BY` that derivation either fails or returns the post-group row count (one per group), not the total entity count. Set `countQuery = "SELECT COUNT(b) FROM BookEntity b"` on `@Query` to short-circuit it.
- Service / mapper / DTO each see `BookView` instead of `BookEntity`. The mapper's `toResponse(BookView)` subtracts `activeLoanCount` from `count` once, in a `default` method — keeps the arithmetic in one place instead of scattered across controller assemblers.

## State-conflict guards (409 vs 400)

- Two flavours of "invalid count" need different statuses:
  - **Static** ("count must be > 0"): expressible as Bean Validation on the DTO field (`@Positive` on `UpdateRequest.count` / `PatchRequest.count`). Validation runs before the controller body, fails with `MethodArgumentNotValidException` → 400 via the existing handler. No service-layer code.
  - **Dynamic** ("count must be ≥ currently borrowed copies", "can't delete with active loans"): depends on persisted state, so it lives in the service. Service throws a domain exception (`BookConflictException`); `@RestControllerAdvice` maps it to 409.
- Reusing the existing 4xx-as-domain-exception pattern keeps controllers HTTP-status-free. The new handler is one method.
- Tests prove the split: PUT/PATCH with `count=0` returns 400 with a `book.count` field error; PUT/PATCH with `count=1` when 5 loans are active returns 409 with the conflict message.
- `WriteRequest.count` stays `@PositiveOrZero` — the "> 0" rule was explicitly scoped to updates. A fresh book with zero copies is a legitimate POST state.

## Soft delete + integration test isolation

- `@SQLDelete` rewrites `repository.delete*()` into `UPDATE deleted_at = NOW()`. Rows physically remain. `@SQLRestriction` hides them from every read, so within a test the row is "gone" — but it's still in the table.
- Consequence for `@BeforeEach { repo.deleteAll(); }`: after N tests, the table holds N tombstones. Partial unique indexes (`WHERE deleted_at IS NULL`) ignore them, so re-inserting the same ISBN/email keeps working. `ddl-auto=create-drop` on the test DB recreates schema between `./gradlew test` invocations, so zombie rows don't pile up across runs.
- Order of `deleteAll()` calls matters for FKs: children before parents (`loanRepository.deleteAll()` before `bookRepository.deleteAll()` / `memberRepository.deleteAll()`). FK constraints reference rows by id; soft-deleted rows still exist, so the constraint stays satisfied — but during the soft-delete itself, the loan row needs to exist before its parents are touched.

## Testing

- **Integration Tests**: `@SpringBootTest` + `@AutoConfigureMockMvc` tests the entire application stack.
- `MockMvc`: used to perform simulated HTTP requests (`get`, `post`, etc.) and verify responses without starting a real HTTP server.
- **Unit Tests**: `@ExtendWith(MockitoExtension.class)` for fast service-layer tests isolated from the Spring context.
- `ObjectMapper`: injected to serialize/deserialize DTOs to/from JSON in test payloads.
- **Test Coverage**: Configured via `jacoco` plugin. Running `make test` executes tests and generates `html` and `xml` coverage reports under `build/reports/jacoco/test/`.
