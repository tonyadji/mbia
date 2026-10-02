# Mbia MVP — Technical Specifications

This directory complements the product specifications. Product behavior remains authoritative; these documents define the selected implementation constraints.

## Files

- `stack.md` — selected technology baseline and version policy.
- `architecture.md` — modular-monolith structure and code dependency rules.
- `data-model.md` — PostgreSQL relational model, constraints, indexes and transactional invariants.
- `genealogy.md` — Persons, relationships, tree and kinship: module structure, algorithms and query rules.
- `api/openapi.yaml` — contract-first REST API for the MVP.
- `adr/` — Architecture Decision Records (index in `adr/README.md`).

## Authority order

```text
Product specifications
        ↓
Technical specifications
        ↓
OpenAPI contract
        ↓
Automated tests
        ↓
Implementation
```

An implementation shortcut must not silently change a product rule.

## Agent rule

Agents may propose technical changes, but changes to framework, architectural style, persistence model semantics or public API require an explicit spec/ADR update.
