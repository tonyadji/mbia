# Phase 2 — Core Family Graph

**Status:** Ready  
**Spec baseline:** `mbia-specs/` 0.3 (see `README.md` changelog)  
**Branch base:** `develop`, after PR-16

This is a delivery plan. It does not define product behavior; the specifications do. If this plan and a spec disagree, the spec wins and the plan must be corrected.

## 1. Goal

Prove that a person can represent their close family in Mbia, navigate it naturally and understand how people are related, without learning genealogy concepts. Memories and invitations come later.

At the end of Phase 2, the following journey works without technical intervention, in FR and EN, at phone width:

```text
sign in
→ create or open a Family
→ "Start with me": create my linked Person
→ add my mother and my father
→ add a grandparent
→ open the tree
→ recenter on the grandparent
→ see the localized "your grandparent" label
→ open the relationship path
→ search for a Person
→ open a Person profile
→ edit an allowed field
→ remove an incorrect relationship
→ restore it as ADMIN
→ handle a possible duplicate
→ ADMIN merges a confirmed duplicate
```

Roles stay those of Phase 1 (ADMIN, CONTRIBUTOR, VIEWER). Without invitations, manual tests mostly use the Family creator, but every rule is implemented and tested for the three roles: the contract and later collaboration depend on them.

## 2. How to run this phase

### 2.1 Rules

Phase 1 rules apply (`phase-1-walking-skeleton.md` §2.1), with these differences:

- PRs are **vertical slices** when possible: contract use, backend, frontend and tests for one user-visible capability.
- Target size: **≤ ~700 lines of hand-written code** per PR (generated code and lockfiles excluded). Beyond that, split before adding unrelated work.
- No new infrastructure or framework without an accepted ADR.
- Features of later phases are forbidden even when easy (§5).

### 2.2 Prompt template for the agent

```text
Implement PR-XX from mbia-specs/delivery/phase-2-core-family-graph.md.

1. Read AGENTS.md, the PR-XX section, §3 (phase-wide constraints), and every
   spec section it references.
2. Before coding, write a short plan: files to create/change, tests to write,
   API/schema impact, and any question or ambiguity. Wait for my approval.
3. Implement only PR-XX scope. Write the tests from the acceptance criteria.
4. Run all checks listed in AGENTS.md §4.
5. Do not commit, push or open the PR yourself: hand over a commit message
   and a PR description titled "PR-XX: <title>" (target develop) listing:
   spec sections covered, acceptance criteria with how each is verified,
   deviations or open questions, and how to run the human check.
```

### 2.3 Human review checklist (every PR)

Same as `phase-1-walking-skeleton.md` §2.3, plus:

- [ ] Nothing from §5 (no Memory, photo, invitation or activity placeholder).
- [ ] Every new screen checked at 375 px, in FR and EN.

## 3. Phase-wide constraints

These apply to every PR of this phase. They restrict what is delivered now; they do not change the specs.

### 3.1 No media yet

- Persons have no photo: cards, Quick View, profile and search results show initials / a neutral avatar.
- No photo control in the Add Person / Add Relative / Edit Person forms (`family-tree-ux.md` §5 lists "Photo": not in this phase).
- `profilePictureUrl` is always `null`.
- A `profileMediaAssetId` sent to `createPerson` or `updatePerson` is ignored: the request behaves as if the field were absent (OQ-005).

### 3.2 No Memories, invitations or activity

- No Memory section on the profile, no Memory count in the Quick View or Family Home, no recent activity, no "Invite" or "Add Memory" action, no disabled placeholder for them.
- `FamilyStats.memoryCount` stays `0`.
- Merge moves relationships and the linked User only; Memory and media steps of `data-model.md` §19 do not apply yet.

### 3.3 Schema

Phase 2 migrations may create only:

| Migration | Content | Spec |
|---|---|---|
| `V003__persons.sql` | `persons` **without** `profile_media_asset_id` | `data-model.md` §9–10 |
| `V004__family_relationships.sql` | `family_relationships` | `data-model.md` §11, §23.2, §23.2bis |
| `V005__unaccent.sql` (PR-25) | `CREATE EXTENSION IF NOT EXISTS unaccent` | `genealogy.md` §11 |
| `V006__audit_entries.sql` (PR-28) | `audit_entries`, index `(family_id, resource_type, resource_id, occurred_at DESC)` | `data-model.md` §17 |

