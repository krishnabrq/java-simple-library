# AGENTS.md — Library

Canonical project context. Read this first; then `PROGRESS.md` (tasks) and `JOURNEY.md` (history). `CLAUDE.md` is a thin pointer here.

## Project

Spring Boot learning sandbox. Owner: Krishna (Java beginner, learning Spring Boot hands-on). Agent role: teaching companion, not just code generator. Not production — pedagogy over patterns.

## Agent behavior

- Teach: explain _why_, relate to plain Java when useful.
- One concept at a time. No profiles, security, AOP, caching, observability, etc. until needed.
- Catch compile-level basics (missing return, wrong import, primitive vs wrapper).
- Confirm before refactors or new abstractions.
- Update tracking files after meaningful milestones.
- No unrequested features. No speculative error handling. No premature abstractions.

## Stack

- Spring Boot 4.0.6, Java 25, Gradle.
- Starters: `web-mvc`, `data-jpa`, `validation`, `devtools`.
- PostgreSQL on `localhost:5432`. Connection details + log/DB toggles come from `.env` at the project root (loaded via `spring.config.import=optional:file:./.env[.properties]`). `.env.example` is committed as a template; `.env` is gitignored. Test DB name comes from the same `DB_NAME` env var — caller is responsible for pointing it at `library_test` before running tests (`DB_NAME=library_test ./gradlew test`).
- MapStruct 1.6.3 (compile-time entity ↔ DTO).
- Logging: SLF4J facade + Logback backend (default).

## Layout

```
com.training.library/
├── LibraryApplication.java          # entry point
├── books/                           # feature package — full CRUD
│   ├── BookController.java          # HTTP
│   ├── BookService.java             # logic + transactions
│   ├── BookRepository.java          # Spring Data JPA interface
│   ├── BookEntity.java              # @Entity
│   ├── BookView.java                # projection record (entity + activeLoanCount)
│   ├── BookDto.java                 # nested records: WriteRequest, UpdateRequest, PatchRequest, Response
│   ├── BookMapper.java              # MapStruct
│   ├── BookConflictException.java   # 409 (delete-with-loans, count-below-loans)
│   └── BookNotFoundException.java
├── users/                           # feature package — entity only (no CRUD yet)
│   ├── UserEntity.java              # @Entity — unified principal (role discriminates member vs staff)
│   ├── UserRepository.java
│   └── UserRole.java                # MEMBER | STAFF
├── loans/                           # feature package — borrow + return endpoints
│   ├── BookLoanEntity.java          # @Entity (FKs to BookEntity + UserEntity)
│   ├── BookLoanRepository.java      # countByBookIdAndReturnedAtIsNull (used by BookService)
│   ├── LoanController.java          # /api/v1/loans (borrow) + /{id}/return
│   ├── LoanService.java             # MEMBER-only enforced against live DB role
│   ├── LoanDto.java                 # BorrowRequest, Response + envelopes
│   ├── LoanMapper.java              # MapStruct (id-only projection of nested FKs)
│   ├── LoanNotFoundException.java   # 404 (also for "not your loan")
│   ├── LoanConflictException.java   # 409 (no copies, double-return)
│   └── LoanNotPermittedException.java  # 403 (STAFF trying to borrow/return)
├── auth/                            # feature package — security plumbing + endpoints
│   ├── SecurityConfig.java          # SecurityFilterChain + BCrypt + JWT encoder/decoder beans
│   ├── JwtService.java              # mints access + refresh tokens (HS256)
│   ├── AuthController.java          # /api/v1/auth/{signup,login,refresh,logout}
│   ├── AuthService.java             # signup/login/refresh logic, hashes passwords
│   ├── AuthDto.java                 # SignupRequest, LoginRequest, RefreshRequest, TokenResponse + envelopes
│   ├── EmailAlreadyExistsException.java
│   ├── InvalidCredentialsException.java
│   └── InvalidTokenException.java
└── common/                          # cross-cutting
	├── GlobalExceptionHandler.java  # @RestControllerAdvice
	└── ErrorResponse.java
```

## Layering (per feature)

Controller → Service → Repository.

| Layer      | Owns                                                         | Forbidden                      |
| ---------- | ------------------------------------------------------------ | ------------------------------ |
| Controller | `@RequestBody`, `@PathVariable`, `@Valid`, status, DTO shape | repo calls, business logic, tx |
| Service    | logic, `@Transactional`, orchestration                       | HTTP types, `ResponseEntity`   |
| Repository | Spring Data JPA queries                                      | anything non-persistence       |

