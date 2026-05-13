.PHONY: help build run test clean compile watch deps format format-check

.DEFAULT_GOAL := help

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

build:
	./gradlew build

run:
	./gradlew bootRun

test:
	./gradlew test

clean:
	./gradlew clean

compile:
	./gradlew compileJava

watch:
	./gradlew compileJava --continuous

deps:
	./gradlew dependencies --configuration runtimeClasspath

format:
	./gradlew spotlessApply

format-check:
	./gradlew spotlessCheck
