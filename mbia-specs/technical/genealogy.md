# Mbia MVP — Genealogy Module

**Version:** 0.1  
**Status:** Draft

This document narrows `architecture.md` and `data-model.md` for the `genealogy` module: Persons, relationships, tree and kinship. It does not introduce a new architecture. Product rules stay in `product/`.

## 1. Module boundary

All Person, relationship, tree and kinship code belongs to:

```text
com.lehnade.mbia.genealogy
├── domain
├── application
├── infrastructure
└── api
```

The module may use stable application-level capabilities of other modules, especially:

```text
family.application.FamilyAccess
identity.application.CurrentUserAccessor
```

It must not depend on `family.domain` or `identity.domain`.

## 2. Domain model

Recommended domain types:

```text
Person
PersonId
PersonStatus
Gender
PartialDate
DatePrecision
FamilyRelationship
RelationshipId
RelationshipType
RelationshipStatus
KinshipCode
KinshipPath
KinshipPathStep
RelationshipWarning
```

`PartialDate` owns the consistency rules of EXACT / YEAR_ONLY / UNKNOWN (`data-model.md` §9).

`FamilyRelationship` owns the rules that need no graph traversal: self-relation and partner canonicalisation.

Graph-wide rules belong in explicit domain/application services, not in JPA entities.

## 3. Repository and query ports

Use explicit ports. At minimum:

```text
PersonRepository
RelationshipRepository
PersonSearchQuery
TreeQuery
PersonHistoryQuery
```

A graph-heavy read model may use SQL/projections. Do not force tree or kinship traversal through lazy ORM object graphs.

## 4. Use cases

One use case per operation:

```text
CreatePersonUseCase
GetPersonUseCase
SearchPersonsUseCase
UpdatePersonUseCase
ClaimPersonUseCase
UnclaimPersonUseCase
ArchivePersonUseCase
RestorePersonUseCase
MergePersonsUseCase
GetPersonHistoryUseCase
CreateRelationshipUseCase
ArchiveRelationshipUseCase
RestoreRelationshipUseCase
ListArchivedPersonRelationshipsUseCase
GetFamilyTreeUseCase
ResolveKinshipUseCase
```

No generic `PersonService` or `GenealogyService`.

## 5. Use-case ordering

For a family-scoped operation:

```text
1. obtain current User
2. FamilyAccess: verify ACTIVE membership / required role
3. load family-scoped resource(s)
4. verify resource state + optimistic version where applicable
5. execute domain rule
6. persist atomically
7. append audit when required
8. map response
```

The access check happens before revealing whether a Person or relationship exists. Cross-family and inaccessible resource probes return 404 (Family isolation rule).

## 6. Persistence

Separate domain and JPA models:

```text
Person                 <-> PersonJpaEntity
FamilyRelationship     <-> FamilyRelationshipJpaEntity
```

Spring Data interfaces stay inside `infrastructure.persistence`.

`PARTNER_OF` endpoints may arrive in either order; canonicalise before storage (`data-model.md` §11.2) with the same UUID ordering in Java and in the database check.

## 7. Cycle detection

Creating or restoring `PARENT_OF(parent, child)` is rejected when a path of ACTIVE `PARENT_OF` relations already exists from `child` to `parent`.

The check may use a recursive PostgreSQL CTE behind a query port. Do not load the whole Family graph into memory only to detect cycles.

The check and the insert/restore run in one transaction. Database uniqueness remains the final guard against duplicate races.

## 8. Relationship warnings

Warnings are pure deterministic calculations from the current birth data of both Persons. Rules and thresholds: `product/domain/person-relationships-collaboration.md` §7.1.

Warnings are not persisted. They are returned with the relationship attempt as defined by OpenAPI.

## 9. Kinship resolver

Kinship is a read-time graph calculation over ACTIVE Persons and relationships (never stored, `data-model.md` §12).

Use a breadth-first traversal, since the shortest path is required. Neighbour ordering is deterministic so that tie-breaking and tests are stable (`product/domain/person-relationships-collaboration.md` §10).

