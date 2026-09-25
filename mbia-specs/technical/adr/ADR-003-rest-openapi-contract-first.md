# ADR-003 — Contract-first REST/OpenAPI

**Status:** Accepted

## Context

Backend and frontend may be built in parallel by different agents. They need a single, verifiable contract.

## Decision

`technical/api/openapi.yaml` is the HTTP contract. Server interfaces and the TypeScript client are generated from it. Generated code lives in dedicated directories and is never edited by hand. CI validates the document and detects breaking changes against the main branch.

## Consequences

- An API change starts with an OpenAPI change.
- Contract drift between backend and frontend becomes a compilation error.
- The frontend can work against a mock server generated from the contract.
