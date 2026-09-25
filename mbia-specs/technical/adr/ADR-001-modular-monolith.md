# ADR-001 — Modular monolith

**Status:** Accepted

## Context

Mbia is built by a very small team with substantial help from coding agents. The MVP must be simple to run, deploy and understand, while keeping business boundaries explicit.

## Decision

Build one Spring Boot application organised by business module (`identity`, `family`, `genealogy`, `memory`, `invitation`, `activity`, `shared`), with the internal layering described in `../architecture.md`. Module boundaries and dependency rules are enforced by automated architecture tests.

## Consequences

- One deployable, one database, local transactions.
- No message broker, no distributed transactions.
- A module can be extracted later only if a real scaling or organisational need appears (new ADR).
