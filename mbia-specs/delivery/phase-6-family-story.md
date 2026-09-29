# Phase 6 — The family story

**Status:** Draft, for the human's review  
**Spec baseline:** `mbia-specs/` 0.7.1 (the family story first; answers to OQ-063 to OQ-067; contract `openapi.yaml` 0.5.0, see `README.md` changelog)  
**Branch base:** `develop`, after PR-57 and the specs 0.7 (tag `phase-5-complete` once its human check is done)

This is a delivery plan. It does not define product behavior; the specs do. If this plan and a spec disagree, the spec wins and the plan must be corrected.

## 1. Goal

Mbia answers "what is the story of my family?" before "what is my family tree?" (`vision.md` §1, `mvp.md` §1). A Memory says when it happened; Family Home opens on the family story, a strip of years; a year tells "what happened" that year; a new Family starts by telling a first memory; the tree comes after, as the index of the story.

At the end of Phase 6, the following journey works without technical intervention, in FR and EN, at phone width:

```text
sign up, create a Family
→ Family Home: "Tell a first memory" → "Who is this memory about?" → Me
→ a story, its year (1975) and a photo → published
→ Family Home opens on "Our story": 1975 · 1 memory
→ Tell a memory about someone not in the tree yet (Marie, added on the way), on 12 March 1962
→ an undated memory
→ the strip: 1962, 1975, Undated memories; open 1962: "What happened in 1962"
→ move to 1975 and to the undated memories without going back
→ invite Awa (Marie's daughter, added on the way) by a link; she joins and recognises herself
→ Awa tells a memory of 1980: it appears in the strip for the ADMIN
→ the ADMIN changes the date of a memory from 1975 to 1962: the strip follows
→ the ADMIN adds the relationships (Marie mother of Awa, Awa mother of me) and views the tree
→ a future date is refused and explains itself
```

This closes the new `mvp.md` §28. `mvp-release-criteria.spec.ts` follows the new release criteria (PR-63).

## 2. How to run this phase

### 2.1 Rules

Phase 3 rules apply (`phase-3-family-memories.md` §2.1):

- vertical slices;
- **≤ ~700 lines of hand-written application code** per PR (generated code, lockfiles and binary test fixtures excluded; tests are counted separately and may exceed it);
- no new infrastructure or framework without an accepted ADR;
- features of later phases are forbidden even when easy (§5).

No open question blocks this phase: OQ-063 to OQ-067 are decided; OQ-068 (family questions) concerns V1.

### 2.2 Prompt template for the agent

Same as `phase-2-core-family-graph.md` §2.2, with `phase-6-family-story.md`.

### 2.3 Human review checklist (every PR)

Same as `phase-4-memory-photos.md` §2.3, plus:

- [ ] Every new or changed screen checked at 375 px, in FR and EN, with a long title and a story without photo.
- [ ] Years are written with digits only (`1975`), dates in the reader's language (`localization-and-kinship-labels.md` §1).
- [ ] Permissions checked for ADMIN, CONTRIBUTOR, VIEWER and a member of another Family.

## 3. Phase-wide constraints

These apply to every PR of this phase. They restrict what is delivered now; they do not change the specs.

### 3.1 Contract (additive, applied with the specs)

The Phase 6 contract changes are **additive** and already in `openapi.yaml` 0.5.0: `api-breaking` must stay green without the `api-breaking-approved` label.

| Change | Detail |
|---|---|
| `info.version` | `0.5.0` |
| `MemoryDate` (new) | a `PartialDate` never in the future (OQ-063) |
| `MemoryResponse.happenedAt` | `MemoryDate`, always present, UNKNOWN when not known |
| `CreateStoryMemoryRequest.happenedAt` | optional; absent or `null`: UNKNOWN |
| `UpdateMemoryRequest.happenedAt` | optional; absent or `null` keeps it, UNKNOWN removes it |
| `listFamilyMemories` `year`, `undated` | the Memories of a year, or without a year, in the order of OQ-064; both together → 400 |
| `listFamilyStoryYears` (new) | `GET /families/{familyId}/story/years` → `FamilyStoryYears { years[{year, memoryCount}], undatedMemoryCount }` |

