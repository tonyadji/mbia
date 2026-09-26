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

### OQ-030 — What the profile of a MERGED Person shows

- **Raised by / date:** coding agent (PR-27), 2026-09-26
- **Context:** `getPerson` returns a MERGED Person with `mergedIntoPersonId`, reachable from an old link or from a removed link, but SCREEN-005 describes only the ARCHIVED case.
- **Question:** what does the profile of a MERGED Person show?
- **Options:** A — the identity data, a "This profile was merged into another profile" notice and a link to the kept profile, no mutation action / B — redirect to the kept profile.
- **Recommendation:** A, the same shape as the archived notice; implemented provisionally in PR-27 and easy to change.
- **Blocking:** nothing; only the wording and the behaviour of that notice.

### OQ-042 — Adding media to a Memory (future iteration)

- **Raised by / date:** human and coding agent (Phase 3 plan), 2026-09-26
- **Context:** `mvp.md` §17 defines two Memory types, PHOTO and STORY, and `mvp.md` §28 expects "add photo and Story". While planning Phase 3, the human decided that a Memory is **a title and a text only** for this phase. Media will later be **added to a Memory**, rather than being a separate kind of Memory. Uploading a file is a technical action. The product action is "add a file to this Memory", and the moment the upload happens in the journey must be designed.
- **Question:** in a later iteration, how does a Memory carry media, and when does the upload happen in the journey?
- **Points to decide:**
  - the model: a Memory with a title, a text and 0..n media, or one medium per Memory. This decides what becomes of the PHOTO / STORY types, `createPhotoMemory`, `memories.media_asset_id` and `caption` (`data-model.md` §14);
  - whether title or text become optional when a medium is present;
  - the moment of upload: when the file is chosen while writing the Memory (published with it), or after the Memory exists ("Add photos to this memory");
  - per-medium caption and taken date (OQ-033 already answers the date's precision);
  - the limit on media per Memory, to stay clear of "advanced albums" (`mvp.md` §27);
  - `mvp.md` §17 and §28 wording.
- **Recommendation:** decide it in a dedicated product discussion before the phase that adds media. Until then, Phase 3 creates nothing that assumes one model: no photo column in `memories`, and `createPhotoMemory` is not implemented.
- **Blocking:** nothing in Phase 3.

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

### OQ-022 — Search ordering with accents, and several words

- **Raised by / date:** coding agent (PR-25), 2026-09-26
- **Context:** `mvp.md` §19 orders results by display name "locale-independent, case-folded" and matches a substring in first name, last name or preferred name. Read literally, a byte-order sort puts "Éloïse" after "Zoé", and "Marie Dup" finds no one because no single field contains it.
- **Question:** is the sort key also accent-folded, and does a query spanning first and last name match?
- **Options:** sort — A: lowered and unaccented display name, compared byte by byte / B: lowered display name only. Matching — C: also "first name last name" / D: each field separately.
- **Recommendation:** A and C.
- **Answer:** A and C (human, 2026-09-26). Documented in `mvp.md` §19, `genealogy.md` §11, `data-model.md` §23.1 and `openapi.yaml` (`searchPersons` descriptions, text only); implemented in PR-25.

### OQ-023 — Error code when archiving a linked Person

- **Raised by / date:** coding agent (PR-26), 2026-09-26
- **Context:** `person-relationships-collaboration.md` §5 and `data-model.md` §10: a linked Person cannot be archived until its User link is released, but `archivePerson` (`openapi.yaml`) names no error code for this refusal.
- **Question:** which status and code?
- **Options:** A — 409 `PERSON_ALREADY_CLAIMED`, the existing code for "this Person is linked to a member" / B — new 409 `PERSON_LINKED`.
- **Recommendation:** A; no new code, the frontend explains it for this action.
- **Answer:** A (human, 2026-09-26). Documented in `openapi.yaml` (`archivePerson` description, text only) and `person-relationships-collaboration.md` §5; implemented in PR-26.

### OQ-024 — Archiving an archived Person, restoring an active one

- **Raised by / date:** coding agent (PR-26), 2026-09-26
- **Context:** `archivePerson` and `restorePerson` describe the ACTIVE ↔ ARCHIVED transitions, not a request for the state the Person is already in, with the current version.
- **Question:** what does the API answer?
- **Options:** A — no-op: 200 with the unchanged Person and version, nothing written or audited (as OQ-020) / B — 409.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-26). Documented in `openapi.yaml` (`archivePerson`, `restorePerson` descriptions, text only) and `person-relationships-collaboration.md` §5; implemented in PR-26.