Mapper converts DTO ↔ entity at controller boundary. Service returns entity; controller maps to `BookDto.Response`.

## Conventions

- Package-by-feature (`books/`, `staffs/`, `members/`, `loans/`). Not package-by-layer. Do not migrate.
- Cross-feature associations: a feature's `Entity` is `public` when other features need to reference it via `@ManyToOne` etc.; otherwise it stays package-private. Internals (DTOs, mappers, services) stay package-private.
- DTOs: group via interface namespace (`BookDto.WriteRequest` etc.). Records for DTOs.
- Exceptions: one-per-file (type-based `@ExceptionHandler` dispatch).
- Cross-cutting packages: `common/`, `config/`, `storage/`, `messaging/`. Don't pre-create empty ones.
- API prefix `/api/v1/...` via class-level `@RequestMapping`. Switch to `WebMvcConfigurer.addPathPrefix` when 2nd controller appears.
- `@Valid` on `@RequestBody`. Bean Validation on DTO fields + path/query params. PATCH DTOs use null-tolerant constraints only (no `@NotBlank`/`@NotNull`).
- Errors flow through `GlobalExceptionHandler`. Controllers throw domain exceptions, never inline HTTP error bodies.
- Wrapper types (`Integer`) in DTOs when "missing" must be distinguishable from primitive default — especially PATCH.
- Records can't be JPA entities (no no-arg ctor, immutable). Entities are regular classes; omit `setId`.
- `@Transactional(readOnly = true)` at service class; method-level `@Transactional` on writes.
- Constructor injection only. No `@Autowired` on fields.
- Formatting: Spotless + google-java-format. `make format` to apply, `make format-check` to verify. Also strips unused imports, normalises import order, forbids wildcard/module imports, runs CleanThat refactors.

## Logging

- Pattern per class: `private static final Logger log = LoggerFactory.getLogger(X.class);`
- Service writes → INFO. Service reads → DEBUG. `GlobalExceptionHandler` 4xx → DEBUG.
- 5xx (when they appear) → ERROR with throwable.
- Parameterized only: `log.info("created id={}", id)`. Never string-concat.
- Levels: `logging.level.<pkg>=<level>` in `application.properties`. Override at runtime: `--logging.level.X=DEBUG` or env `LOGGING_LEVEL_X=DEBUG`.
- Structured JSON (Spring Boot 3.4+, built-in, no extra dep): `logging.structured.format.console={ecs|gelf|logstash}`. Off by default.

## Running

Makefile wraps Gradle.

| Target               | Runs                                                         |
| -------------------- | ------------------------------------------------------------ |
| `make` / `make help` | list targets                                                 |
| `make run`           | `./gradlew bootRun`                                          |
| `make build`         | `./gradlew build`                                            |
| `make test`          | `./gradlew test`                                             |
| `make compile`       | `./gradlew compileJava`                                      |
| `make watch`         | `./gradlew compileJava --continuous` (pairs with `make run`) |
| `make clean`         | `./gradlew clean`                                            |
| `make deps`          | `./gradlew dependencies --configuration runtimeClasspath`    |
| `make format`        | `./gradlew spotlessApply` (auto-format Java sources)         |
| `make format-check`  | `./gradlew spotlessCheck` (verify formatting, fails on diff) |

- App: http://localhost:8080/api/v1/books
- Postgres dev DB: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` (defaults `localhost:5432/library`, user `postgres`). Schema auto-managed by Hibernate (`DB_DDL_AUTO=update` by default) until Flyway lands.
- Local env: copy `.env.example` to `.env` and edit. Shell env vars (`DB_NAME=library_test ./gradlew test`) override `.env` values via Spring Boot's property-source precedence.
- Hot reload: run `make run` and `make watch` in separate terminals. DevTools restarts the embedded server when class files change.

## Tracking files

| File          | Purpose                      | Update when                                   |
| ------------- | ---------------------------- | --------------------------------------------- |
| `AGENTS.md`   | canonical context            | stack / conventions / architecture changes    |
| `CLAUDE.md`   | thin pointer to `AGENTS.md`  | rarely                                        |
| `PROGRESS.md` | task list (done / next)      | after each completed task                     |
| `JOURNEY.md`  | concepts, decisions, gotchas | after teaching a new concept or hitting a bug |

Anti-drift: project info lives in `AGENTS.md` only. Delete duplicates that drift into `CLAUDE.md`.
