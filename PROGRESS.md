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

## Next

- First test: `@WebMvcTest` (controller), `@DataJpaTest` (repo), or `@SpringBootTest` (end-to-end).
- Pagination + sorting on list (`Pageable`).
- Flyway migrations; switch `spring.jpa.hibernate.ddl-auto` off `create-drop`.
