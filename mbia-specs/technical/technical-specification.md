# Mbia MVP — Technical Specification

**Version:** 0.2  
**Status:** Draft

## 1. Technical objective

Mbia may be built substantially by coding agents, but it must remain fully understandable and maintainable by a human developer without access to the original agent conversation.

A developer must be able to:

```text
clone repository
→ run locally
→ understand module boundaries
→ modify a use case
→ add a migration
→ evolve OpenAPI
→ run tests
→ deploy
```

without asking an agent to rediscover its own architecture.

## 2. Architecture

Use a **modular monolith**.

```text
React SPA
   ↓ HTTPS / REST / JSON
Spring Boot modular monolith
   ├── PostgreSQL
   ├── S3-compatible object storage
   ├── OIDC identity provider
   └── transactional email provider
```

Do not introduce microservices, Kafka, RabbitMQ, service mesh or distributed transactions for the MVP without a new architectural decision.

## 3. Monorepo

Recommended repository:

```text
mbia/
├── AGENTS.md
├── README.md
├── docker-compose.yml
├── .env.example
├── mbia-specs/
│   ├── product/
│   └── technical/
│       └── adr/
├── backend/
├── frontend/
└── infrastructure/
```

## 4. Backend principles

Package by business capability:

```text
identity
family
genealogy
memory
invitation
activity
shared
```

Each module may use:

```text
domain/
application/
infrastructure/
api/
```

Dependency direction:

```text
API -> Application -> Domain
Infrastructure -> Domain/Application ports
```

Domain code must not depend on Spring MVC, JPA, PostgreSQL, AWS or JSON.

## 5. Explicit use cases

Prefer small use-case classes:

```text
CreatePersonUseCase
UpdatePersonUseCase
ArchivePersonUseCase
RestorePersonUseCase
ClaimPersonUseCase
MergePersonsUseCase
CreateRelationshipUseCase
ArchiveRelationshipUseCase
RestoreRelationshipUseCase
ResolveKinshipUseCase
```

Avoid giant generic services with dozens of unrelated methods.

## 6. Backend stack

Baseline:

- Java 25 LTS;
- Spring Boot 4.1.x;
- Spring MVC;
- Spring Security OAuth2/OIDC resource server;
- Spring Data JPA / Hibernate;
- PostgreSQL 18;
- Flyway;
- Maven Wrapper;
- Bean Validation;
- JUnit 5;
- AssertJ;
- Mockito;
- Testcontainers;
- Spring Boot Actuator.

## 7. Frontend stack

Baseline:

- React 19;
- TypeScript;
- Vite;
- React Router;
- TanStack Query;
- React Hook Form;
- Tailwind CSS;
- generated OpenAPI TypeScript client/types.

The MVP uses a SPA rather than adding a second server-side application layer such as Next.js.

## 8. Persistence

PostgreSQL is the only business database for the MVP, including the family graph.

No graph database is required.

Recursive SQL/CTEs or purpose-built graph queries may be used when necessary.

Binary media is stored outside PostgreSQL in S3-compatible object storage. PostgreSQL stores metadata and storage keys.

## 9. Authentication and authorization

Authentication is delegated to **Keycloak** (ADR-005): realm `mbia`, public SPA client using Authorization Code + PKCE, backend as OAuth2 Resource Server.

Mbia stores the stable external subject on its User record, created just-in-time on the first authenticated call. Tokens without a verified email are rejected (`EMAIL_NOT_VERIFIED`).

The realm configuration is versioned in `infrastructure/keycloak/realm-mbia.json` and imported automatically locally and in CI.

Authorization remains owned by Mbia:

```text
identity -> who is this User?
membership -> which Family may they access?
role + domain rule -> what may they do?
```

Every Family-scoped backend use case verifies ACTIVE membership, as its first step, through the `family` module's `FamilyAccess` guard (the only entry point other modules use):

- unknown Family, or caller without an ACTIVE membership in it (never a member, or `REMOVED`) → **404 `FAMILY_NOT_FOUND`, never 403**. Both cases return the same response, so an outsider cannot learn whether a Family exists;
- ACTIVE member whose role does not allow the operation → 403 `PERMISSION_DENIED`.

The same applies to any resource of a Family the caller cannot access: it answers as if it did not exist.

Frontend-hidden actions are UX only, never a security mechanism.

## 10. Contract-first API

