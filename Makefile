.PHONY: help build run test clean compile watch deps format format-check

.DEFAULT_GOAL := help

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

build: ## Compile, run tests, assemble jar
	./gradlew build

run: ## Boot the app — http://localhost:8080
	./gradlew bootRun

test: ## Run tests only
	./gradlew test

clean: ## Delete build outputs (build/, .gradle caches)
	./gradlew clean

compile: ## Compile main sources only (skip tests)
	./gradlew compileJava

watch: ## Auto-recompile on file changes (pairs with `make run` in another terminal)
	./gradlew compileJava --continuous

deps: ## Print runtime dependency tree
	./gradlew dependencies --configuration runtimeClasspath

format: ## Auto-format Java sources (Spotless + google-java-format)
	./gradlew spotlessApply

format-check: ## Verify formatting without modifying files (CI-style)
	./gradlew spotlessCheck