Migrations are numbered in delivery order: Flyway refuses a lower version once a higher one is applied.

Enumerations are checked `VARCHAR` columns, as in V001–V002.

Not created in this phase: `family_invitations`, `media_assets`, `memories`, `memory_persons`, `activities`.

### 3.4 Audit before V006

Every important mutation (`genealogy.md` §13) goes through an application audit port from its first PR. Until PR-28 creates `audit_entries`, the port's implementation does nothing; PR-28 plugs the real one without changing use-case behavior.

### 3.5 Kinship scope

Supported labels are those of `localization-and-kinship-labels.md` §3. `IMPLAUSIBLE_GENERATION_GAP` is never emitted (`person-relationships-collaboration.md` §7.1). No generic Nth-degree cousins, in-laws, marriage or parent subtypes.

---

## 4. PR list

| PR | Title | Main user value |
|---|---|---|
| PR-17 | Create Person / Start with me | the first Person exists |
| PR-18 | Person profile read & edit | maintain identity |
| PR-19 | Claim / unclaim & linked-Person protection | "this is me" |
| PR-20 | Create relationships & Add Relative | the graph starts |
| PR-21 | Kinship resolver | understand relationships |
| PR-22 | Family tree read model | navigable graph (backend) + profile Family section |
| PR-23 | Family tree UI & Quick View | first visual "aha" moment |
| PR-24 | Remove / restore relationships | correct the graph |
| PR-25 | Search & Add Relative with an existing Person | grow the graph easily |
| PR-26 | Archive / restore Person | remove mistakes safely |
| PR-27 | Duplicate detection & merge | recover from duplicates |
| PR-28 | Person history & Phase 2 hardening | traceability + release |

---

## PR-17 — Create Person / Start with me

**Goal:** a Family gets its first Person through a real vertical slice.

**Scope**

- `V003__persons.sql` (§3.3), with the constraints and indexes of `data-model.md` §10 and the `(id, family_id)` unique key.
- `genealogy` module: `Person`, `PartialDate` and value objects, `PersonRepository`, `CreatePersonUseCase` implementing `createPerson`.
- `linkToCurrentUser = true` creates and links the Person in one transaction.
- Duplicate check behind a port that returns "no candidates"; the real rule is PR-27.
- `FamilyStats.personCount` backed by real data.
- SCREEN-002: empty state with actions by role; with Persons, Family name, Person count and `Add a person` (`Add a relative` arrives with PR-20, `View family tree` with PR-23).
- SCREEN-004, modes "Start with me" and "Standalone Person".

**Out of scope:** profile screen, relationships, search, photo (§3.1), real duplicate detection.

**Specs:** `product/mvp.md` §6, §7, §14; `product/ux/screens.md` SCREEN-002, SCREEN-004; `product/ux/family-tree-ux.md` §5; `technical/data-model.md` §9, §10, §21; `technical/genealogy.md` §1–6; `openapi.yaml` `createPerson`.

**Acceptance criteria**

- `firstName` blank or missing → 400 `VALIDATION_FAILED`.
- Inconsistent partial dates, or a death date for a non-deceased Person → 400.
- A User gets at most one linked Person per Family.
- ADMIN and CONTRIBUTOR can create; VIEWER → 403.
- Another Family's id → 404.
- No profile photo column, field or control; a `profileMediaAssetId` in the request is ignored (Person created, no error, `profilePictureUrl = null`).
- E2E: create Family → "Start with me" → Family Home shows 1 Person.

**Human check:** create "me" in FR and EN at 375 px.

---

## PR-18 — Person profile read & edit

**Goal:** open and maintain a Person's identity.

**Scope**

- `getPerson`, `updatePerson` with ETag / `If-Match`.
- Display-name rule.
- SCREEN-005 (header, About; the Family section stays empty until PR-22; no Memory section, §3.2).
- SCREEN-012 Edit Person, including exact / year-only / unknown dates and biography.

**Out of scope:** linked-Person protection (PR-19), History section (PR-28), relatives.