`technical/api/openapi.yaml` is the HTTP contract source of truth.

Preferred development flow:

```text
Product spec
→ use case
→ OpenAPI contract
→ implementation
→ automated tests
```

HTTP DTOs are not domain objects.

Example:

```text
CreatePersonRequest
→ CreatePersonCommand
→ Person
→ PersonResponse
```

Generated frontend code lives in an isolated generated directory and is never edited manually.

## 11. API conventions

Prefix:

```text
/api/v1
```

Families scope most resources.

Examples include:

```text
GET  /api/v1/me
POST /api/v1/families
GET  /api/v1/families/{familyId}
GET  /api/v1/families/{familyId}/members
POST /api/v1/families/{familyId}/invitations
POST /api/v1/families/{familyId}/persons
GET  /api/v1/families/{familyId}/persons/{personId}
POST /api/v1/families/{familyId}/persons/{personId}/claim
POST /api/v1/families/{familyId}/persons/{personId}/merge
POST /api/v1/families/{familyId}/relationships
GET  /api/v1/families/{familyId}/tree
GET  /api/v1/families/{familyId}/kinship
POST /api/v1/families/{familyId}/memories/photos
POST /api/v1/families/{familyId}/memories/stories
GET  /api/v1/families/{familyId}/activities
```

The full contract is in `api/openapi.yaml`.

## 12. Error model

Return stable application error codes in a common problem representation.

Examples:

```text
PERSON_NOT_FOUND
FAMILY_NOT_FOUND
MEMBERSHIP_REQUIRED
PERMISSION_DENIED
PERSON_ALREADY_CLAIMED
USER_ALREADY_LINKED
RELATIONSHIP_ALREADY_EXISTS
SELF_RELATIONSHIP_NOT_ALLOWED
RELATIONSHIP_CREATES_CYCLE
RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED
PERSON_NOT_ACTIVE
PERSON_MERGE_CONFLICT
CONCURRENT_MODIFICATION
MEMORY_NOT_FOUND
MEDIA_NOT_FOUND
MEDIA_TOO_LARGE
MEDIA_INVALID
MEDIA_NOT_READY
MEDIA_ALREADY_USED
```

Memories and media (OQ-037): an unknown, other-Family or ARCHIVED Memory → 404 `MEMORY_NOT_FOUND`; an unknown or other-Family media asset → 404 `MEDIA_NOT_FOUND`; a related Person unknown, of another Family or MERGED → 404 `PERSON_NOT_FOUND`, newly added and ARCHIVED → 409 `PERSON_NOT_ACTIVE`; a field irrelevant to the Memory type → 400 `VALIDATION_FAILED`; editing or archiving another member's Memory without being ADMIN → 403 `PERMISSION_DENIED`; completing or attaching another member's upload → 403 `PERMISSION_DENIED`; attaching an asset already used → 409 `MEDIA_ALREADY_USED` (OQ-036).

The frontend translates stable codes into localized messages. It must not parse English server messages to determine behavior.

Every error response is `application/problem+json` shaped as the OpenAPI `ProblemDetails` schema:

- `type` is `<problems base URI>/<code in kebab-case>`, e.g. `https://mbia.example.com/problems/validation-failed`; the base URI is configuration (`mbia.problems.base-uri`).
- `traceId` equals the `X-Request-Id` response header. The server reuses the request's `X-Request-Id` when it is safe (`[A-Za-z0-9._-]`, at most 64 characters), otherwise it generates one.
- Bean Validation errors return 400 `VALIDATION_FAILED` with `fieldErrors`; `fieldErrors[].code` is the constraint name in UPPER_SNAKE case (`@NotBlank` → `NOT_BLANK`).
- Requests the framework rejects before any use case use generic codes: unreadable body, wrong parameter type or missing parameter → 400 `VALIDATION_FAILED`; unknown path → 404 `RESOURCE_NOT_FOUND`; 405 `METHOD_NOT_ALLOWED`; 406 `NOT_ACCEPTABLE`; 415 `UNSUPPORTED_MEDIA_TYPE`.
- Unexpected errors return 500 `INTERNAL_ERROR` with a generic message; the cause is only logged. No response ever contains a stack trace, SQL or exception class name.

## 13. Optimistic concurrency

Mutable resources expose a version.

Stale updates return conflict rather than silently overwriting newer data.