### OQ-025 — Archiving or restoring a MERGED Person

- **Raised by / date:** coding agent (PR-26), 2026-09-26
- **Context:** a MERGED Person (PR-27) is neither ACTIVE nor ARCHIVED; `archivePerson` and `restorePerson` do not say what they answer for it.
- **Question:** which status and code?
- **Options:** A — 404 `PERSON_NOT_FOUND`, as `updatePerson` and `claimPerson` for a Person that can no longer change / B — 409 `PERSON_NOT_ACTIVE`.
- **Recommendation:** A; a merged Person is never restored.
- **Answer:** A (human, 2026-09-26). Documented in `openapi.yaml` (`archivePerson`, `restorePerson` descriptions, text only) and `person-relationships-collaboration.md` §5; implemented in PR-26.

### OQ-026 — Error codes of a refused merge

- **Raised by / date:** coding agent (PR-27), 2026-09-26
- **Context:** `mergePerson` (`openapi.yaml`), `data-model.md` §19 and `person-relationships-collaboration.md` §4.2 list the refusals (two different linked Users, resulting self relation or parental cycle, non-ACTIVE Persons, `A.id != B.id`) but name only `PERSON_MERGE_CONFLICT`, without saying which refusal uses it; SCREEN-COMPONENT-004 must explain each conflict.
- **Question:** which status and code for each refusal?
- **Options:** A — 409 `PERSON_MERGE_CONFLICT` with `details.reason` = `DIFFERENT_LINKED_USERS` / `SELF_RELATIONSHIP` / `PARENTAL_CYCLE`; a MERGED Person or another Family's → 404 `PERSON_NOT_FOUND` (OQ-025); an ARCHIVED Person → 409 `PERSON_NOT_ACTIVE` (OQ-011); the same Person as source and target → 400 `VALIDATION_FAILED`; a stale version → 409 `CONCURRENT_MODIFICATION` / B — reuse `SELF_RELATIONSHIP_NOT_ALLOWED` and `RELATIONSHIP_CREATES_CYCLE` for the graph refusals.
- **Recommendation:** A; one code for the merge, and the UI explains each reason.
- **Answer:** A (human, 2026-09-26). Documented in `openapi.yaml` (`mergePerson` description, text only), `person-relationships-collaboration.md` §4.2 and `data-model.md` §19; implemented in PR-27.

### OQ-027 — Removed relationships and exact duplicates in a merge

- **Raised by / date:** coding agent (PR-27), 2026-09-26
- **Context:** `data-model.md` §19 moves the duplicate's relationships to the kept Person and deduplicates identical ones, but does not say what happens to its ARCHIVED relationships, to an ACTIVE relationship identical to one of the kept Person, nor to a removed link between the two Persons (the database forbids self relations).
- **Question:** which relationships move, and what becomes of the duplicates?
- **Options:** A — every relationship of the duplicate (ACTIVE and ARCHIVED) moves to the kept Person and stays restorable from their profile; an ACTIVE relationship identical to an ACTIVE one of the kept Person is archived and stays on the duplicate; an ARCHIVED link between the two stays on the duplicate and does not block the merge / B — only ACTIVE relationships move.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-26). Documented in `data-model.md` §19 and `person-relationships-collaboration.md` §4.2; implemented in PR-27.

### OQ-028 — Which target values are "empty" in a merge

