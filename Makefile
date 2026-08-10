# Copyright (c) 2026 ElatusDev
# make verify is the gate of record. There is no pipeline — see docs/guides/build-and-test.md.
SHELL := bash
.DEFAULT_GOAL := help
MVN := mvn -B

.PHONY: help verify build test arch mutation contract-check e2e up down keys clean

help:
	@grep -E '^[a-z-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-16s %s\n",$$1,$$2}'

verify: ## every gate: hygiene, compile, arch, unit, component, coverage, contract
	@$(MAKE) --no-print-directory guard-docker
	$(MVN) verify
	@$(MAKE) --no-print-directory contract-check

build: ## compile only
	$(MVN) compile

test: ## fast inner loop — unit + ArchUnit. WireMock adapter tests are *ComponentTest, so they run in verify
	$(MVN) test

arch: ## the structural suite alone
	$(MVN) test -Dtest='*ArchitectureTest' -DfailIfNoSpecifiedTests=false

mutation: ## PIT on domain + application. Slow; run deliberately, not in the commit loop
	@# test-compile first: the goal mutates whatever is in target/classes, so invoking it
	@# alone silently scores the PREVIOUS build and reports survivors you have already killed
	$(MVN) test-compile
	$(MVN) org.pitest:pitest-maven:mutationCoverage -DoutputFormats=XML,HTML
	@echo "report: target/pit-reports/index.html (survivors: target/pit-reports/mutations.xml)"

contract-check: ## the spec is valid, and no breaking change slipped into an existing operation
	@if [ ! -f src/main/resources/openapi/pokedex-api.yaml ]; then \
	  echo "contract-check: src/main/resources/openapi/pokedex-api.yaml does not exist yet (WU-000-B)"; exit 1; fi
	@command -v openapi-spec-validator >/dev/null || { echo "install: pipx install openapi-spec-validator"; exit 1; }
	openapi-spec-validator src/main/resources/openapi/pokedex-api.yaml
	@# oasdiff is required only when there is a tag to diff against. Demanding it before
	@# the first release made contract-check fail on a spec that is provably fine, and the
	@# check it gates could not have run anyway.
	@PREV=$$(git tag --sort=-v:refname | head -1); \
	 if [ -z "$$PREV" ]; then \
	   echo "contract-check: no previous tag; no existing operation to break yet"; else \
	   command -v oasdiff >/dev/null || { \
	     echo "oasdiff is required once a release exists: https://github.com/oasdiff/oasdiff"; exit 1; }; \
	   git show $$PREV:src/main/resources/openapi/pokedex-api.yaml > /tmp/prev-spec.yaml && \
	   oasdiff breaking /tmp/prev-spec.yaml src/main/resources/openapi/pokedex-api.yaml; fi

e2e: ## Newman against the running stack with seeded data
	@if [ ! -d e2e ]; then echo "e2e: collection does not exist yet (WU-999-A)"; exit 1; fi
	@command -v newman >/dev/null || { echo "install: npm i -g newman"; exit 1; }
	newman run e2e/pokedex-api.postman_collection.json --env-var baseUrl=http://localhost:8080/api

up: ## postgres + redis + api
	docker compose up --build

down: ## stop and keep data (use down-v to wipe)
	docker compose down

keys: ## generate the dev ES256 keystore (never committed)
	@mkdir -p keys
	@if [ -f keys/pokedex-dev.p12 ]; then echo "keys/pokedex-dev.p12 already exists"; else \
	  keytool -genkeypair -alias pokedex-dev -keyalg EC -groupname secp256r1 \
	    -sigalg SHA256withECDSA -validity 3650 -storetype PKCS12 \
	    -keystore keys/pokedex-dev.p12 -storepass "$${JWT_KEYSTORE_PASSWORD:-changeit-dev-only}" \
	    -dname "CN=pokedex-api-dev, O=ElatusDev, C=US" && echo "generated keys/pokedex-dev.p12"; fi

clean: ## clean build output
	$(MVN) clean

guard-docker:
	@docker info >/dev/null 2>&1 || { \
	  echo "make verify FAILS rather than skips when Docker is absent."; \
	  echo "The component tier needs a daemon — a skipped tier reporting green is worse than a red build (risk R8)."; \
	  exit 1; }