The deprecated Memory-level `takenAt` stays as it is: always null, refused on update (OQ-042, OQ-063).

### 3.2 Schema

Phase 6 migrations may create only:

| Migration | Content | Spec |
|---|---|---|
| `V012__memory_happened_date.sql` (PR-58) | `memories.happened_date`, `happened_year`, `happened_date_precision` (default `UNKNOWN`), the check `ck_memory_happened_date`, the index `idx_memories_family_story_year` | `data-model.md` §14, §23.5 |

Every existing Memory becomes undated. Committed migrations stay immutable.

### 3.3 Module placement

- Everything lives in the `memory` module. The Memory's date is a value object of the `memory` domain: `TakenDate` may be generalised (its field name becomes a parameter), but no module uses another module's `domain` package, and the architecture tests stay unchanged.
- "Never in the future" uses the server's current day in UTC plus one day of tolerance (`openapi.yaml` `MemoryDate`), through an injected `Clock` so that tests fix the day.
- The strip of years is one grouped query; a year and the undated list are paged like `listFamilyMemories` today. Query counts do not grow with the page (fixture tests, as in Phase 3).
- `MEMORY_CREATED` activity keeps `memoryTitle` only (OQ-063).

### 3.4 Onboarding

- `Tell a first memory` (SCREEN-002, SCREEN-006) uses existing operations: `createPerson` (with `linkToCurrentUser` for `Me`), then `createStoryMemory`. No new operation (OQ-065).
- A Person created from the form is created at publication, just before the Memory; possible duplicates are offered as choices; if the Memory is then refused, the Person stays and the form keeps what was written.
- `Start with me` stays on the empty tree (SCREEN-003) and in the invitee's onboarding (SCREEN-010); `AddPersonPage` keeps its `mode=me`.

### 3.5 Tests

- The e2e specs that open a new Family with `Start with me` on Family Home (22 of them) move to a shared helper in `e2e/support/` that starts the Family the new way, so that each spec keeps testing its own subject. Their assertions do not change otherwise.
- `expectAccessibleControls` and `expectReadableScreen` (`e2e/support/accessibility.ts`) run on every new or changed screen.

---

## 4. PR list

| PR | Title | Main user value |
|---|---|---|
| PR-58 | Memory date (backend) | a Memory says when it happened |
| PR-59 | Family story queries (backend) | the years of the story and the Memories of a year |
| PR-60 | Memory date in the screens | set, change and read when a Memory happened |
| PR-61 | Family story screens | read the family's story, year after year |
| PR-62 | A Family starts with a memory | a new Family tells its first memory before building the tree |
| PR-63 | Phase 6 hardening & MVP north star | release |

---

## PR-58 — Memory date (backend)

**Goal:** a Memory says when it happened.

**Scope**

- `V012__memory_happened_date.sql` (§3.2).
- `happenedAt` on `createStoryMemory`, `updateMemory`, `getMemory` and every Memory response (lists included); the future-date rule (§3.3); the consistency rules of `PartialDate`.
- Audit: `MEMORY_UPDATED` of the field `happenedAt` with the dates before and after (`data-model.md` §17); `MEMORY_CREATED` unchanged.

**Out of scope:** the family story queries (PR-59), UI.

**Specs:** `product/mvp.md` §17; OQ-063; `technical/data-model.md` §9, §14, §17; `openapi.yaml` `MemoryDate`, `createStoryMemory`, `updateMemory`.

**Acceptance criteria**

- A Memory is created with an EXACT date, a year only, or no date (absent, `null` or UNKNOWN); every response carries `happenedAt`.
- An update sets, changes or removes the date; absent or `null` keeps it; a stale `If-Match` → 409.
- Inconsistent combinations and a date or year after the current day (plus one day) → 400 `VALIDATION_FAILED` on `happenedAt`; the day before the limit is accepted (fixed `Clock`).
- The schema check refuses an inconsistent row (schema test); existing Memories read as UNKNOWN.
- The deprecated `takenAt` is still refused on update and still null in responses.
- The audit records the dates of `happenedAt`, never a text; the activity of a new Memory is unchanged.

