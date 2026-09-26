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

### OQ-004 — Language of the Keycloak pages after a language change in Mbia

- **Raised by / date:** coding agent (PR-12), 2026-09-25
- **Context:** `localization-and-kinship-labels.md` §1 requires Keycloak pages in both languages and says the language chosen in account settings wins on every device. Mbia passes `ui_locales` to Keycloak on sign-in, sign-up and sign-out (PR-11), but the `Change password` link of SCREEN-011 opens the Keycloak account console, whose own sign-in redirect carries no Mbia language: in local tests its pages follow Keycloak's own choice (browser, cookie or the Keycloak user's `locale` attribute), not `preferredLocale`. `PATCH /me` does not update the Keycloak user.
- **Question:** must Keycloak pages opened from account settings follow the language chosen in Mbia?
- **Options:** A — accept Keycloak's own choice for the account console (current PR-12 behavior) / B — the backend also writes the Keycloak user's `locale` attribute on `PATCH /me` (new Keycloak Admin API dependency: ADR needed) / C — replace the account-console link by an application-initiated action (`signinRedirect` with `kc_action=UPDATE_PASSWORD` and `ui_locales`): the password page opens in the UI language and returns to Mbia, but SCREEN-011 says "link to Keycloak account page".
- **Recommendation:** C; it keeps the user in Mbia's language without new infrastructure. Needs a SCREEN-011 wording change.
- **Blocking:** nothing; PR-12 ships option A.

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

### OQ-005 — `profileMediaAssetId` before media exists (Phase 2)

- **Raised by / date:** coding agent (Phase 2 plan), 2026-09-25
- **Context:** `CreatePersonRequest` and `UpdatePersonRequest` accept an optional `profileMediaAssetId` (`openapi.yaml`), but Phase 2 delivers Persons without media: `media_assets` and `persons.profile_media_asset_id` do not exist yet (`delivery/phase-2-core-family-graph.md` §3.1, §3.3). No spec says what the API answers when a client sends a non-null value.
- **Question:** in Phase 2, what does `createPerson` / `updatePerson` do with a non-null `profileMediaAssetId`?
- **Options:** A — reject with 400 `VALIDATION_FAILED` on that field (no media asset can exist yet, so the reference is invalid) / B — ignore the field silently / C — remove the field from the contract until media is delivered (breaking change, needs `api-breaking-approved`, re-added later).
- **Recommendation:** A; the contract stays stable and nothing is silently dropped. `profilePictureUrl` is always `null` in Phase 2 responses.
- **Blocking:** the handling of that field in PR-17 and PR-18 only; the rest of those PRs can proceed.
- **Answer:** B (human, 2026-09-25): the value is ignored and the request behaves as if the field were absent. Documented in `delivery/phase-2-core-family-graph.md` §3.1 and PR-17 / PR-18 acceptance criteria.

### OQ-006 — Where an ADMIN finds what can be restored

- **Raised by / date:** coding agent (Phase 2 plan), 2026-09-25
- **Context:** `mvp.md` §13 lets an ADMIN restore an archived relationship or Person, and the Phase 2 journey requires it (`delivery/phase-2-core-family-graph.md` §1). But archived relationships and Persons are hidden from tree and search, `archiveRelationship` returns 204 without the new version, `PersonHistoryEntry` carries no resource id or version, and no API lists archived items. `screens.md` also does not say where `Remove link` and `Restore` are offered.
- **Question:** how does an ADMIN reach an archived relationship or Person (and its version) to restore it, and on which screens are `Remove link` / `Restore` shown?
- **Options:** A — additive endpoints listing archived items (for example `GET /families/{familyId}/relationships?status=ARCHIVED&personId=…` and `GET /families/{familyId}/persons?status=ARCHIVED`, ADMIN only) plus a "Removed links" / "Archived people" area for the ADMIN on SCREEN-005 and in search / B — restore only from the Person's History section, adding `resourceId` and `resourceVersion` to `PersonHistoryEntry` (additive) / C — `Undo` offered only right after removal (fragile: relies on guessing the version).
- **Recommendation:** A, with `Remove link` on each relative of SCREEN-005's Family section (and in the Quick View); it is additive (non-breaking) and matches the "correct mistakes without technical intervention" goal.
- **Blocking:** the restore UI of PR-24 and PR-26 and the restore step of the Phase 2 journey. Backend `restoreRelationship` / `restorePerson` can be built.
- **Answer:** A (human, 2026-09-25): additive list endpoints and an ADMIN-only area on the profile. Contract: `searchPersons` gains `status=ARCHIVED` (ADMIN only), new `listArchivedPersonRelationships`, `getPerson` documented as returning ARCHIVED Persons (checked non-breaking with oasdiff). `Remove link` is on the profile Family section only, not in the Quick View. Specs: `mvp.md` §13, `person-relationships-collaboration.md` §5 and §8, `screens.md` SCREEN-005 (Remove link, Removed links, Archived Person) and SCREEN-007, `genealogy.md` §11 and §11bis, `data-model.md` §23.2bis; delivered in PR-24 and PR-26.