**Specs:** `product/mvp.md` §6, §16; `product/domain/person-relationships-collaboration.md` §2 (non-linked Person), §11; `product/ux/screens.md` SCREEN-005, SCREEN-012; `technical/technical-specification.md` §13; `openapi.yaml` `getPerson`, `updatePerson`.

**Acceptance criteria**

- Stale `If-Match` → 409 `CONCURRENT_MODIFICATION`; the UI offers "Reload latest version".
- ADMIN and CONTRIBUTOR edit a non-linked Person; VIEWER → 403.
- Invalid death/date combinations → 400.
- Unknown or other-Family Person → 404.
- A `profileMediaAssetId` in the request is ignored and changes nothing.

**Human check:** edit a Person in two tabs and see the conflict message.

---

## PR-19 — Claim / unclaim & linked-Person protection

**Goal:** an existing Person can safely become "me".

**Scope**

- `claimPerson`, `unclaimPerson` (own link; ADMIN corrects any link).
- Linked-Person identity protection in `updatePerson`.
- SCREEN-005 actions `This is me` and unlink.
- Link and release go through the audit port (§3.4).

**Out of scope:** member removal/leave (later phase).

**Specs:** `product/mvp.md` §4 (VIEWER), §7; `product/domain/person-relationships-collaboration.md` §2, §12; `technical/data-model.md` §21; `openapi.yaml` `claimPerson`, `unclaimPerson`.

**Acceptance criteria**

- Claiming a second Person in the same Family → refused.
- Claiming a Person linked to another User → refused.
- Another CONTRIBUTOR cannot edit a linked Person's identity; the linked User and an ADMIN can.
- A VIEWER can claim and unclaim their own Person.

**Human check:** claim, edit and unclaim a Person as the creator.

---

## PR-20 — Create relationships & Add Relative

**Goal:** create the explicit family graph from human choices.

**Scope**

- `V004__family_relationships.sql` (§3.3).
- Relationship domain and repository; `CreateRelationshipUseCase` implementing `createRelationship`.
- Hard blocks: self relation, exact active duplicate, other Family, ARCHIVED/MERGED Person, parental cycle (recursive CTE, same transaction).
- `PARTNER_OF` canonicalisation.
- Date warnings and confirmation (`confirmWarnings`).
- SCREEN-004 "Relative of a Person" mode, new-Person path, from SCREEN-005 and from Family Home (`Add a relative`), with the gendered shortcuts.
- New Person + relationship are two API calls; when the relationship fails, the Person stays and the UI says they were added but not linked. No composite endpoint.

**Out of scope:** choosing an existing Person (PR-25), removal (PR-24), kinship labels (PR-21).

**Specs:** `product/mvp.md` §8, §10; `product/domain/person-relationships-collaboration.md` §6, §7, §7.1; `product/ux/family-tree-ux.md` §9, §9.1, §14; `product/ux/screens.md` SCREEN-004; `technical/data-model.md` §11; `technical/genealogy.md` §6–8; `openapi.yaml` `createRelationship`.

**Acceptance criteria**

- Each hard block has a use-case test and a Testcontainers test; each is shown to the User in human language.
- Reverse `PARTNER_OF` duplicate is refused.
- `PARENT_BORN_AFTER_CHILD` and `IMPLAUSIBLE_PARENT_AGE` trigger at the specified thresholds, block without confirmation, pass with it.
- ADMIN and CONTRIBUTOR create; VIEWER → 403; other Family → 404.

**Human check:** add mother, father and a grandparent from "me", including one date warning.

---

## PR-21 — Kinship resolver

**Goal:** Mbia explains supported family relationships.

**Scope**

- Breadth-first resolver with deterministic tie-breaking; `getKinship`.
- All supported `KinshipCode`s, gendered by the target Person.
- FR/EN labels and path sentences in the frontend.

**Out of scope:** §3.5; persisted kinship.

**Specs:** `product/mvp.md` §9; `product/domain/person-relationships-collaboration.md` §9, §10; `product/ux/localization-and-kinship-labels.md` §2–4; `technical/genealogy.md` §9; `openapi.yaml` `getKinship`.

**Acceptance criteria**

- Graph-fixture unit tests for every supported pattern, half-siblings, several partners, several shortest paths (tie-breaking), `RELATED` and `NONE_KNOWN`.
- Archived relationships and Persons are not traversed.