- **Raised by / date:** coding agent (PR-27), 2026-09-26
- **Context:** `data-model.md` §19 step 3 keeps the target's non-empty scalar values and fills its empty ones from the source, but `gender`, dates and `isDeceased` always have a value.
- **Question:** when is a target value empty, and how is the death handled?
- **Options:** A — an absent text, `gender: UNKNOWN` and a date of precision `UNKNOWN` are empty; the kept Person is deceased when either Person is, and the source's death date fills an unknown death date / B — the target's `isDeceased` always wins.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-26). Documented in `data-model.md` §19 and `openapi.yaml` (`mergePerson` description, text only); implemented in PR-27.

### OQ-029 — Where the merge starts on the profile

- **Raised by / date:** coding agent (PR-27), 2026-09-26
- **Context:** SCREEN-005 offers the ADMIN "merge a duplicate" (SCREEN-COMPONENT-004) without saying from which profile, nor how the other one is chosen.
- **Question:** which profile opens the merge, and how is the other chosen?
- **Options:** A — from the duplicate's profile: the ADMIN searches the profile to keep (SCREEN-007), compares both, confirms, and lands on the kept profile / B — the same, with a way to swap which profile is kept.
- **Recommendation:** A.
- **Answer:** A (human, 2026-09-26). Documented in `screens.md` SCREEN-005 and SCREEN-COMPONENT-004; implemented in PR-27.

### OQ-031 — What the Person history shows

- **Raised by / date:** coding agent (PR-28), 2026-09-26
- **Context:** `genealogy.md` §13 says the Person history maps "presentation-safe Person-related entries" and `data-model.md` §18 shows a field change ("Birth year: 1954 → 1956"), but neither says which audit entries are "Person-related" (relationship entries have the relationship as resource), how an update of several fields is shown (`PersonHistoryEntry` has one `field`), nor which values are safe to show.
- **Question:** which entries and which values does the Person history show?
- **Options:** scope: A — only entries whose resource is the Person / B — also the created, removed and restored relationships of the Person. Values: A — one audit entry per changed field, old → new, except the biography (changed, without values); no value for other actions (never a user id) / B — old → new for every field, biography included / C — the changed field only, no values.
- **Recommendation:** A for both.
- **Answer:** A for both (human, 2026-09-26). Documented in `genealogy.md` §13 and `data-model.md` §18; implemented in PR-28.

### OQ-032 — Screens to view, edit and archive a Memory, and the Family Memories list

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `screens.md` defines only SCREEN-006 Add Memory. Several things have no screen: opening a Memory, whether a photo in its larger version (`mvp.md` §23) or a story in full; editing it and archiving it (`mvp.md` §17, `updateMemory`, `archiveMemory`); and the Family's list of Memories. That list is the `Memories` tab of the primary navigation (`family-tree-ux.md` §4) and is served by `listFamilyMemories`. SCREEN-005 lists "Memories" first in its section order but does not describe the section. Nothing says either whether a story is plain text.
- **Question:** which screens show, edit and archive a Memory, and what does each contain?
- **Options:**
  - A — the following screens:
    - SCREEN-013 Memory:
      - a photo in its display version, with caption, taken date when known, related Persons (links to their profiles) and author and date; or a story's title and full text as plain text with line breaks kept;
      - `Edit` and `Archive` for the creator or an ADMIN.
    - SCREEN-014 Edit Memory: the fields of SCREEN-006 except the file, since a photo cannot be replaced.
    - Archive confirmation: a Modal saying the Memory disappears for the whole Family.
    - SCREEN-015 Family Memories (`Memories` tab): photo thumbnails and story cards, with the filter All / Photos / Stories.
    - SCREEN-005 Memories section: the Person's photo thumbnails and story cards, and `Add a memory` for ADMIN / CONTRIBUTOR.
  - B — the same without SCREEN-015; the `Memories` tab waits for a later phase.
- **Recommendation:** A. The second value moment ("open a grandparent and see photos or stories", `family-tree-ux.md` §15) needs SCREEN-013. SCREEN-015 is the only way to find a Memory without knowing who is on it.
- **Blocking:** the Memory screens (PR-30 to PR-33).
- **Answer:** A (human, 2026-09-26): SCREEN-013 Memory, SCREEN-014 Edit Memory, SCREEN-015 Family Memories; a story is plain text with line breaks kept. Documented in `screens.md` SCREEN-005 (Memories section), SCREEN-013, SCREEN-014, SCREEN-015 and SCREEN-006 (plain text).

