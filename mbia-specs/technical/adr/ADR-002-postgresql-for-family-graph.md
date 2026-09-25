# ADR-002 — PostgreSQL instead of a graph database

**Status:** Accepted

## Context

The family graph is central, but the MVP only needs local traversal (parents, children, partners, siblings, first cousins) and cycle checks on families of tens to a few thousand Persons.

## Decision

Store Persons and explicit relationships (`PARENT_OF`, `PARTNER_OF`) in PostgreSQL. Traversal uses explicit SQL, recursive CTEs, or in-memory traversal of one Family's active relationships.

## Consequences

- One database to operate, back up and secure.
- Graph constraints that SQL cannot express (acyclicity) are enforced in the domain.
- If graph queries become a bottleneck, revisit with measurements (new ADR).
