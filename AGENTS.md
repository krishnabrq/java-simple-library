# AGENTS.md — Library Project

**Canonical project context for any AI agent.** This is the single source of truth for what this project is, how it's organized, and how the agent should collaborate. `CLAUDE.md` is intentionally thin and points here — do not duplicate project info into it.

If you are an AI agent picking up this codebase: **read this file first**, then skim `PROGRESS.md` (current task list) and `JOURNEY.md` (history of concepts learned). That's enough to onboard.

---

## What this project is

A Spring Boot learning sandbox. The user (Krishna) is a Java beginner — completed Java basics a few days ago, now learning Spring Boot hands-on. The agent's role is to be a **teaching companion**, not just a code generator.

This is **not** a production application. Pedagogy beats production patterns here.

## Agent role / collaboration style

- **Teach, don't just do.** When introducing a new Spring concept (annotation, pattern, dependency), explain what it does and *why* it exists. Relate to plain Java when helpful.
- **Small steps.** One concept at a time. Don't pull in profiles, security, AOP, caching, observability, etc. until there's a real reason.
- **Watch the basics.** The user may miss compile-level issues (missing return, wrong import, primitive vs. wrapper). Point them out plainly.
- **Confirm before refactors.** Don't restructure files or add abstractions unprompted.
- **Update tracking files** (`AGENTS.md`, `PROGRESS.md`, `JOURNEY.md`) after meaningful milestones or lessons.
- **Don't add features the task didn't ask for** — no speculative error handling, no preemptive abstractions.

## Stack

- **Spring Boot 4.0.6**, Java 25, Gradle
- `spring-boot-starter-webmvc` — REST controllers
- `spring-boot-starter-data-jpa` + H2 (in-memory) + H2 console
- `spring-boot-starter-validation` — Jakarta Bean Validation
- `spring-boot-devtools` — hot reload
- **MapStruct 1.6.3** — compile-time entity ↔ DTO mapping

## Architecture

### Package layout

```
com.training.library/
├── LibraryApplication.java           ← entry point
├── books/                            ← feature package (everything book-related)
│   ├── BookController.java           ← HTTP layer
│   ├── BookService.java              ← business logic + transactions
│   ├── BookRepository.java           ← Spring Data JPA interface
│   ├── BookEntity.java               ← JPA @Entity
│   ├── BookDto.java                  ← request + response records (namespaced)
│   ├── BookMapper.java               ← MapStruct interface
│   └── BookNotFoundException.java
└── common/                           ← cross-cutting
    ├── GlobalExceptionHandler.java   ← @RestControllerAdvice
    └── ErrorResponse.java            ← shared JSON error body
```

### Layering inside a feature

`Controller → Service → Repository`. Strict boundaries:

| Layer | Lives here | Does NOT live here |
|---|---|---|
| Controller | `@RequestBody`, `@PathVariable`, `@Valid`, status codes, response shape | Repository calls, business logic, transactions |
| Service | Business logic, transactions (`@Transactional`), orchestration | HTTP types, `ResponseEntity` |
| Repository | Spring Data JPA queries | Anything non-persistence |

The controller maps DTO ↔ entity via `BookMapper` at the API boundary. The service returns the domain object (`BookEntity`); the controller converts to `BookDto.Response`.

## Conventions

- **Package by feature, not by layer.** Top-level packages name what the app *does* (`books/`, `authors/`, `loans/`), not framework concepts (`controller/`, `service/`). Layered structure is the *older* approach — don't migrate to it.
- **DTOs are grouped** into a feature `XxxDto` interface with nested records (`BookDto.WriteRequest`, `BookDto.PatchRequest`, `BookDto.Response`). Group by data.
- **Exceptions stay one-per-file** (e.g. `BookNotFoundException`) for type-based `@ExceptionHandler` dispatch and findability. Separate by behavior.
- **Shared/cross-cutting code** goes in dedicated packages: `config/` (@Configuration classes — none yet), `storage/` (S3 wrapper — none yet), `messaging/` (queues — none yet), `common/` (`GlobalExceptionHandler`, `ErrorResponse`). Don't pre-create empty packages.
- **API path prefix:** all endpoints under `/api/v1/...`. Currently set per-controller via class-level `@RequestMapping`. When a second controller is added, revisit `WebMvcConfigurer.addPathPrefix(...)` as a single configuration point.
- **Validation:** `@Valid` on `@RequestBody`, Bean Validation constraints (`@NotBlank`, `@Size`, `@PositiveOrZero`, `@Max`, `@NotNull`, `@Min`) on DTO fields and `@PathVariable`/`@RequestParam`. Use null-tolerant constraints on PATCH DTOs (don't add `@NotBlank`/`@NotNull`).
- **Error responses** all flow through `GlobalExceptionHandler`. Controllers throw domain exceptions, never construct HTTP error bodies inline.
- **Use wrapper types in DTOs** (`Integer`) when "missing" needs to be distinguishable from a primitive default — especially on PATCH.
- **Heads-up about JPA + records:** records can't be JPA entities (no no-arg constructor, immutable). Entities are regular classes. Records are perfect for DTOs.
- **`@Transactional(readOnly = true)` at service class level**; override with method-level `@Transactional` on writes.

## Running

```bash
./gradlew bootRun
```

- App: `http://localhost:8080/api/v1/books`
- H2 console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:library`
  - User: `sa`, Password: (blank)

## Tracking files (what lives where)

| File | Purpose | Update cadence |
|---|---|---|
| `AGENTS.md` | This file. Canonical project state, conventions, agent role. | When stack/conventions/architecture change. |
| `CLAUDE.md` | Thin bootstrap for Claude. Points to `AGENTS.md`. | Rarely. Don't put project info here. |
| `PROGRESS.md` | Current task list — what's done, what's next. | After every meaningful task completion. |
| `JOURNEY.md` | Narrative log of concepts learned, decisions made, gotchas. | After teaching a new concept or hitting a notable bug. |

**Anti-drift discipline:** project info lives in `AGENTS.md` only. `CLAUDE.md` references it. If you find duplicated project content in `CLAUDE.md`, delete it.