### OQ-033 — The taken date of a photo in the forms

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `mvp.md` §17 lists `takenAt` / `takenAtPrecision` for a photo, and the contract accepts `takenAt` (a `PartialDate`) on create and update. But SCREEN-006 and the photo flow of `family-tree-ux.md` §13 do not ask for it. A story has no date in either spec.
- **Question:** can the User enter when a photo was taken, and where?
- **Options:** A — an optional "When was this photo taken?" (exact date / year only / unknown, as for a birth date), behind "More information" in SCREEN-006 and in SCREEN-014 / B — in SCREEN-014 only / C — not in the MVP UI; the API still accepts it.
- **Recommendation:** A. It follows the progressive disclosure of `family-tree-ux.md` §5, and old family photos are often dated by year only.
- **Blocking:** nothing in Phase 3: photos in Memories wait for OQ-042.
- **Answer:** A (human, 2026-09-26): optional exact / year only / unknown, behind "More information". Documented in `screens.md` SCREEN-006 and SCREEN-014; delivered with media in Memories (OQ-042).

### OQ-034 — Order and paging of Memory lists

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `listFamilyMemories` and `listPersonMemories` are paged, but no spec gives their order. `data-model.md` §23.4 indexes `(family_id, created_at DESC)`. The taken date is often unknown, so it cannot order every Memory.
- **Question:** in which order are Memories listed, and how much does the profile show at first?
- **Options:** A — most recently added first (`created_at DESC`, then `id`), pages of 20, then "Show more", in both lists / B — by taken date when known, then by date added.
- **Recommendation:** A. It is deterministic, matches the index and does not invent a chronology.
- **Blocking:** PR-31 and PR-32 (list queries and UI).
- **Answer:** A (human, 2026-09-26): most recently added first, pages of 20, "Show more". Documented in `openapi.yaml` (`listFamilyMemories`, `listPersonMemories`), `data-model.md` §23.3, §23.4 and `screens.md`.

### OQ-035 — Memories of an archived Person

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `mvp.md` §17 says "every Memory is linked to at least one ACTIVE Person", and `data-model.md` §15 keeps archived Persons "historically referenced but not selectable for new associations". Nothing says what happens when the only related Person of a Memory is archived, whether an edit may keep an archived Person already on the Memory, nor how an archived Person appears on a Memory.
- **Question:** when does "at least one ACTIVE Person" apply, and how are archived Persons shown on a Memory?
- **Options:**
  - A:
    - the rule is checked when a Memory is created or edited: the new list has at least one ACTIVE Person, and no Person newly added is non-ACTIVE; an archived Person already on the Memory may stay;
    - archiving a Person never changes nor blocks its Memories: they stay ACTIVE, in the Family list and on the profiles of their other Persons;
    - on a Memory, an archived Person is shown by name, marked "archived", and links to its profile only for the ADMIN (as for removed links, SCREEN-005);
    - restoring the Person brings everything back.
  - B — archiving a Person who is the only ACTIVE Person of some Memory is refused.
- **Recommendation:** A. Archiving stays reversible and never hides family content by side effect.
- **Blocking:** the related-Person rules of PR-29 and PR-33, and their display in PR-31.
- **Answer:** A (human, 2026-09-26): the rule applies on create and edit only; archiving a Person never changes its Memories; an archived Person is shown as archived on a Memory. Documented in `mvp.md` §17, `data-model.md` §15, `openapi.yaml` (`updateMemory`) and `screens.md` SCREEN-013, SCREEN-014.