**Human check:** read the FR and EN label of each fixture relationship.

---

## PR-22 — Family tree read model

**Goal:** one API call returns the depth-1 local graph.

**Scope**

- `getFamilyTree` (the UI uses depth 1).
- Focus fallback when `focusPersonId` is absent.
- Parents, partners, children, siblings, and ACTIVE relationships among returned nodes.
- `hasMoreParents` / `hasMoreChildren`; `relationshipToCurrentUser` without N+1.
- SCREEN-005 Family section and relationship badge, fed by this call.

**Specs:** `product/mvp.md` §15; `product/ux/family-tree-ux.md` §6; `technical/genealogy.md` §10, §15; `technical/data-model.md` §23.2; `openapi.yaml` `getFamilyTree`.

**Acceptance criteria**

- Empty Family → `focusPersonId = null`, no nodes, no edges.
- Focus fallback order and its tie-breaks tested.
- Query count bounded independently of Family size (250-Person fixture).

**Human check:** the profile of "me" lists parents and grandparent correctly.

---

## PR-23 — Family tree UI & Quick View

**Goal:** the first visual "aha" moment.

**Scope**

- SCREEN-003 with the fixed three-row layout, Person cards with initials, loading behavior.
- SCREEN-COMPONENT-001 Quick View (no Memory count, §3.2); SCREEN-COMPONENT-002 Siblings list.
- Recentering, horizontal pan, zoom controls, pinch-zoom.
- Last focused Person remembered per Family in browser storage.
- Family Home `View family tree`.
- "See how" path explanation from the relationship label.

**Out of scope:** any generic graph-layout library.

**Specs:** `product/ux/family-tree-ux.md` §6, §6.1, §7, §8, §10, §11; `product/ux/screens.md` SCREEN-003, SCREEN-COMPONENT-001, SCREEN-COMPONENT-002; `product/ux/localization-and-kinship-labels.md` §3–4.

**Acceptance criteria**

- Layout tests for several parents, several partners, children grouped by other parent.
- Works at 375 px and on desktop (Playwright).

**Human check:** navigate three generations on a phone.

---

## PR-24 — Remove / restore relationships

**Goal:** graph errors are reversible without SQL.

**Scope**

- `archiveRelationship` (ADMIN, CONTRIBUTOR), `restoreRelationship` (ADMIN) with `If-Match`.
- Restore re-runs every current validity rule.
- `listArchivedPersonRelationships` (ADMIN).
- SCREEN-005: `Remove link` on parents, partners and children, with SCREEN-COMPONENT-003; ADMIN "Removed links" area with `Restore`.
- Tree and profile refresh after mutation.

**Specs:** `product/mvp.md` §13; `product/domain/person-relationships-collaboration.md` §8; `product/ux/screens.md` SCREEN-005 (Family section, Removed links), SCREEN-COMPONENT-003; `technical/data-model.md` §20, §23.2bis; `technical/genealogy.md` §11bis; `openapi.yaml` `archiveRelationship`, `restoreRelationship`, `listArchivedPersonRelationships`.

**Acceptance criteria**

- CONTRIBUTOR can remove, cannot restore; VIEWER can do neither.
- Restoring a relationship that would now create a cycle or duplicate, or that involves an archived Person → refused, explained in human language.
- `listArchivedPersonRelationships`: CONTRIBUTOR and VIEWER → 403; other Family → 404; returns only ARCHIVED relationships of that Person, with the other Person.
- Kinship and tree change right after removal.

**Human check:** remove a wrong parent link and restore it as ADMIN.

---

## PR-25 — Search & Add Relative with an existing Person

**Goal:** navigate and grow a larger Family.

**Scope**

- Real `searchPersons`: accent- and case-insensitive, deterministic order (`V005__unaccent.sql`, §3.3).
- SCREEN-007 with its three entry points.
- SCREEN-004 "search existing" path.

**Specs:** `product/mvp.md` §19; `product/ux/screens.md` SCREEN-004, SCREEN-007; `technical/genealogy.md` §11; `technical/data-model.md` §23.1; `openapi.yaml` `searchPersons`.

**Acceptance criteria**

- "Eloise" finds "Éloïse"; ARCHIVED and MERGED Persons are never returned.
- Order tested with equal display names.
- 250-Person search smoke test.

