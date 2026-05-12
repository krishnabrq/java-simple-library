# Progress

## Done

- Scaffolded via start.spring.io: web-mvc, data-jpa, h2, devtools, validation. Java 25, Gradle, Spring Boot 4.0.6.
- API mounted at `/api/v1/books` (class-level `@RequestMapping`).
- Package-by-feature; `common/` created only when first cross-cutting class arrived. `config/`, `storage/`, `messaging/` deferred.
- JPA + H2 in-memory + console + SQL logging. `BookEntity` (class, not record — JPA needs no-arg ctor + mutability). No `setId`.
- CRUD on `/api/v1/books`: GET list, GET by-id (404 via `BookNotFoundException`), POST (201), PUT (full replace), PATCH (partial via `PatchRequest` with nullable wrapper types), DELETE (204; 404 if missing).
- `common/GlobalExceptionHandler` (`@RestControllerAdvice`). Handles: `BookNotFoundException` (404), `NoResourceFoundException` (404, replaces Whitelabel HTML), `MethodArgumentNotValidException` (400, body validation field errors), `HandlerMethodValidationException` (400, param-level validation), `MethodArgumentTypeMismatchException` (400, path/query conversion), `HttpMessageNotReadableException` (400, malformed JSON).
- `spring-boot-starter-validation`. `WriteRequest` (POST/PUT): `@NotBlank @Size(1,1000)` title, `@NotNull @PositiveOrZero @Max(100_000)` count. `PatchRequest` (PATCH): null-tolerant only (`@Size`, `@PositiveOrZero`, `@Max`). `@Min(1)` on `bookId` path variables.
- `common/ErrorResponse` record (preserves component order in JSON; vs `Map.of` unordered).
- `BookService`: extracted from controller. Class-level `@Transactional(readOnly = true)`; method-level `@Transactional` on writes.
- `BookDto.Response` record. Controller returns DTOs only; entity stays internal.
- `BookMapper` (MapStruct, `componentModel = "spring"`): `toResponse`, `toResponses`, `toEntity` (`@Mapping(target="id", ignore=true)`), `updateFromWriteRequest` (PUT), `updatePatch` (PATCH; `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)`).
- `AGENTS.md` canonical; `CLAUDE.md` shrunk to pointer.
- `BookEntity` entity-level constraints: `@Table(name="books")`. Title `@NotBlank @Size(1,1000)` + `@Column(nullable=false, length=1000)`. Count `@Min(0) @Max(100_000)` + `@Column(nullable=false)`. DDL emits `NOT NULL`, `VARCHAR(1000)`, `CHECK` on count.
- `Makefile`: `help`, `build`, `run`, `test`, `clean`, `compile`, `watch`, `deps`, `format`, `format-check`. Self-documenting via `## ` comments.
- SLF4J logging. `BookService`: writes INFO, reads DEBUG. `GlobalExceptionHandler` 4xx: DEBUG. Configurable via `logging.level.<pkg>=<level>`. Structured JSON (Spring Boot 3.4+ built-in) via `logging.structured.format.console={ecs|gelf|logstash}` (off by default).
- Spotless (`com.diffplug.spotless` 8.4.0) + google-java-format. Also: `removeUnusedImports`, `importOrder`, `forbidWildcardImports`, `forbidModuleImports`, `cleanthat`. `make format` to apply, `make format-check` to verify.