### OQ-037 — Error codes of Memories and media

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `ProblemDetails.code` has no code for an unknown Memory or media asset. Several other cases have no stated response: a related Person that is unknown, of another Family, MERGED or ARCHIVED; a field irrelevant to the Memory type (`UpdateMemoryRequest` says "rejected" without a code); and a CONTRIBUTOR editing another member's Memory. `listFamilyMemories` also lists no 404 response, although another Family must answer 404 (AGENTS.md §5).
- **Question:** which status and code for each case?
- **Options:**
  - A:
    - an unknown Memory, another Family's, or an ARCHIVED one → 404 `MEMORY_NOT_FOUND`;
    - an unknown media asset or another Family's → 404 `MEDIA_NOT_FOUND`;
    - both codes are new and additive;
    - a related Person that is unknown, of another Family or MERGED → 404 `PERSON_NOT_FOUND`;
    - a newly added ARCHIVED Person → 409 `PERSON_NOT_ACTIVE` (as OQ-011);
    - a field irrelevant to the type → 400 `VALIDATION_FAILED` on that field;
    - someone other than the creator or an ADMIN → 403 `PERMISSION_DENIED`;
    - `404` is added to `listFamilyMemories` (additive).
  - B — reuse `RESOURCE_NOT_FOUND` for unknown Memories and media.
- **Recommendation:** A. It follows `PERSON_NOT_FOUND` and OQ-011, and the UI can explain each case.
- **Blocking:** the API tests and messages of PR-29 to PR-37 (only the codes; the rules can be built).
- **Answer:** A (human, 2026-09-26). Documented in `openapi.yaml` (`ProblemDetails.code`, Memory operations, 404 on `listFamilyMemories`) and `technical-specification.md` §12.

### OQ-038 — Memory count in the Quick View

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** SCREEN-COMPONENT-001 shows a "Memory count" for the Person. But `getFamilyTree` nodes (`PersonSummary`) and `PersonResponse` carry no count, and loading `listPersonMemories` per card would be N+1.
- **Question:** where does the Quick View get the Memory count?
- **Options:** A — add an optional `memoryCount` (ACTIVE Memories) to the tree nodes, counted in one query for all returned nodes (additive) / B — the Quick View calls `listPersonMemories` with `size=1` and reads `totalElements` when it opens.
- **Recommendation:** B. No contract change, one call only when a Quick View opens, and no cost on every tree load.
- **Blocking:** the Quick View part of PR-34 only.
- **Answer:** B (human, 2026-09-26): the Quick View calls `listPersonMemories` with `size=1`. Implementation choice, no spec change; recorded in `delivery/phase-3-family-memories.md` PR-34.

### OQ-039 — Audit of Memory mutations

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `data-model.md` §17 gives `MEMORY_ARCHIVED` as an example, and `technical-specification.md` §15 says important mutations are audited. But no list exists for Memories, as `genealogy.md` §13 gives for Persons.
- **Question:** which Memory and media operations write an audit entry, and with which values?
- **Options:** A — `MEMORY_CREATED` (type and related Person ids), `MEMORY_UPDATED` (field-focused, one entry per changed field; texts are not copied, only the fact that they changed), `MEMORY_ARCHIVED`. Media operations are not audited: their state is in `media_assets`. Storage keys and URLs are never written / B — `MEMORY_ARCHIVED` only.
- **Recommendation:** A. It gives support what it needs to restore an archived Memory and to answer "who changed this", without copying family texts into the audit.
- **Blocking:** the audit part of PR-29 and PR-33.
- **Answer:** A (human, 2026-09-26). Documented in `data-model.md` §17.

### OQ-040 — Setting, replacing and removing a Person's photo

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `CreatePersonRequest` / `UpdatePersonRequest` accept `profileMediaAssetId` (`PROFILE_PICTURE` purpose). `family-tree-ux.md` §5 shows "Photo" in the quick create form. But nothing says:
  - who may change a Person's photo;
  - whether it may come from a Memory photo;
  - how it is removed: OQ-008 makes an absent or `null` field "unchanged", so there is no way to clear it today;
  - how a photo is framed in a round avatar.
- **Question:** how is a Person's photo set, replaced and removed?
- **Options:**
  - A:
    - the photo follows the edit rules of the Person (`person-relationships-collaboration.md` §2, linked-Person protection);
    - "Add a photo" / "Change the photo" upload a new `PROFILE_PICTURE`. It is not chosen from Memories in the MVP;
    - "Remove the photo" sends a new boolean `removeProfilePicture: true` to `updatePerson` (additive, consistent with OQ-008);
    - the previous asset becomes `ARCHIVED`;
    - the image is shown centre-cropped in the circle, with no crop tool.
  - B — the same, with a crop step before upload.