**Human check:** with `curl`, create a Memory in 1975, change it to 12 March 1962, then remove its date.

---

## PR-59 — Family story queries (backend)

**Goal:** the years of the family story and the Memories of a year.

**Scope**

- `listFamilyStoryYears` (§3.1), one grouped query on the story year (`data-model.md` §23.5).
- `listFamilyMemories` with `year` or `undated`, in the order of OQ-064; both → 400.

**Specs:** `product/mvp.md` §20; OQ-064; `technical/data-model.md` §14, §23.5; `openapi.yaml` `listFamilyStoryYears`, `listFamilyMemories`.

**Acceptance criteria**

- The strip lists only the years with ACTIVE Memories, oldest first, with their counts, and the undated count; archived Memories and those of other Families never count; a Family without Memory → empty list and 0.
- A year returns its EXACT dates first by date, then its years only by addition, then `id`; undated returns most recently added first; paging works as today.
- A Memory whose date changes moves from one year to another; an archived one leaves its year.
- Every member, VIEWER included, reads the story; another Family → 404 without data (isolation test, as `CollaborationFamilyIsolationApiTest`).
- The query count of the strip and of a year page does not grow with the number of Memories (fixture).

**Human check:** with `curl`, list the years of a test Family, then the Memories of one year and the undated ones.

---

## PR-60 — Memory date in the screens

**Goal:** set, change and read when a Memory happened.

**Scope**

- SCREEN-006 and SCREEN-014: `When did it happen?` (exact date, year only or unknown), the date picker extracted from `MemoryPhotosField` and shared with the photo taken date; a future date explained in human language.
- SCREEN-013: "In 1975" / "On 12 March 1975", linking to the year of the family story (the link target arrives with PR-61; until then the date is plain text).

**Specs:** `product/ux/screens.md` SCREEN-006, SCREEN-013, SCREEN-014; `product/mvp.md` §17; `product/ux/localization-and-kinship-labels.md` §1; OQ-063, OQ-067.

**Acceptance criteria**

- A Memory is published with a year, an exact date or no date, and edited to change or remove it; the photo taken dates keep working unchanged.
- A future date is refused before sending, and the server's refusal is explained if it happens anyway.
- The date reads in the reader's language, the year in digits only.
- Every control has an accessible name; E2E at 375 px, FR and EN.

**Human check:** on a phone, date a Memory, change its date, then remove it.

---

## PR-61 — Family story screens

**Goal:** read the family's story, year after year.

**Scope**

- The `YearStrip` component (`design-guidelines.md` §7): swipe and keyboard, the current entry marked by more than its color, opened on the most recent year.
- SCREEN-002: "Our story" first on the screen, with the strip; the invitation to tell a first memory when the Family has Persons but no Memory.
- SCREEN-016: "What happened in {year}" and the undated Memories, the strip kept in view, `Previous year` / `Next year`, 20 at a time with `Show more`, the empty state of a year left without Memory, an invalid year not found.
- The SCREEN-013 date links to its year.

**Out of scope:** the new primary action and empty state of Family Home (PR-62).

**Specs:** `product/mvp.md` §20; `product/ux/screens.md` SCREEN-002, SCREEN-013, SCREEN-016; `product/ux/design-guidelines.md` §7, §9; `product/ux/localization-and-kinship-labels.md` §1; OQ-064, OQ-067.

**Acceptance criteria**

- The strip shows the years of the Family with their counts and the undated entry; it is hidden without Memory.
- A year shows its Memories in the order of OQ-064, each with its date; another year opens from the same screen, and `Previous year` / `Next year` stop at the ends.
- A VIEWER reads the story; another Family's year → "Family not found".
- The strip works by swipe and by keyboard, and never scrolls the page sideways at 375 px.
- Every control has an accessible name; E2E at 375 px, FR and EN.

**Human check:** on a phone, read the story of a test Family with Memories over several years.

---

## PR-62 — A Family starts with a memory

**Goal:** a new Family tells its first memory before building the tree.

**Scope**