### OQ-007 — Error code when "Start with me" finds an existing linked Person

- **Raised by / date:** coding agent (PR-17), 2026-09-25
- **Context:** `createPerson` with `linkToCurrentUser = true` follows the claim rules (`openapi.yaml`, `data-model.md` §21: "no other non-MERGED person in family linked to current user"), but no spec names the error returned when the current User already has a linked Person in the Family. `PERSON_ALREADY_CLAIMED` describes a Person linked to someone else.
- **Question:** which code does the API return?
- **Options:** A — new 409 `USER_ALREADY_LINKED` (additive in `ProblemDetails.code` examples) / B — reuse 409 `PERSON_ALREADY_CLAIMED`.
- **Recommendation:** A; the frontend can explain the right situation ("you are already in this family's tree"), and PR-19 can reuse it for "claiming a second Person".
- **Answer:** A (human, 2026-09-25). Added to `openapi.yaml` (`createPerson` description, `ProblemDetails.code` examples), `technical-specification.md` §12 and `data-model.md` §21; changed in PR-17.

### OQ-008 — Partial update semantics of `updatePerson`

- **Raised by / date:** coding agent (PR-18), 2026-09-25
- **Context:** `updatePerson` is a `PATCH` whose optional fields are nullable (`UpdatePersonRequest`), but no spec says how a client clears a value, and the generated server models (`openApiNullable = false`) cannot tell an absent field from a `null` one. The specs also do not say what a request that changes nothing does to the version (`technical-specification.md` §13, and PR-18's "`profileMediaAssetId` … changes nothing"), nor what `updatePerson` answers for an ARCHIVED or MERGED Person (SCREEN-005 offers no mutation on them).
- **Question:** (1) how is an optional field cleared; (2) does a no-op request increment the version; (3) what does editing a non-ACTIVE Person return?
- **Options:** (1) A — absent or `null` leaves a field unchanged, a blank string clears an optional text, `{precision: UNKNOWN}` clears a date, `gender: UNKNOWN` clears the gender / B — `null` clears, absent keeps (needs `JsonNullable`, a new library: ADR). (2) A — 200, nothing written, version unchanged / B — the version always increments. (3) A — 404 `PERSON_NOT_FOUND` / B — no rule until PR-26.
- **Recommendation:** A for all three.
- **Answer:** A for all three (human, 2026-09-25). Documented in `openapi.yaml` (`updatePerson`, `UpdatePersonRequest`) and `technical-specification.md` §13; changed in PR-18.


### OQ-009 — Claim / unclaim edge cases and scope of linked-Person protection

- **Raised by / date:** coding agent (PR-19), 2026-09-25
- **Context:** `claimPerson` / `unclaimPerson` (`openapi.yaml`, `data-model.md` §21) and the linked-Person rules (`mvp.md` §4, §7, `person-relationships-collaboration.md` §2, §12) do not say (1) what `unclaimPerson` answers for a Person linked to nobody (no error code exists), (2) what `claimPerson` answers for the Person already linked to the caller, nor (3) which `updatePerson` fields are the protected "identity fields".
- **Question:** (1) unclaim of a non-linked Person; (2) claim of one's own Person; (3) protected fields.
- **Options:** (1) A — only the linked User or an ADMIN may unclaim: a non-ADMIN gets 403 `PERMISSION_DENIED`, an ADMIN gets 200 with the unchanged Person and version / B — new 409 `PERSON_NOT_CLAIMED` / C — 200 no-op for everyone. (2) A — 200, nothing written / B — 409 `USER_ALREADY_LINKED`. (3) A — every `updatePerson` field: a CONTRIBUTOR edits a linked Person only when it is their own (`mvp.md` §4 "edit their own linked Person") / B — identity only, the biography stays open to every CONTRIBUTOR.
- **Recommendation:** A for all three; as for `updatePerson` (OQ-008), claiming or unclaiming a non-ACTIVE Person returns 404 `PERSON_NOT_FOUND`, and a linked VIEWER stays read-only on their Person.
- **Answer:** A for all three (human, 2026-09-25). Documented in `openapi.yaml` (`claimPerson`, `unclaimPerson`, `updatePerson` descriptions) and `data-model.md` §21; changed in PR-19.

### OQ-010 — How Family Home knows the current User's linked Person

- **Raised by / date:** coding agent (PR-20), 2026-09-25
- **Context:** SCREEN-002 offers `Add a relative` (My father, My mother, …) "when the current User has a linked Person" (`family-tree-ux.md` §9.1), but no Phase 2 endpoint gives the frontend that Person: `FamilySummary` has no such field, `searchPersons` arrives with PR-25, `getFamilyTree` with PR-22, member listing in a later phase.
- **Question:** how does Family Home find the caller's linked Person?
- **Options:** A — additive `FamilySummary.myLinkedPersonId` (nullable) / B — postpone the Family Home entry to PR-22.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-25). Added to `openapi.yaml` (`FamilySummary`) and `screens.md` SCREEN-002; changed in PR-20.