- **Recommendation:** A.
- **Blocking:** PR-37 and PR-38.
- **Answer:** A (human, 2026-09-26), confirmed after the Memory discussion: a Person photo is the separate upload of option A. Documented in `openapi.yaml` (`UpdatePersonRequest.removeProfilePicture`, `updatePerson`) and `screens.md` SCREEN-012.

### OQ-041 — Memory rights of a creator who is no longer a CONTRIBUTOR

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26
- **Context:** `mvp.md` §4 lets "the creator" edit or archive their own Memory. A VIEWER is read-only. Once roles can change (member management, a later phase), a creator may become a VIEWER.
- **Question:** may a creator who is now a VIEWER still edit or archive their own Memory?
- **Options:** A — no: editing and archiving need the ADMIN or CONTRIBUTOR role, and then being the creator or the ADMIN / B — yes: the creator keeps these rights whatever their role.
- **Recommendation:** A. VIEWER stays strictly read-only.
- **Blocking:** nothing in Phase 3, where roles do not change; the rule is implemented as A and changed if the answer is B.
- **Answer:** A (human, 2026-09-26). Documented in `mvp.md` §17, `person-relationships-collaboration.md` §12 and `openapi.yaml` (`updateMemory`, `archiveMemory`).

### OQ-036 — Lifecycle of an uploaded Person photo

- **Raised by / date:** coding agent (Phase 3 plan), 2026-09-26; narrowed after the human's answer on Memories (OQ-042)
- **Context:** in Phase 3 the only uploaded images are Person photos (`PROFILE_PICTURE`, OQ-040); Memories have no media (OQ-042). `data-model.md` §13 and ADR-007 describe upload and processing, but leave several points open: who may attach a READY asset, whether one asset may serve several Persons, and what happens to a READY asset that is never attached (only `PENDING_UPLOAD` assets are cleaned up after 24 h).
- **Question:** what are the rules for an uploaded Person photo, from upload to replacement?
- **Options:**
  - A:
    - only its uploader may complete an asset and attach it, once, to one Person. An already used asset → 409 with a new code `MEDIA_ALREADY_USED` (additive);
    - a READY asset still unattached 24 hours after `ready_at` becomes `FAILED`, and its files are deleted by the same scheduled task as ADR-007 §4;
    - a replaced or removed photo becomes `ARCHIVED` (OQ-040) and serves no URL.
  - B — the same, but unattached READY assets are kept.
- **Recommendation:** A. No uploaded photo stays stored without being visible, and no member can take over another member's upload.
- **Blocking:** the Person photo PRs (PR-36, PR-37).
- **Answer:** A (human, 2026-09-26). Documented in `data-model.md` §13, `openapi.yaml` (`completeMediaUpload`, `UpdatePersonRequest`, `ProblemDetails.code`) and `technical-specification.md` §12.

### OQ-043 — "At least one ACTIVE Person" on an edit that keeps the Persons

- **Raised by / date:** coding agent (PR-33), 2026-09-26
- **Context:** OQ-035 checks "at least one ACTIVE Person" when a Memory is created or edited, and lets an archived Person already on the Memory stay. Once every Person of a Memory has been archived, the specs do not say whether an edit that does not change its Persons (only the title or the text, or the same list sent again) is refused.
- **Question:** does the rule apply to every edit, or only to an edit that changes the related Persons?
- **Options:** A — only when the set of related Persons actually changes: the title and text of such a Memory can still be corrected / B — on every edit: the editor must first add an ACTIVE Person.
- **Recommendation:** A. Correcting a typo must not require relinking the story, and archiving a Person never changes its Memories (OQ-035).
- **Blocking:** the related-Person rule of PR-33.
- **Answer:** A (human, 2026-09-26). Documented in `mvp.md` §17, `data-model.md` §15, `openapi.yaml` (`updateMemory`) and `screens.md` SCREEN-014.