**Human check:** find a Person from the tree, from Add Relative and from general navigation.

---

## PR-26 — Archive / restore Person

**Goal:** remove a mistaken Person safely.

**Scope**

- `archivePerson`, `restorePerson`, ADMIN only.
- A linked Person cannot be archived.
- Archived Persons are hidden from tree and search and cannot receive relationships.
- `searchPersons` with `status=ARCHIVED` (ADMIN).
- SCREEN-005 ADMIN archive / restore actions with confirmation, and the archived-Person notice.
- SCREEN-007 ADMIN "Archived people" view.

**Specs:** `product/mvp.md` §13; `product/domain/person-relationships-collaboration.md` §5; `product/ux/screens.md` SCREEN-005 (Archived Person), SCREEN-007; `technical/genealogy.md` §11; `openapi.yaml` `archivePerson`, `restorePerson`, `searchPersons`, `getPerson`.

**Acceptance criteria**

- CONTRIBUTOR and VIEWER → 403.
- Archiving a linked Person → refused.
- After archive: absent from tree, default search and kinship paths; relationship creation to them refused.
- `status=ARCHIVED` lists them for the ADMIN only (CONTRIBUTOR and VIEWER → 403); MERGED Persons never appear.
- `getPerson` still returns an archived Person.

**Human check:** archive and restore a Person.

---

## PR-27 — Duplicate detection & merge

**Goal:** recover from duplicate Persons.

**Scope**

- The deterministic candidate rule replaces the PR-17 stub; `POSSIBLE_DUPLICATE` UI on SCREEN-004.
- `mergePerson`: row locks in UUID order, version checks, relationship transfer, canonicalisation and deduplication, linked-User rules, source → MERGED, target stays ACTIVE (§3.2 for Memories).
- SCREEN-COMPONENT-004.

**Specs:** `product/mvp.md` §11, §12; `product/domain/person-relationships-collaboration.md` §4; `product/ux/screens.md` SCREEN-004, SCREEN-COMPONENT-004; `technical/data-model.md` §19, §22; `technical/genealogy.md` §12; `openapi.yaml` `createPerson`, `mergePerson`.

**Acceptance criteria**

- Candidate rule tested on each criterion, including missing values and accents.
- Merge refused for two different linked Users.
- Merge producing a self relation or a cycle is rolled back entirely.
- Stale source or target version → 409.

**Human check:** create a duplicate, then merge it as ADMIN.

---

## PR-28 — Person history & Phase 2 hardening

**Goal:** the phase is releasable and auditable.

**Scope**

- `V006__audit_entries.sql`; the real audit adapter behind the §3.4 port.
- `getPersonHistory`; SCREEN-005 History section.
- Playwright spec replaying the §1 journey.
- Check that no later-phase UI or placeholder exists (§5).
- Accessibility pass on Phase 2 screens.
- 250-Person tree and search smoke tests.

**Specs:** `product/domain/person-relationships-collaboration.md` §3; `technical/data-model.md` §17, §18; `technical/genealogy.md` §13; `openapi.yaml` `getPersonHistory`.

**Acceptance criteria**

- Every mutation of `genealogy.md` §13 writes an audit entry, without secrets.
- History shows only presentation-safe entries.
- The §1 journey passes in CI.

**Human check:** run the §1 journey by hand, in FR and EN, at 375 px.

---

## 5. Not in Phase 2 (planned later)

Photos and Stories, media upload and processing, invitations (email and link), member management and leaving a Family, activity feed, analytics events, comments / likes / notifications, depth-2 tree, generic cousin degrees, in-law labels, marriage semantics, parent subtypes, branch-level privacy, graph database, cache, message broker, search engine.

## 6. Phase 2 exit criteria

- [ ] PR-17 to PR-28 merged into `develop`, CI green.
- [ ] The §1 journey passes in Playwright, and manually at 375 px in FR and EN.
- [ ] Every hard graph rule has automated tests (cycle, self relation, cross-Family, duplicate, linked-Person protection, warning confirmation, archive/restore, merge atomicity).
- [ ] No Memory, invitation, media or activity feature or placeholder.
- [ ] `mbia-specs/open-questions.md` has no open question blocking Phase 2.
- [ ] `develop` can be tagged `phase-2-complete`.
