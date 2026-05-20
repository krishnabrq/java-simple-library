.PHONY: help build run test clean compile watch deps format format-check \
	checkstyle pmd quality ci-verify \
	db-update db-status db-history db-validate db-rollback-count db-rollback-tag \
	db-tag db-clear-checksums db-changelog-sync db-drop-all db-diff \
	db-generate-migration \
	docker-build docker-run docker-up docker-down docker-restart \
	docker-logs docker-ps docker-shell docker-clean

# Auto-load .env if present so every target (including liquibase) inherits the
# same DB_* / JWT_SECRET / LOG_* values Spring reads at runtime. Make's `include`
# parses .env as a Makefile — fine for `KEY=VALUE` and `#` comments, breaks on
# multiline / quoted values. `export` propagates these to child processes.
ifneq (,$(wildcard .env))
include .env
export
endif

build: ## Compile the project and run all tests
	./gradlew build

run: ## Start the Spring Boot app via bootRun
	./gradlew bootRun

test: ## Run JUnit tests + Jacoco report
	./gradlew test

clean: ## Delete build/ output
	./gradlew clean

compile: ## Compile main sources only (no tests)
	./gradlew compileJava

watch: ## Continuous recompile on source change
	./gradlew compileJava --continuous

deps: ## Print resolved runtime classpath
	./gradlew dependencies --configuration runtimeClasspath

format: ## Apply Spotless / google-java-format
	./gradlew spotlessApply

format-check: ## Verify formatting without changing files
	./gradlew spotlessCheck

# ----------------------------------------------------------------------------
# Code quality — Checkstyle, PMD, SonarQube.
#
# These tasks are GATED by the `-Pci` Gradle property. Day-to-day `make build`
# does NOT run them so the local loop stays fast. CI passes `-Pci` to enable
# them. You can opt in locally with the targets below.
# ----------------------------------------------------------------------------

checkstyle: ## Run Checkstyle on main + test sources (locally opts into -Pci)
	./gradlew checkstyleMain checkstyleTest -Pci

pmd: ## Run PMD on main + test sources (locally opts into -Pci)
	./gradlew pmdMain pmdTest -Pci

quality: ## Run Spotless + Checkstyle + PMD locally (same set CI enforces)
	./gradlew spotlessCheck checkstyleMain checkstyleTest pmdMain pmdTest -Pci

ci-verify: ## Full CI gauntlet: format, lint, test, build (no Docker, no push)
	./gradlew spotlessCheck checkstyleMain checkstyleTest pmdMain pmdTest test build -Pci

# ----------------------------------------------------------------------------
# Liquibase — schema migrations.
#
# Two ways to invoke Liquibase against this project:
#   1. Spring runtime  — `make run` applies pending changesets on app startup
#      (controlled by spring.liquibase.change-log in application.properties).
#   2. Gradle plugin   — the `db-*` targets below run Liquibase as a CLI tool
#      against the same DB the app uses. Connection comes from DB_* env vars
#      auto-loaded from .env at the top of this Makefile.
# ----------------------------------------------------------------------------

db-update: ## Apply all pending changesets to the database
	./gradlew update

db-status: ## Show which changesets have not yet been applied
	./gradlew status

db-history: ## List every changeset ever applied to this database
	./gradlew history

db-validate: ## Verify the changelog parses and checksums are intact
	./gradlew validate

db-rollback-count: ## Roll back the last N changesets (usage: make db-rollback-count N=1)
	./gradlew rollbackCount -Dliquibase.command.count=$(N)

db-rollback-tag: ## Roll back to a tagged checkpoint (usage: make db-rollback-tag TAG=v1)
	./gradlew rollback -Dliquibase.command.tag=$(TAG)

db-tag: ## Tag the current DB state for later rollback (usage: make db-tag TAG=v1)
	./gradlew tag -Dliquibase.command.tag=$(TAG)

db-clear-checksums: ## Reset stored checksums; next `update` recomputes them
	./gradlew clearChecksums

db-changelog-sync: ## Mark all changesets as applied without running them (use for existing DBs)
	./gradlew changelogSync

db-drop-all: ## DESTRUCTIVE — drop every object Liquibase manages in the DB
	./gradlew dropAll

db-diff: ## Print schema diff (JPA entities vs live DB) to the console
	./gradlew diff -PliquibaseRunList=diff --info

db-generate-migration: ## Generate YAML changeset from JPA-vs-DB diff (usage: make db-generate-migration NAME=add-author)
	@if [ -z "$(NAME)" ]; then \
		echo "Error: NAME=<migration-name> is required."; \
		echo "Example: make db-generate-migration NAME=add-author-to-books"; \
		exit 1; \
	fi
	@mkdir -p src/main/resources/db/changelog/changesets
	@ts=$$(date +%Y%m%d%H%M%S); \
	out="src/main/resources/db/changelog/changesets/$${ts}-$(NAME).yaml"; \
	echo "Writing diff to $${out}"; \
	./gradlew diffChangelog -PliquibaseRunList=diff -PoutputChangelog=$${out} && \
	echo "" && \
	echo "  Generated: $${out}" && \
	echo "  Next steps:" && \
	echo "    1. Open the file and review/edit the changes" && \
	echo "    2. Add an 'include:' entry for it in db.changelog-master.yaml" && \
	echo "    3. Run 'make db-update' to apply"

# ----------------------------------------------------------------------------
# Docker — containerized build and run.
#
# Two flavors:
#   1. `docker-build` / `docker-run` — build and run the app image standalone
#      (you must point DB_HOST at a Postgres reachable from inside the container).
#   2. `docker-up` — bring up the full stack (Postgres + app) via docker compose
#      using values from .env. This is the easiest way to run the app locally.
# ----------------------------------------------------------------------------

docker-build: ## Build the app Docker image (multi-stage, layered)
	docker build -t library-app:latest .

docker-run: ## Run the app container standalone on :8080 (needs an external Postgres)
	docker run --rm -it \
		--name library-app \
		-p 8080:8080 \
		--env-file .env \
		library-app:latest

docker-up: ## Start the full stack (Postgres + app) via docker compose
	docker compose up -d --build

docker-down: ## Stop the docker compose stack (keeps the Postgres volume)
	docker compose down

docker-restart: ## Restart only the app container (faster than full rebuild)
	docker compose restart app

docker-logs: ## Tail logs from the app container
	docker compose logs -f app

docker-ps: ## Show running containers in the compose stack
	docker compose ps

docker-shell: ## Open a shell inside the running app container
	docker compose exec app sh

docker-clean: ## DESTRUCTIVE — remove containers, image, and the Postgres volume
	docker compose down -v
	-docker rmi library-app:latest