### OQ-011 — Error code for a relationship involving an ARCHIVED or MERGED Person

- **Raised by / date:** coding agent (PR-20), 2026-09-25
- **Context:** `person-relationships-collaboration.md` §7 blocks such relationships, but the contract has no code for it; PR-24 must also explain a refused restore "because the other Person is archived".
- **Question:** which status and code?
- **Options:** A — new 409 `PERSON_NOT_ACTIVE` / B — 404 `PERSON_NOT_FOUND`, as `claimPerson` does for a non-ACTIVE Person.
- **Recommendation:** A; `getPerson` still returns an archived Person, so "not found" would be misleading.
- **Answer:** A (human, 2026-09-25). Added to `openapi.yaml` (`createRelationship`, `ProblemDetails.code`), `technical-specification.md` §12 and `person-relationships-collaboration.md` §7; changed in PR-20.

### OQ-012 — Parent born the same year as the child or after: one warning or two?

- **Raised by / date:** coding agent (PR-20), 2026-09-25
- **Context:** `person-relationships-collaboration.md` §7.1: when `parentBirthYear >= childBirthYear`, the computed age (≤ 0) is also `< 12`, so a literal reading emits both `PARENT_BORN_AFTER_CHILD` and `IMPLAUSIBLE_PARENT_AGE`.
- **Question:** emit both warnings, or only the first?
- **Options:** A — only `PARENT_BORN_AFTER_CHILD`; age = difference of birth years, also with exact dates / B — both.
- **Recommendation:** A; the second message repeats the first.
- **Answer:** A (human, 2026-09-25). Documented in `person-relationships-collaboration.md` §7.1; changed in PR-20.

### OQ-013 — Kinship with an ARCHIVED or MERGED Person

- **Raised by / date:** coding agent (PR-21), 2026-09-25
- **Context:** `person-relationships-collaboration.md` §10 and `genealogy.md` §9: paths use ACTIVE Persons only, but `getKinship` may be called with an ARCHIVED or MERGED `from` or `to`, and `getPerson` returns an archived Person with its `relationshipToCurrentUser`.
- **Question:** what does `getKinship` answer when `from` or `to` is not ACTIVE?
- **Options:** A — 200 `NONE_KNOWN` with an empty path (`from = to` stays `SELF`) / B — 409 `PERSON_NOT_ACTIVE` / C — 404 `PERSON_NOT_FOUND`.
- **Recommendation:** A; consistent with the badge of an archived Person's profile, and no new error on a read.
- **Answer:** A (human, 2026-09-25). Documented in `person-relationships-collaboration.md` §10 and the `getKinship` description of `openapi.yaml` (text only); changed in PR-21.

### OQ-014 — Tree focus requested on an ARCHIVED or MERGED Person

- **Raised by / date:** coding agent (PR-22), 2026-09-25
- **Context:** `getFamilyTree` accepts `focusPersonId`; `family-tree-ux.md` §6 says the browser's last focused Person falls back to the next rule when it is no longer ACTIVE, and `person-relationships-collaboration.md` §5 hides an archived Person from the tree. The contract does not say what the server answers for such a focus.
- **Question:** 200 with a server-side fallback, 409 `PERSON_NOT_ACTIVE` or 404 `PERSON_NOT_FOUND`?
- **Options:** A — 200: the server applies rules 1 then 3 as if no focus were requested, and `focusPersonId` of the response names the actual focus / B — 409 `PERSON_NOT_ACTIVE` / C — 404 `PERSON_NOT_FOUND`.
- **Recommendation:** A; one call for the tree, no new error on a read. An unknown or other-Family focus stays 404 `PERSON_NOT_FOUND`. The same fallback applies when the caller's linked Person is not ACTIVE.
- **Answer:** A (human, 2026-09-25). Documented in the `getFamilyTree` description of `openapi.yaml` (text only) and `family-tree-ux.md` §6; changed in PR-22.

### OQ-015 — Order of the tree nodes and edges