Among shortest paths made of the same kind of steps, the chosen one is the path whose sequence of Person ids comes first, ids being compared in PostgreSQL `uuid` order (their lowercase hexadecimal form, as for partner canonicalisation, §6). The result must not depend on the order in which relationships are loaded.

Traversal steps:

- `PARENT_OF A -> B`: from B to A = `PARENT`; from A to B = `CHILD`;
- `PARTNER_OF A <-> B`: either direction = `PARTNER`.

Supported concise patterns:

```text
[]                              SELF
[PARENT]                        parent
[CHILD]                         child
[PARENT, PARENT]                grandparent
[CHILD, CHILD]                  grandchild
[PARENT, CHILD]                 sibling (excluding self)
[PARENT, PARENT, CHILD]         parent's sibling
[PARENT, CHILD, CHILD]          sibling's child
[PARENT, PARENT, CHILD, CHILD]  first cousin
[PARTNER]                       partner
```

Any other existing path returns `RELATED`. No path returns `NONE_KNOWN`.

The target Person's gender turns a generic code into `FATHER`, `MOTHER`, etc. (`product/ux/localization-and-kinship-labels.md` §2).

## 10. Tree read model

`GET /tree` is served by a dedicated read query, not by repeated repository calls. It returns exactly the neighbourhood defined by OpenAPI and `product/ux/family-tree-ux.md` §6.

For every returned node compute `hasMoreParents`, `hasMoreChildren` and `relationshipToCurrentUser`. `relationshipToCurrentUser` uses a batched resolver or read strategy: no SQL query per card.

Target: a bounded set of SQL queries per tree request, independent of the Family size.

## 11. Search

PostgreSQL only; no Elasticsearch/OpenSearch.

Matching is case- and accent-insensitive, in first name, last name, preferred name and "first name last name"; the order is the lowered and unaccented display name compared byte by byte, then creation date, then id (`product/mvp.md` §19, OQ-022). The `%` and `_` typed by a User are literal characters. Use the PostgreSQL `unaccent` extension (in its own migration) or a normalised search column documented in `data-model.md`. No separate search service, no materialised search infrastructure without a measured need.

Search stays Family-scoped and paginated. `status=ARCHIVED` (ADMIN only) runs the same matching and ordering over ARCHIVED Persons; MERGED Persons are never returned.

## 11bis. Archived items for restoration

`listArchivedPersonRelationships` (ADMIN only) returns the ARCHIVED relationships where the Person is source or target, each with a summary of the other Person, in one bounded query (no query per row). It never returns ACTIVE relationships: those come from the tree read model.

## 12. Optimistic concurrency

`Person` and `FamilyRelationship` are versioned and follow `technical-specification.md` §13.

Merge receives both source and target versions and locks both rows in deterministic UUID order before any change.

## 13. Audit and history

Append audit entries (`data-model.md` §17) for at least:

```text
PERSON_CREATED
PERSON_UPDATED (field-focused entries where useful)
PERSON_CLAIMED
PERSON_UNCLAIMED
PERSON_ARCHIVED
PERSON_RESTORED
PERSONS_MERGED
RELATIONSHIP_CREATED
RELATIONSHIP_ARCHIVED
RELATIONSHIP_RESTORED
```

The Person history endpoint maps only presentation-safe Person-related entries. Never return raw audit JSON.

## 14. No event infrastructure

No Kafka, RabbitMQ, SQS, Redis, graph database, worker or outbox. In-process application events are allowed only for secondary effects (`architecture.md` §10); they must not hide the primary business flow.

## 15. Performance boundaries

The MVP is not optimised for millions of Persons, but must avoid algorithms that scale with the whole database.

Test fixtures include at least:

- a 1-Person Family;
- a normal Family of 20–50 Persons;
- a large Family of 250 Persons for tree and search smoke tests.

No millisecond SLA is set; tests mainly prevent N+1 queries and full-database scans.