- SCREEN-002: `Tell a memory` as the primary action for ADMIN / CONTRIBUTOR; `View family tree` and `Add a relative` / `Add a person` as secondary actions; the counts after the story; the empty state `Tell a first memory` / `Add a person`.
- SCREEN-003: its own empty state with `Start with me` and `Add someone else`.
- SCREEN-006: "Who is this memory about?" (`Me` / `Someone else`) when the Family has no Person; `Add {typed name}` in the related-Persons field; the Person created at publication (§3.4).
- The e2e helper of §3.5 and the migration of the specs that used `Start with me` on Family Home.

**Specs:** `product/mvp.md` §14, §20; `product/ux/screens.md` SCREEN-002, SCREEN-003, SCREEN-006; `product/ux/family-tree-ux.md` §13; OQ-065, OQ-066.

**Acceptance criteria**

- A new Family shows `Tell a first memory`; `Me` publishes a Memory linked to the User's own new Person, `Someone else` to a new Person; the Memory appears in the family story.
- A Person added from the related-Persons field is created with the Memory; a possible duplicate is offered as a choice; a Memory refused after its Person was created keeps the form and says so.
- The empty tree offers `Start with me`; the invitee's `No → create Person` still works.
- A VIEWER sees no mutation action on Family Home.
- Every e2e spec passes with the shared helper; E2E at 375 px, FR and EN.

**Human check:** on a phone, create a new Family and tell its first memory about yourself, then one about a grandparent not in the tree.

---

## PR-63 — Phase 6 hardening & MVP north star

**Goal:** the phase and the new MVP journey are releasable.

**Scope**

- `e2e/phase-6-journey.spec.ts` replaying §1 in FR and EN at 375 px, with `expectAccessibleControls` and `expectReadableScreen` on every screen.
- `mvp-release-criteria.spec.ts`: one serial journey of the new `mvp.md` §28, one `test.step` per line, and the cross-Family step with a second real member, the family story included.
- Security tests: isolation of `listFamilyStoryYears` and of `listFamilyMemories?year=` / `undated=`; no story text or caption in logs, audit or activity for the new operations.
- Accessibility pass on SCREEN-002, SCREEN-006 and SCREEN-016.
- Check that nothing of §5 exists; `frontend/README.md` updated (it still describes `mvp-release-criteria.spec.ts` as `test.fixme`).

**Specs:** `product/mvp.md` §17, §20, §28; `product/ux/design-guidelines.md` §9; `technical/technical-specification.md` §17.

**Acceptance criteria**

- The §1 journey and the MVP north star pass in CI.
- Every new Family-scoped operation or parameter answers 404 to a member of another Family.
- No `test.fixme` in `mvp-release-criteria.spec.ts`.

**Human check:** run the §1 journey by hand on a phone, in FR and EN.

---

## 5. Not in Phase 6 (planned later)

- family questions (V1, OQ-068);
- ephemeral contributors (an idea, not decided, `mvp.md` §27);
- events and ceremonies on the year screen, periods spanning several years;
- a Memory date deduced from its photos;
- analytics events, `family_story_viewed` included (ADR-008);
- notifications, comments, likes, chat;
- a group invitation link (OQ-052), the access-code onboarding (OQ-053).

## 6. Phase 6 exit criteria

- [ ] PR-58 to PR-63 merged into `develop`, CI green.
- [ ] The §1 journey passes in Playwright, and manually on a phone at 375 px in FR and EN.
- [ ] `mvp-release-criteria.spec.ts` follows the new `mvp.md` §28, has no `fixme` and passes.
- [ ] Every family story rule has automated tests:
  - a Memory date: precisions, consistency, never in the future, set, change, removal, audit;
  - the strip: years with ACTIVE Memories only, counts, undated count, a Memory changing year or archived;
  - the order of a year and of the undated Memories;
  - a first memory: `Me`, `Someone else`, a Person added on the way, a Memory refused after its Person;
  - Family isolation of every new operation and parameter.
- [ ] The contract changes are additive (`api-breaking` green without approval label).
- [ ] No analytics event, notification or feature of §5.
- [ ] `mbia-specs/open-questions.md` has no open question blocking Phase 6.
- [ ] `develop` can be tagged `phase-6-complete`.