- A versioned resource returns its version as a strong `ETag`: `"<version>"`, for example `"3"`.
- A mutation sends it back in `If-Match` (exactly one such tag; weak tags, `*` and lists are rejected). Missing or malformed `If-Match` → 400 `VALIDATION_FAILED`.
- An `If-Match` different from the persisted version → 409 `CONCURRENT_MODIFICATION`. The write itself is also guarded by the `version` column, so a change committed between the check and the write returns the same 409.
- Each successful mutation increments the version by one. A request that changes nothing (same values, or only ignored fields) is checked like any other, then answers 200 without writing: the version is unchanged (OQ-008).

This applies especially to Person, relationship and editable Memory flows.

## 14. Transactions

Atomic business operations use one local PostgreSQL transaction.

Examples:

- Family creation + creator ADMIN membership;
- invite acceptance + membership creation;
- Person merge + relationship deduplication + Memory reassociation + audit;
- relationship archive + audit/activity.

## 15. Audit and activity

User-facing activity is not the same as technical audit.

Activity is concise and readable.

Audit may contain:

```text
actor
timestamp
resource
action
oldValue
newValue
traceId when useful
```

## 16. Media uploads

Prefer direct browser upload to S3-compatible storage via pre-signed URLs.

```text
Browser -> request upload authorization from Mbia
Mbia -> returns upload URL + storage key
Browser -> uploads binary directly to object storage
Mbia -> persists/activates media metadata
```

Do not route every large photo binary through the application server unless required by a later use case.

Limits and processing (ADR-007):

- browser-side downscale to ≤ 2560 px before upload;
- maximum 15 MB per upload;
- on completion, the server validates, strips all metadata, generates `display` (≤ 2048 px) and `thumbnail` (≤ 480 px) JPEG derivatives, and deletes the original;
- views use pre-signed GET URLs valid 60 minutes.

## 16bis. Internationalisation

French (default) and English (ADR-006). The API returns stable codes, never translated text; the frontend translates. Emails are rendered server-side from localized templates. CI checks that both languages define the same translation keys.

## 17. Testing strategy

```text
Domain unit tests
→ application use-case tests
→ PostgreSQL Testcontainers tests
→ API integration tests
→ small critical E2E browser suite
```

Critical automated scenarios include:

- create Family;
- create Person;
- create parent relation;
- derive grandparent;
- reject cycle;
- claim Person;
- protect linked Person;
- merge duplicate;
- add Memory;
- invite Contributor (email and link);
- reject reused, expired, revoked or renewed invitation token;
- keep at least one ADMIN;
- release linked Person when a member leaves;
- strip photo metadata;
- reject cross-Family access.

## 18. Local development

Required locally:

```text
Docker
JDK
Node
```

Typical flow:

```text
docker compose up -d
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Local dependencies started by `docker compose`:

- PostgreSQL (Mbia database + Keycloak database);
- RustFS (S3-compatible storage, ADR-009);
- Keycloak with the `mbia` realm imported and test users;
- Mailpit (catches all emails, including Keycloak emails).

## 19. Observability

Minimum:

- structured logs;
- request correlation / trace ID;
- health endpoint;
- basic metrics;
- centralized error reporting.

## 20. CI

Each pull request should at least run:

- backend compilation;
- backend tests;
- frontend typecheck;
- frontend tests;
- frontend build;
- OpenAPI validation.

Breaking API changes should be detectable.

## 21. AGENTS.md rule

The project should contain a root `AGENTS.md` explaining:

- where specs live;
- architecture rules;
- how to run tests;
- where use cases belong;
- how to change OpenAPI;
- how to create migrations;
- which files are generated;
- which decisions require an ADR.

## 22. ADR rule

Architecturally significant decisions are recorded as ADRs using:

```text
Context
Decision
Consequences
```

ADRs live in `technical/adr/` (index: `technical/adr/README.md`), currently ADR-001 to ADR-008.

Agents may propose ADRs but must not silently change established architecture.

## 23. Human maintainability rule

A pull request is not acceptable if its rationale only exists in the agent conversation.

A human maintainer should understand the change from:

- specs;
- code;
- tests;
- ADRs;
- commit/PR description.

## 24. Source-of-truth order

```text
Product Specification
        ↓
Technical Specification
        ↓
OpenAPI Contract
        ↓
Automated Tests
        ↓
Implementation
```

An implementation shortcut must never silently alter a product rule.
