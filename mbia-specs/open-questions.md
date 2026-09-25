# Open Questions

Specification gaps found during implementation. Agents add entries here instead of inventing product behavior (see `AGENTS.md` §1).

When a question is answered, update the relevant spec, then move the entry to **Resolved** with a link to the change.

## Template

```markdown
### OQ-### — Short title

- **Raised by / date:**
- **Context:** where the gap was found (spec section, use case, screen)
- **Question:**
- **Options:** A / B / …
- **Recommendation:**
- **Blocking:** what cannot be built until answered
```

## Open

### OQ-003 — Token email already used by another Mbia account

- **Raised by / date:** coding agent (PR-10), 2026-09-25
- **Context:** ADR-005 and `data-model.md` §5 create the User just in time from the token and update its email when the claim changes. The unique index `uq_users_email_lower` allows one ACTIVE account per email. Keycloak forbids duplicate emails, but a Keycloak account deleted and recreated with the same email gets a new `sub`: its token then carries an email that an existing Mbia User with another `sub` still holds. The same happens when a user changes their email to one freed in Keycloak but still stored in Mbia.
- **Question:** what does the API answer, and what happens to the existing Mbia account?
- **Options:** A — reject with a dedicated 409 code (for example `EMAIL_ALREADY_IN_USE`, added to the contract) and let support resolve it / B — anonymise the old account (as account deletion, `mvp.md` §30) and create the new one / C — attach the new `sub` to the existing account (account takeover risk: not recommended).
- **Recommendation:** A; it never merges or erases data silently.
- **Blocking:** nothing in Phase 1. Until answered, PR-10 does not special-case it: the database refuses the row and the API answers 500 `INTERNAL_ERROR`.

## Resolved

### OQ-001 — JUnit major version with Spring Boot 4.1

- **Raised by / date:** coding agent (PR-02), 2026-09-25
- **Context:** `technical/stack.md` lists "JUnit 5", but Spring Boot 4.1.1 manages JUnit Jupiter 6.0.3 in its dependency BOM.
- **Question:** force JUnit 5 against the Boot BOM, or use the version Boot manages?
- **Options:** A — use JUnit 6 as managed by Spring Boot / B — override to JUnit 5.x.
- **Recommendation:** A; overriding the Boot BOM risks incompatibilities with Boot test support and Testcontainers 2.
- **Answer:** A (human, 2026-09-25). `technical/stack.md` now reads "JUnit Jupiter (version managed by Spring Boot)"; changed in PR-02.

### OQ-002 — Error codes for requests rejected by the framework

- **Raised by / date:** coding agent (PR-07), 2026-09-25
- **Context:** `technical-specification.md` §12 and the OpenAPI `ProblemDetails.code` list only name business codes. Unknown paths, unsupported methods or media types and unreadable bodies are rejected by Spring before any use case.
- **Question:** which `code` do these responses carry?
- **Options:** A — 400 cases → `VALIDATION_FAILED`, plus dedicated generic codes `RESOURCE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `NOT_ACCEPTABLE` (406), `UNSUPPORTED_MEDIA_TYPE` (415) / B — two generic codes `ROUTE_NOT_FOUND` and `REQUEST_NOT_SUPPORTED` / C — leave these cases out of PR-07.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-25). Added to `technical-specification.md` §12 and to the `ProblemDetails.code` examples in `openapi.yaml`; changed in PR-07.

### OQ-003 — Problem `type` URI and `fieldErrors[].code` format

- **Raised by / date:** coding agent (PR-07), 2026-09-25
- **Context:** the `ProblemDetails` schema requires `type` (URI) and `FieldError.code`, but no spec defines their values.
- **Question:** what goes into `type`, and in which format is `fieldErrors[].code`?
- **Options:** `type`: A — `<configurable base URI>/<code in kebab-case>` / B — `about:blank`. `fieldErrors[].code`: A — constraint name in UPPER_SNAKE case (`NOT_BLANK`) / B — raw constraint name (`NotBlank`).
- **Recommendation:** A for both.
- **Answer:** A for both (human, 2026-09-25). Documented in `technical-specification.md` §12; changed in PR-07.