- First test: `@SpringBootTest` (end-to-end API tests) and `@ExtendWith(MockitoExtension.class)` (unit tests) added. Test coverage via `jacoco`.
- Pagination on `GET /api/v1/books` via Spring Data `Page` / `PageRequest`. Query params: `page` (default 1, `@Min(1)`) and `limit` (default 10, `@Min(10) @Max(50)`). API page is 1-based; service subtracts 1 for Spring Data's 0-based `PageRequest`. Meta: `{total, next_page, prev_page}`; `next_page`/`prev_page` omitted via `@JsonInclude(NON_NULL)` when no neighbour.
- Root-key envelopes on all book request/response bodies (`{"book": {...}}`, list returns `{"books": [...], "meta": {...}}`). Implemented as DTO records: `WriteEnvelope`, `PatchEnvelope`, `ResponseEnvelope`, `ListEnvelope`. `@Valid @NotNull` on the envelope's inner field cascades Bean Validation into the wrapped DTO.
- Migrated H2 → PostgreSQL. Runtime dep `org.postgresql:postgresql` replaces `com.h2database:h2` + `spring-boot-h2console`. Dev DB `library` uses `ddl-auto=update`. Tests use `src/test/resources/application.properties` against `library_test` with `ddl-auto=create-drop`. `BookControllerTest` already calls `bookRepository.deleteAll()` between tests, so no further isolation needed.
- Externalised env-sensitive config to `.env` (gitignored) loaded via `spring.config.import=optional:file:./.env[.properties]`. `.env.example` committed as template. `application.properties` keys with `${VAR:default}` placeholders cover DB host/port/name/user/password, `ddl-auto`, `show-sql`, log levels, structured-log format. Test props share the same import and read `DB_NAME` too — caller passes `DB_NAME=library_test` when running tests (`ddl-auto=create-drop` stays pinned in the test file to keep schema management hermetic).
- Phase 1 of multi-table model: `books` gains `isbn` (NOT NULL, ISBN-10/13 `@Pattern`, immutable after creation), `created_at`, `updated_at`, `deleted_at`. Soft delete via Hibernate `@SQLDelete` (UPDATE deleted_at) + `@SQLRestriction("deleted_at IS NULL")` (every find filters out tombstones). Partial unique index on ISBN (`WHERE deleted_at IS NULL`) declared in `src/main/resources/import.sql` because JPA `@UniqueConstraint` can't express WHERE clauses. DTO split: `WriteRequest` (POST, with `isbn`) vs `UpdateRequest` (PUT, no `isbn`) — immutability enforced by wire-contract construction. `Response` exposes `isbn`/`created_at`/`updated_at`; `deleted_at` stays internal.
- Phase 2 (schema-only): `staffs` table — `id`, `name`, `email`, `password_hash`, `created_at`, `updated_at`, `deleted_at`. Same audit + `@SQLDelete`/`@SQLRestriction` pattern as `books`. Partial unique index on `email` (active rows only) added to `import.sql`. **No repository / service / controller / DTO yet** — entity exists so Hibernate creates the table; CRUD is intentionally out of scope for this phase.
- Phase 3 (schema-only): `members` table — identical column shape to `staffs` (`id`, `name`, `email`, `password_hash`, audit + soft-delete). Partial unique index on `email`. Entity only; no CRUD.
- Phase 4 (schema-only): `book_loans` table — `id`, `book_id` (FK → `books.id`), `member_id` (FK → `members.id`; renamed from the originally proposed `borrower_id` since only members borrow), `borrowed_at`, `returned_at` (nullable — null = still on loan), audit + soft-delete. Implemented as JPA `@ManyToOne(fetch = LAZY)` associations; Hibernate auto-generates the FK constraints. `BookEntity` and `MemberEntity` promoted from package-private to `public` so `loans/` can reference them. Entity only; no CRUD.

## Next

- CRUD endpoints for `staffs` / `members` / `book_loans` once schemas are stable.

## Deferred gaps (explicit follow-ups)

- **409 Conflict mapping for unique-constraint violations.** Today any duplicate hit on a partial-unique index (active ISBN in `books`, soon active email in `staffs` / `members`) bubbles up as a 500. Map `DataIntegrityViolationException` → 409 in `GlobalExceptionHandler`, ideally with a discriminator that surfaces which field collided. Until that lands, integration tests treat duplicate-active-key as "exception raised", not a specific HTTP status.
- **Partial-index verification tests.** No test today asserts that (a) inserting two active rows with the same `isbn`/`email` is rejected, or (b) inserting one after the prior soft-delete succeeds. The index runs (otherwise tests would have failed schema generation), but we have no positive regression coverage. Add when 409 mapping lands — easier to assert response code than raw exception type.
- **`import.sql` is a temporary scaffold.** Works because of `IF NOT EXISTS` on `ddl-auto=update`, but it's unversioned and bypasses the application's migration story. Replace with Flyway/Liquibase migrations when the schema stabilises (after Phase 4), and flip `ddl-auto` to `validate`.
- **Sorting on list endpoints.** `Pageable` is already wired; just need a `sort` query param on each list endpoint.
- **Password hashing.** `staffs.password_hash` (Phase 2) currently stores the plaintext value under the `_hash` column. Documented at the call site. Replace with `spring-security-crypto` + `BCryptPasswordEncoder` once a real auth flow lands. Migration: hash existing plaintext rows in-place during the deploy that adds the encoder.
