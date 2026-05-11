# Journey

A narrative log of what's been learned, decisions made, and mistakes worth remembering.

---

## 2026-05-11 — Day 0: Setup

**Where I'm starting from:** A few days of learning Java basics. First time touching Spring Boot.

**What I did:**

- Generated a Spring Boot 4.0.6 project on [start.spring.io](https://start.spring.io) with Gradle, Java 25, and these starters: Web MVC, Data JPA, H2, DevTools.
- Created a `Book` record with `id`, `title`, `count`.
- Stubbed out a `BookController` with a `@GetMapping("/books")` method.

**What I learned (so far):**

- A Spring Boot app entry point is a class annotated with `@SpringBootApplication` that calls `SpringApplication.run(...)`. The annotation is a meta-annotation bundling `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan` — meaning: "this is a config class, auto-configure based on what's on the classpath, and scan this package (and below) for components."
- `@RestController` = `@Controller` + `@ResponseBody`. Methods return data, not view names. Spring serializes the return value to JSON.
- `@GetMapping("/books")` maps HTTP `GET /books` to the method.

**Open question / mistake to fix next:**

- `BookController.list()` declares it returns `Book[]` but the body is empty. That's a compile error — every non-void path must return a value. Next session: return something (start with a hard-coded list, then move toward a real data source).

### Mid-session: First working endpoint

- Hit a second compile error: `new Book()` doesn't work because `Book` is a **record**. Records auto-generate a _canonical constructor_ taking all components — there's no implicit no-arg constructor like in regular classes. Fixed by calling `new Book(1L, "...", 3)`.
- Ran `./gradlew bootRun` — app starts, `GET /books` returns the JSON array. First Spring endpoint live.

### Next concept: API versioning

- Want endpoints under `/api/v1/books` so future breaking changes can live alongside the old API.
- Three approaches discussed:
  1. **Class-level `@RequestMapping("/api/v1/books")`** — cleanest start. Methods use bare `@GetMapping` and are relative to the class path.
  2. **`WebMvcConfigurer.configurePathMatch` + `addPathPrefix`** — one config class adds `/api/v1` to every `@RestController`. Best when there are several controllers.
  3. Header/query/subdomain versioning — exist, but URI versioning is the standard.
- Picked Option 1 for now. Will revisit Option 2 when there's a second controller.

### Mid-session: 404s, content negotiation, and project structure

- Hitting old `/books` URL in the browser showed Spring Boot's **"Whitelabel Error Page"** — its default HTML for unhandled errors. Important realization: Spring uses **content negotiation** on the `Accept` header — browsers get HTML, API clients sending `Accept: application/json` already get JSON.
- For consistent JSON errors regardless of client, the standard tool is `@RestControllerAdvice` + `@ExceptionHandler` (e.g. `NoResourceFoundException` for 404s). Skipped implementing it for now; noted for later.
- Took stock of project structure ahead of adding DB, configs, S3, queues, utilities. Validated current shape and settled on principles:
  - **Package by feature**, not by layer. `books/` holds controller + service + repository + entity together. Layered structure (`controller/`, `service/`, ...) is the older approach and not what to grow into.
  - Use **package-private** within a feature to keep its internals invisible to other features — a real encapsulation boundary that the layered structure can't give you.
  - **Shared infrastructure** (S3, queues) gets its own package (`storage/`, `messaging/`). Cross-cutting `@Configuration` classes go in `config/`. App-wide utilities go in `common/` — but only after a second consumer appears, to avoid the `util/` dumping ground.
  - **Don't pre-create empty packages.** Add a folder only when there's a real file for it.
  - Heads-up for later: JPA can't use Java `record`s as entities. When DB lands, `Book` becomes an `@Entity` class; a separate `BookResponse` record can keep the API shape decoupled from the DB shape.

### Gotcha: trailing whitespace in `.properties` files

While wiring up H2, hit a confusing error: `Cannot load driver class: org.h2.Driver`. The H2 jar was on the classpath — the real cause was trailing whitespace on the property value: `spring.datasource.driver-class-name=org.h2.Driver····` made Spring try to load `"org.h2.Driver    "` (with spaces) as a class.

**Why this happens:** Java `.properties` files preserve trailing whitespace as part of the value. YAML/TOML strip it; `.properties` doesn't.

**How to apply:** Enable "trim trailing whitespace on save" in your editor (e.g. `"files.trimTrailingWhitespace": true` in VS Code). When a Spring error says "cannot load class X" and X looks correct, suspect invisible characters in the config first.

### Mid-session: Persistence with JPA + H2

Adding write/read against an in-memory H2 database. Several new concepts at once:

- **JPA entity basics** — `@Entity` marks a class as a DB-mapped table; `@Id` is the primary key; `@GeneratedValue(strategy = IDENTITY)` lets the DB auto-assign ids. Package is `jakarta.persistence` (post-Jakarta EE migration), not the older `javax.persistence`.
- **Why `Book` had to stop being a record:** JPA reflectively constructs entities (needs a no-arg constructor) and sets fields after construction (needs mutability). Records are immutable and have no implicit no-arg ctor. Converted `Book` to a regular class with a public no-arg constructor, a convenience `(title, count)` constructor, getters, and setters — but **no `setId`**, so the API can't override the DB-assigned id.
- **Spring Data JPA repositories:** `BookRepository extends JpaRepository<Book, Long>` is the entire file. Spring generates the implementation at runtime — get `findAll`, `save`, `findById`, `deleteById`, etc. for free. Custom finder methods can be added later by naming convention (e.g. `findByTitleContainingIgnoreCase`).
- **Dependency injection — the core idea of Spring:** never write `new BookRepository(...)`. Spring sees `BookController`'s constructor takes a `BookRepository`, finds the matching bean, and passes it in. Used **constructor injection** (not `@Autowired` on fields) — supports `final` fields, makes unit testing trivial, dependencies are explicit. Old tutorials show field injection; ignore them.
- **`@RequestBody`** — Spring (via Jackson) deserializes the JSON request body into a `Book`. Calls the no-arg ctor + setters. Since `id` has no setter, malicious clients can't pre-assign ids.
- **H2 config** in `application.properties`:
  - `spring.datasource.url=jdbc:h2:mem:library` — in-memory; data lives only while app runs.
  - `spring.jpa.hibernate.ddl-auto=create-drop` — Hibernate creates schema from entities on startup, drops on shutdown. For real apps later: `none` + Flyway migrations.
  - `spring.jpa.show-sql=true` + `hibernate.format_sql=true` — every SQL Hibernate runs prints to console. Hugely valuable for understanding what JPA is doing under the hood.
  - `spring.h2.console.enabled=true` — browse the DB at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:library`, user `sa`, no password).

### Mid-session: Full CRUD + first global exception handler

Built out the remaining HTTP verbs on `/api/v1/books`. Several concepts landed:

- **`@PathVariable`** binds a URL segment (`/{bookId}`) to a method parameter. The variable name in the annotation must match the placeholder in the path (or pass `@PathVariable("bookId")` explicitly).
- **`Optional<T>` from `findById`** — Spring Data returns `Optional` rather than nullable values. `findById(id).orElseThrow(() -> new BookNotFoundException(id))` is the idiomatic pattern. Forces you to handle "not found" at the call site.
- **PUT vs PATCH — the conceptual heart of this step:**
  - **PUT** = full replacement. Client sends the entire new representation; missing fields default. Implementation: load existing, overwrite all fields, save.
  - **PATCH** = partial update. Client sends only the fields they want to change; missing fields mean "don't touch". Implementation needs a DTO with **nullable wrapper types** (`Integer`, not `int`) so the controller can distinguish "absent" from "set to 0".
- **`BookPatchRequest` record** — first proper DTO. Records are perfect here: immutable, no boilerplate. Records aren't usable as JPA entities (they need a no-arg constructor and mutability), but for request/response payloads they're ideal.
- **Custom domain exceptions** — `BookNotFoundException extends RuntimeException` knows nothing about HTTP. It's a _domain signal_. The HTTP translation lives elsewhere — see below.
- **`@RestControllerAdvice` + `@ExceptionHandler`** — created `common/GlobalExceptionHandler` (the first inhabitant of `common/`, which we'd predicted would house cross-cutting code). One `@ExceptionHandler(BookNotFoundException.class)` method turns the exception into a JSON 404. As more exception types appear, they all funnel through this one class — controllers stay clean.
- **`@ResponseStatus`** — declarative way to set the HTTP status on a method (e.g. 204 for DELETE, 201 for POST). Simpler than `ResponseEntity` when the status is fixed.
- **`@ResponseStatus(HttpStatus.NO_CONTENT)`** on a `void` DELETE method — REST convention: successful delete returns 204, no body.

**Key habit to internalize:** controllers should throw domain exceptions and never construct HTTP error responses inline. Push all HTTP-translation logic into the `@RestControllerAdvice`.

### Design principle: group by data, separate by behavior

Asked whether to consolidate book exceptions into one file and book DTOs into one file. Answer is different for each:

- **DTOs → group together** in a feature namespace, e.g. `BookDto` as an interface with nested records: `BookDto.PatchRequest`, `BookDto.Response`. Reasons: DTOs are tiny data shapes, often evolve together, and the namespacing (`BookDto.X`) makes call sites clearer. Interface (vs. final class) is the cleaner idiom — members are implicitly `public static`.
- **Exceptions → one per file.** Reasons: exceptions are dispatched **by type** (`@ExceptionHandler(BookNotFoundException.class)`). Collapsing them into one class with a `kind` enum throws away type-based routing and forces switch-on-enum logic. Different exceptions also tend to carry different data. Sealed class hierarchies are a legitimate middle ground (Java 17+) but overkill for now.

**The principle:** group by data, separate by behavior.

### Global "not found" handler

Earlier deferred — now added. Two handlers in `GlobalExceptionHandler`:

- `BookNotFoundException` — thrown by the controller for a missing book id.
- `NoResourceFoundException` (`org.springframework.web.servlet.resource`) — Spring throws this when no route matches any request. Adding a handler for it replaces Spring's Whitelabel HTML page with consistent JSON 404s for _all_ clients, not just those sending `Accept: application/json`.

Extracted a private `notFound(message)` helper at the same time. Two consumers of the same shape = the right moment to DRY it (any sooner would be premature).

### Mid-session: Input validation (Bean Validation / Jakarta Validation)

Took validation seriously. Two distinct validation scenarios, both end in HTTP 400 but use different mechanisms:

1. **Path/query parameter conversion** (`/books/hhjg` where bookId is `Long`) — Spring fails the conversion _before_ the controller runs and throws `MethodArgumentTypeMismatchException`. Caught in `GlobalExceptionHandler`.
2. **Body content validation** (title length, count range, missing required fields) — done with **Bean Validation** annotations + `@Valid` on the `@RequestBody` parameter. Spring runs constraints before calling the method; on failure throws `MethodArgumentNotValidException`. Caught in `GlobalExceptionHandler` and converted to a 400 with a `errors[]` array of `{field, message}` entries.
3. **(Bonus)** Malformed JSON throws `HttpMessageNotReadableException`. Also handled, also 400.

**Key annotation behaviors to remember:**

- `@NotBlank` — string must be non-null AND non-empty AND not whitespace-only. Used on POST/PUT required fields.
- `@NotNull` — value must not be null. Used **with wrapper types** (`Integer`), not primitives — `int` can't be null and `@NotNull` is meaningless there.
- `@Size`, `@PositiveOrZero`, `@Min`, `@Max`, `@Positive`, `@Negative` — **null-tolerant**: they skip null values rather than failing. Perfect for PATCH where fields are optional but must be valid when present.
- Use `Integer` (not `int`) on DTO fields when you need to distinguish "not sent" (null) from "sent as 0".

**Architectural moves at the same time:**

- POST and PUT now take `BookDto.WriteRequest` (a DTO with validation) instead of `BookEntity`. The API DTO and the persistence entity are now separate types. This is the right shape long-term: validation on DTOs, persistence concerns on entities.
- Created `common/ErrorResponse` record. With 5 exception handlers now returning the same JSON shape, extracting a typed record was overdue. Bonus: records preserve component order in the JSON output (`Map.of(...)` doesn't — it's an unordered map and Jackson would emit fields in hash order).
- Refactored `GlobalExceptionHandler` to use `ErrorResponse.of(...)` static factories. Two private helpers — `notFound(...)` and `badRequest(...)` — make each new exception handler a one-liner.

### Mid-session: Parameter-level validation + service layer

**Parameter validation (path variables).** Spring 6.1+ auto-runs Bean Validation constraints on `@PathVariable`/`@RequestParam` — no `@Validated` on the class needed. The thrown type is `HandlerMethodValidationException` (distinct from `MethodArgumentNotValidException`, which is for `@RequestBody` validation). Added `@Min(1)` on every `bookId` to demo. **The real value of this lands later:** when `bookId` migrates to a Stripe-style string id (`book_abc123`), `@Pattern(regexp = ...)` is the _only_ thing standing between malformed ids becoming 400s vs. quietly becoming 404s — because any string is a valid `String`, so the type-mismatch handler never fires.

Caught an API surprise while wiring this up: the method is `getParameterValidationResults()` (not `getAllValidationResults()`). Worth remembering as a debugging hint: when Spring throws an exception type you haven't seen, `javap` on its class in the jar is fast and authoritative.

**Service layer (`BookService`).** Pulled all business logic — find/throw/mutate/save — out of the controller. New layering:

- **Controller**: HTTP concerns only (`@RequestBody`, `@PathVariable`, `@Valid`, status codes). One-line method bodies that delegate to the service.
- **Service**: business rules, transaction boundaries, orchestration of the repository (and future collaborators).
- **Repository**: persistence (Spring Data JPA).

Key annotations:

- **`@Service`** — Spring stereotype identical in behavior to `@Component`, semantically signals "business logic layer". Picked up by component scanning.
- **`@Transactional`** — Spring AOP wraps the method in a DB transaction. On `RuntimeException` (e.g. `BookNotFoundException`), the transaction rolls back. Essential for read-modify-write flows so the whole sequence is atomic.
- **`@Transactional(readOnly = true)` at class level** — defaults reads to read-only transactions (Hibernate skips dirty-checking → small perf + documents intent). Each write method overrides with method-level `@Transactional`. Idiomatic Spring pattern worth keeping.

**Honest compromise documented for future-me:** the service currently accepts `BookDto.WriteRequest` and `BookDto.PatchRequest` directly. The "pure" approach is to translate request DTOs into internal command types (`CreateBookCommand`, etc.) at the controller boundary, so the service is API-shape-agnostic. Premature for a single-controller app; the signal to split is when a second caller (CLI, GraphQL, scheduled job) needs to invoke the service.

### Mid-session: Response DTO + MapStruct + agent handoff doc

**`BookDto.Response`** added. The controller now returns `BookDto.Response` (or `List<...>`), not `BookEntity`. The win: if `BookEntity` gains internal fields (e.g. `createdAt`, `internalNotes`), the JSON contract stays stable. **The entity stops being part of the public API.**

**MapStruct (`BookMapper`).** Compile-time annotation processor that _generates_ the mapping code. The interface declares the shapes; the build produces a `BookMapperImpl` class that's plain getter→setter Java — no reflection, debuggable, JIT-friendly. Key methods used:

- `toResponse` / `toResponses` — entity → response shape (the list variant is auto-generated by delegating to the single-item method).
- `toEntity(WriteRequest)` — POST. `@Mapping(target = "id", ignore = true)` so the DB owns id assignment.
- `updateFromWriteRequest(@MappingTarget entity, WriteRequest)` — PUT: full overwrite, id preserved.
- `updatePatch(@MappingTarget entity, PatchRequest)` — PATCH. The trick: `@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)` makes nulls mean "leave the target field alone" — replaces the manual `if (patch.field() != null)` chain we had in the service.

**Why MapStruct over ModelMapper / hand-written:**

- Compile-time field-name checking: typos fail the build, not at 3am in prod.
- Zero runtime reflection — the generated code is the code you'd write by hand.
- `componentModel = "spring"` makes the generated impl a `@Component`; constructor injection works as usual.

**When MapStruct is NOT worth it:** 1–2 field DTOs. Manual mapping is fine and obvious. The honest "add MapStruct" signal: "I've copy-pasted the same mapping for the third time, or my Book class is up to 15 fields and the manual mapper is half my service file."

Worth knowing also: MapStruct works fine with Java 25 / Spring Boot 4.0.6 even though the official compat matrix may lag behind the latest JDKs. The annotation processor uses standard JSR-269 APIs.

### Mid-session: Agent handoff doc + anti-drift split

Created `AGENTS.md` as the **canonical** project handoff doc (tool-neutral — any AI agent can read it and onboard). It contains: stack, architecture, conventions, agent role, tracking-file responsibilities.

**Anti-drift design:** `CLAUDE.md` shrank to a _thin bootstrap_ that says "read AGENTS.md" plus a placeholder for Claude-only notes. No project info in CLAUDE.md anymore. Single source of truth = no drift possible. Whenever conventions or stack change, only one file needs updating.

The mental model going forward:

| File          | Role                                                                     |
| ------------- | ------------------------------------------------------------------------ |
| `AGENTS.md`   | Canonical project context. Stack, architecture, conventions, agent role. |
| `CLAUDE.md`   | Thin pointer to AGENTS.md + Claude-only quirks (currently empty).        |
| `PROGRESS.md` | Live task list. What's done, what's next.                                |
| `JOURNEY.md`  | Chronological learning log. Concepts, decisions, gotchas.                |

---

_Future sessions will add new dated entries below._