- **Raised by / date:** coding agent (PR-22), 2026-09-25
- **Context:** `family-tree-ux.md` §6.1 orders partners by relationship creation date and children by birth date then creation date, but `TreeNode` and `TreeEdge` carry no creation date and the contract gives no order.
- **Question:** how does the response carry that order?
- **Options:** A — a documented order (text only): edges by relationship creation then id; nodes focus first, then birth, Person creation, id / B — additive `createdAt` fields / C — no guarantee.
- **Recommendation:** A; no schema change.
- **Answer:** A (human, 2026-09-25). Documented in the `getFamilyTree` description of `openapi.yaml` (text only); changed in PR-22.

### OQ-016 — Headings of the profile Family section

- **Raised by / date:** coding agent (PR-22), 2026-09-25
- **Context:** SCREEN-005 lists parents, partners, children and siblings in the Family section; `localization-and-kinship-labels.md` has no heading for these groups.
- **Question:** which FR / EN headings?
- **Options:** A — FR "Parents / Partenaires / Enfants / Frères et sœurs", EN "Parents / Partners / Children / Siblings" / B — other wording.
- **Recommendation:** A, consistent with the §3 labels.
- **Answer:** A (human, 2026-09-25). Added to `localization-and-kinship-labels.md` §3bis; changed in PR-22.

### OQ-017 — `depth = 2` of `getFamilyTree` in Phase 2

- **Raised by / date:** coding agent (PR-22), 2026-09-25
- **Context:** the contract accepts `depth` 1 or 2; PR-22 says "the UI uses depth 1".
- **Question:** implement depth 2 now?
- **Options:** A — yes, as the contract describes (parents of the parents, children of the children) / B — depth 1 only.
- **Recommendation:** A; the contract already exposes it, at the same bounded query count.
- **Answer:** A (human, 2026-09-25). Implemented in PR-22 (no spec change).

### OQ-018 — Linked Person or last focused Person when opening the tree

- **Raised by / date:** coding agent (PR-23), 2026-09-25
- **Context:** `family-tree-ux.md` §6 ranks the User's linked Person (rule 1) before the last focused Person (rule 2), but also says the browser sends the last focused Person as the requested focus, which the server then uses whatever the linked Person.
- **Question:** when the User has a linked Person and a remembered focus, which one does the tree open on?
- **Options:** A — the literal order: the browser sends the remembered focus only when the User has no linked Person; the current focus stays in the tree URL while navigating / B — the remembered focus always wins; the linked Person is only the first default.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-25). Documented in `family-tree-ux.md` §6; changed in PR-23.

### OQ-019 — Order of the children groups in the tree

- **Raised by / date:** coding agent (PR-23), 2026-09-25
- **Context:** `family-tree-ux.md` §6.1 orders the children groups "one group per partner (same order as row 2)", while row 2 places partners by creation, first on the right, next on the left.
- **Question:** is "same order as row 2" the creation order or the left-to-right order of row 2?
- **Options:** A — left to right, as the partners are drawn (no crossing lines), then the group of the other children / B — creation order.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-25). Documented in `family-tree-ux.md` §6.1; changed in PR-23.

### OQ-020 — Removing a removed relationship, restoring an active one

- **Raised by / date:** coding agent (PR-24), 2026-09-26
- **Context:** `archiveRelationship` and `restoreRelationship` (`openapi.yaml`, `data-model.md` §20) describe the ACTIVE → ARCHIVED transition and its reverse, but not what happens when the relationship is already in the requested state with the current version (a stale version is already 409 `CONCURRENT_MODIFICATION`).
- **Question:** what does archiving an ARCHIVED relationship, or restoring an ACTIVE one, answer?
- **Options:** A — no-op: 204 for the archive, 200 with the unchanged relationship and version for the restore, nothing written or audited / B — 404 `RESOURCE_NOT_FOUND` / C — new 409 codes.
- **Recommendation:** A, as for `updatePerson` (OQ-008) and `unclaimPerson` (OQ-009); no new code.
- **Answer:** A (human, 2026-09-26). Documented in `openapi.yaml` (`archiveRelationship`, `restoreRelationship` descriptions, text only) and `person-relationships-collaboration.md` §8; changed in PR-24.

### OQ-021 — Date warnings when restoring a relationship

- **Raised by / date:** coding agent (PR-24), 2026-09-26
- **Context:** restoring re-runs every current validity rule (`mvp.md` §13, `person-relationships-collaboration.md` §8), but the date warnings of §7.1 are not validity rules, and `restoreRelationship` has no `confirmWarnings`.
- **Question:** are the date warnings re-evaluated at restore, and do they block it?
- **Options:** A — not blocking; recomputed from the current birth data and returned in `warnings` for information / B — not blocking, `warnings` always empty / C — blocking, with a new `confirmWarnings` parameter.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-26). Documented in `openapi.yaml` (`restoreRelationship` description, text only) and `person-relationships-collaboration.md` §7.1 and §8; changed in PR-24.
