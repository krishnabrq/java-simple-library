# Progress

A running checklist of what's built and what's next.

## Done

- [x] Project scaffolded via [start.spring.io](https://start.spring.io) with Web MVC, JPA, H2, DevTools.
- [x] `Book` record created (`id: long`, `title: String`, `count: int`).
- [x] `BookController` with `@RestController` and `/books` GET mapping.
- [x] `list()` returns a hard-coded `Book[]`; app runs and returns JSON.

## In progress

_Nothing — pick the next concept._

## Done (continued)

- [x] Versioned the API: `BookController` now mounted at `/api/v1/books` via class-level `@RequestMapping`.
- [x] Discussed and settled on **package-by-feature** structure (deferred `common/`, `config/`, `storage/`, `messaging/` packages until needed).
- [x] **Persistence with JPA + H2:** `BookEntity`, `BookRepository`, H2 in-memory + console + SQL logging.
- [x] **Full CRUD:**
  - `GET /api/v1/books` — list
  - `GET /api/v1/books/{id}` — read one (404 via `BookNotFoundException`)
  - `POST /api/v1/books` — create (201 Created)
  - `PUT /api/v1/books/{id}` — full replace
  - `PATCH /api/v1/books/{id}` — partial update via `BookPatchRequest` DTO with nullable fields
  - `DELETE /api/v1/books/{id}` — delete (204 No Content; 404 if missing)
- [x] **`common/GlobalExceptionHandler`** — first cross-cutting class in `common/`; turns `BookNotFoundException` into a JSON 404.
- [x] **Global "no route" handler** — `NoResourceFoundException` mapped to JSON 404 (replaces Whitelabel HTML page).
- [x] **Input validation:** added `spring-boot-starter-validation`. `BookDto.WriteRequest` (POST/PUT) uses `@NotBlank @Size`/`@NotNull @PositiveOrZero @Max`; `BookDto.PatchRequest` uses null-tolerant constraints only. POST/PUT no longer leak `BookEntity` as the request shape.
- [x] **`common/ErrorResponse` record** — shared JSON error body shape; preserves field order (vs. unordered `Map.of`).
- [x] **Three new exception handlers:** `MethodArgumentNotValidException` (body validation, field errors), `MethodArgumentTypeMismatchException` (path/query type conversion), `HttpMessageNotReadableException` (malformed JSON). All 400.
- [x] **Parameter-level validation:** `@Min(1)` on `bookId` path variables; `HandlerMethodValidationException` handler for `@PathVariable`/`@RequestParam` constraint failures.
- [x] **Service layer (`BookService`):** controller is now thin (HTTP only); business logic + repository calls + transaction boundaries moved into the service. Class-level `@Transactional(readOnly = true)` with method-level `@Transactional` on writes.
- [x] **`BookDto.Response`** record — API shape decoupled from the entity. Controller now returns `BookDto.Response`/`List<BookDto.Response>`.
- [x] **MapStruct (`BookMapper`)** — compile-time entity ↔ DTO mapping. Five methods: `toResponse`, `toResponses`, `toEntity` (POST), `updateFromWriteRequest` (PUT), `updatePatch` (PATCH with `NullValuePropertyMappingStrategy.IGNORE`). Generated `BookMapperImpl` is plain getter→setter code, no reflection.
- [x] **`AGENTS.md`** — canonical, tool-neutral project handoff doc. `CLAUDE.md` shrunk to a thin bootstrap that points at `AGENTS.md` (anti-drift: project info lives in one file only).
- [x] **Entity-level constraints + explicit table name.** `BookEntity` now has `@Table(name = "books")`, `@NotBlank @Size(1, 1000)` + `@Column(nullable = false, length = 1000)` on title, `@Min(0) @Max(100_000)` + `@Column(nullable = false)` on count. DTO bounds updated to match (1000 / 100_000). DDL emits `NOT NULL`, `VARCHAR(1000)`, and a `CHECK` constraint on `count`.
- [x] **`Makefile`** — self-documenting shortcuts for the common Gradle calls (`make run`, `make build`, `make test`, `make watch`, `make clean`, `make compile`, `make deps`). `make` with no args lists targets via comments.

## Next up (suggested path)

- [ ] Code formatter — Spotless + google-java-format in `build.gradle`.
- [ ] First integration test (`@SpringBootTest` end-to-end, or `@DataJpaTest` for the repository, or `@WebMvcTest` for the controller).
- [ ] Pagination + sorting on the list endpoint (`Pageable` parameter).
- [ ] Consider migrations (Flyway) and switch `spring.jpa.hibernate.ddl-auto` away from `create-drop`.
- [ ] First integration test with `@SpringBootTest` / `@DataJpaTest`.
- [ ] Add pagination/sorting to the list endpoint (`Pageable` parameter).
- [ ] Add `POST /books` to create a new book.
- [ ] Wire JPA: turn `Book` into a `@Entity` and use a `JpaRepository`.
- [ ] Enable H2 console and view the data.
- [ ] Add validation (e.g. non-empty title, non-negative count).
- [ ] Write a first integration test for the controller.

_(Path is flexible — adjust based on what Krishna wants to learn next.)_
