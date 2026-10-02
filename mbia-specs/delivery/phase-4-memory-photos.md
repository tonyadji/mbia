# Phase 4 — Photos in Memories

**Status:** Ready  
**Spec baseline:** `mbia-specs/` 0.5 (answer to OQ-042, see `README.md` changelog)  
**Branch base:** `develop`, after PR-39 (tag `phase-3-complete`)

This is a delivery plan. It does not define product behavior; the specifications do. If this plan and a spec disagree, the spec wins and the plan must be corrected.

## 1. Goal

A Memory gets its photos. Phase 3 delivered stories and a safe photo pipeline for Persons; Phase 4 lets a family attach a few photos to a story, as `mvp.md` §17 now describes (OQ-042): a Memory is a title, a text and up to N photos (3 at launch), each with an optional caption and taken date.

At the end of Phase 4, the following journey works without technical intervention, in FR and EN, at phone width:

```text
sign in
→ open a Family with me, my mother and my grandmother
→ from my grandmother's profile: Add a memory
→ write a title and a short story
→ add two photos from my phone, one large with GPS location
→ see each upload progress; give the first a caption and the second a year
→ link my mother; publish
→ the Memory shows its title, both photos with their caption and year, then the story
→ its card shows the first photo on both profiles and in the Family "Memories" tab
→ edit it: add a third photo; "Add a photo" is now disabled; remove the second photo
→ write a Memory with a title and a photo only, without text
→ merge a duplicate of my grandmother that has a Memory with photos: the photos stay
→ no served photo contains location data
```

This closes the story-and-photo step of `mvp.md` §28 ("add a Memory with a photo and its story").

## 2. How to run this phase

### 2.1 Rules

Phase 3 rules apply (`phase-3-family-memories.md` §2.1):

- vertical slices;
- **≤ ~700 lines of hand-written application code** per PR (generated code, lockfiles and binary test fixtures excluded; tests are counted separately and may exceed it);
- no new infrastructure or framework without an accepted ADR;
- features of later phases are forbidden even when easy (§5).

No open question blocks this phase. OQ-048 (layout of SCREEN-013) is provisional by design: PR-44 ships the chosen layout and its human check confirms or reopens it.

### 2.2 Prompt template for the agent

Same as `phase-2-core-family-graph.md` §2.2, with `phase-4-memory-photos.md`.

### 2.3 Human review checklist (every PR)

Same as `phase-3-family-memories.md` §2.3:

- [ ] No storage key, pre-signed URL or original file name in logs, audit or error responses.
- [ ] No story text or caption in logs, audit or analytics.
- [ ] Every new or changed screen checked at 375 px, in FR and EN.

## 3. Phase-wide constraints

These apply to every PR of this phase. They restrict what is delivered now; they do not change the specs.

### 3.1 One kind of Memory (OQ-042)

- Every Memory is created by `createStoryMemory` with type `STORY`; it carries 0 to N photos.
- Title always required; text required only when the Memory has no photo (`mvp.md` §17, `data-model.md` §14).
- Photos keep the order of addition; no reordering, no replacement in place (remove, then add).
- A photo is attached by the member who uploaded it, READY, of purpose `MEMORY_PHOTO`, used nowhere else (OQ-036). An ADMIN editing another member's Memory adds their own uploads.
- A removed photo's asset becomes `ARCHIVED` (`data-model.md` §13, §14bis). Archiving a Memory leaves its photos attached.
- Merge and Person archival do not touch photos: they belong to the Memory.

### 3.2 Contract (additive, applied in PR-40)

The Phase 4 contract changes are **additive**: `api-breaking` must stay green without the `api-breaking-approved` label. They are applied to `openapi.yaml` in PR-40, contract-first, because new required response fields change the generated models and need the code in the same change.

| Change | Detail |
|---|---|
| `info.version` | `0.3.0` |
| `FamilyLimits` (new) | `{ maxPhotosPerMemory: integer, 1–10 }`, required |
| `FamilyResponse.limits` | `FamilyLimits`, required |
| `MemoryPhotoInput` (new) | `mediaAssetId` (uuid, required), `caption` (`[string, null]`, ≤ 5000), `takenAt` (`PartialDate` or null) |
| `MemoryPhotoResponse` (new) | `mediaAssetId`, `caption`, `takenAt`, `url` (display, pre-signed 60 min), `thumbnailUrl`, `widthPx`, `heightPx`; required: `mediaAssetId`, `url`, `thumbnailUrl` |
| `CreateStoryMemoryRequest` | `content` no longer required, type `[string, null]` (≤ 50,000, not blank when present); new optional `photos: MemoryPhotoInput[]`, `maxItems: 10` |
| `UpdateMemoryRequest` | new optional `photos: MemoryPhotoInput[]` (`maxItems: 10`): the complete new list. Absent means unchanged (OQ-008). Photos already on the Memory keep their position whatever their order in the list; new ones are added after them in list order; missing ones are removed. `content: ""` (blank) empties the text, allowed only if a photo remains; `content: null` keeps it (OQ-008, OQ-049) |
| `MemoryResponse.photos` | `MemoryPhotoResponse[]`, required, in position order, empty for a Memory without photo |
| `createStoryMemory` responses | document `404` (Person, media) and `409` (`PERSON_NOT_ACTIVE`, `MEDIA_NOT_READY`, `MEDIA_ALREADY_USED`, `MEMORY_PHOTO_LIMIT_REACHED`) |
| `ProblemDetails.code` | example `MEMORY_PHOTO_LIMIT_REACHED` |
| `createMediaUpload` | purpose `MEMORY_PHOTO` accepted (the enum already has it) |
| Deprecated, kept | `createPhotoMemory` (still 404 `RESOURCE_NOT_FOUND`), `CreatePhotoMemoryRequest`, `MemoryType.PHOTO` (never returned), `MemoryResponse.media`, `.caption`, `.takenAt` (always null), `UpdateMemoryRequest.caption`, `.takenAt` (still 400), the `type` parameter of `listFamilyMemories` (`STORY` lists all, `PHOTO` none) |

Nothing is removed. Cleaning the deprecated items would be a breaking change and is not planned (§5).

### 3.3 Schema

Phase 4 migrations may create only:

| Migration | Content | Spec |
|---|---|---|
| `V009__memory_photos.sql` (PR-40) | `memory_photos` with its keys and checks; `media_assets` purpose check extended to `MEMORY_PHOTO`; the STORY check of `memories` relaxed to the title only | `data-model.md` §13, §14, §14bis |

V007 and V008 are immutable: their unnamed checks are dropped and replaced by named ones in V009.

### 3.4 Module placement

- Memory photos live in the `memory` module, next to media assets and Memories. No new module.
- The limit is one application setting, `mbia.memory.max-photos` (§3.5). `memory` enforces it; `family.api` exposes it in `FamilyResponse.limits`. `family` must not depend on `memory`: bind the setting where both may read it (for example a configuration record in `shared`), or declare a port in `family.application` implemented by `memory.infrastructure`, as `LinkedPersonsPort`. Architecture tests decide; they are never weakened.

### 3.5 The photo limit

- `mbia.memory.max-photos`, environment variable `MBIA_MEMORY_MAX_PHOTOS`, default `3`, documented in `.env.example`. The application refuses to start outside 1–10.
- Enforced by the backend on every addition (create or update), in the Memory transaction: more photos than the limit **after an addition** → 409 `MEMORY_PHOTO_LIMIT_REACHED`. An update that adds nothing is accepted even when the Memory is above a lowered limit (`mvp.md` §17).
- The frontend never hard-codes the value: it reads `FamilyResponse.limits.maxPhotosPerMemory`.

### 3.6 Reuse of Phase 3 media

- Upload slot, direct upload, completion, processing, derivatives and cleanup are those of PR-35 and PR-36, unchanged; only the purpose `MEMORY_PHOTO` is new. A photo chosen but never published is cleaned after 24 h (OQ-036).
- Browser downscale and the upload hook of PR-38 are reused; the hook takes the purpose instead of hard-coding `PROFILE_PICTURE`.
- Image fixtures of `phase-3-family-memories.md` §3.8 are reused; no new fixture is needed.

### 3.7 SCREEN-013 layout (OQ-048)

PR-44 ships the provisional layout: `display` versions stacked at full width, each with its caption and taken date. The human confirms it at PR-44's human check, or reopens OQ-048; a different layout then becomes its own PR in this phase, before PR-46.

At PR-44's human check (2026-09-27), the stacked layout was **not satisfactory**. The human chose to try option B of OQ-048, a grid of thumbnails with a photo viewer: it is PR-45b. OQ-048 stays open until the human has inspected B.

### 3.8 Still out of this phase

No invitation, member, role change, activity or analytics work (as `phase-3-family-memories.md` §3.2). No `activities` table. No analytics event.

---

## 4. PR list

| PR | Title | Main user value |
|---|---|---|
| PR-40 | Memory photos: contract, schema & upload | a photo can be sent for a Memory |
| PR-41 | Publish a Memory with photos (backend) | stories carry photos |
| PR-42 | Edit a Memory's photos (backend) | photos can be added, described and removed |
| PR-43 | Photos in Add Memory | add photos from a phone while writing |
| PR-44 | Photos on the Memory and its cards | see the photos of a story |
| PR-45 | Photos in Edit Memory | correct the photos of a story |
| PR-45b | Photo grid and viewer on SCREEN-013 (OQ-048) | browse the photos of a story |
| PR-46 | Phase 4 hardening | release |

---

## PR-40 — Memory photos: contract, schema & upload

**Goal:** the browser can upload a photo meant for a Memory, and the API tells how many a Memory can hold.

**Scope**

- `openapi.yaml`: every change of §3.2, contract-first. Generated code regenerated; nothing hand-edited.
- `V009__memory_photos.sql` (§3.3).
- `createMediaUpload` accepts `MEMORY_PHOTO`; completion and cleanup treat it as `PROFILE_PICTURE`.
- The limit setting (§3.5) and `FamilyResponse.limits`.
- `MemoryResponse.photos` returned as an empty list everywhere a Memory is returned; `createStoryMemory` and `updateMemory` refuse `photos` with 400 `VALIDATION_FAILED` until PR-41 and PR-42.
- `AGENTS.md` §4 updated if a command or setting changes.

**Out of scope:** attaching photos (PR-41, PR-42), UI.

**Specs:** OQ-042; `technical/data-model.md` §13, §14, §14bis; `technical/technical-specification.md` §12; `openapi.yaml`.

**Acceptance criteria**

- `api-breaking` passes without the approval label; `api-lint` passes.
- ADMIN and CONTRIBUTOR get a `MEMORY_PHOTO` slot; VIEWER → 403; another Family → 404. A `MEMORY_PHOTO` completes to READY without metadata, as a `PROFILE_PICTURE`.
- The cleanup deletes an unattached READY `MEMORY_PHOTO` after 24 h.
- `getFamily` returns `limits.maxPhotosPerMemory` = the setting (test with a non-default value); a setting outside 1–10 fails the start.
- Schema tests: `memory_photos` refuses an asset of another Family, the same asset twice, and two photos at the same position; a Memory without `content` is accepted by the database.
- Every existing Memory test still passes; `photos` is `[]`.

**Human check:** `curl` a `MEMORY_PHOTO` slot, upload and complete it; read `limits` on the Family.

---

## PR-41 — Publish a Memory with photos (backend)

**Goal:** a story is published with its photos, and read back with them.

**Scope**

- `createStoryMemory` with `photos` (§3.1): each asset READY, `MEMORY_PHOTO`, of the caller, unused, no duplicate in the request; limit (§3.5); text optional when a photo is present. Memory, Persons, photos and audit in one transaction (`technical-specification.md` §14).
- Caption and taken date per photo (partial date of OQ-033, `data-model.md` §9).
- `MemoryResponse.photos` filled in `getMemory`, `listPersonMemories` and `listFamilyMemories`, signed in one batch per response (`data-model.md` §14bis).
- Audit `MEMORY_CREATED` with the photo asset ids, never a caption (`data-model.md` §17).

**Out of scope:** changing photos (PR-42), UI.

**Specs:** `product/mvp.md` §17; `technical/data-model.md` §13, §14, §14bis, §17; `technical/technical-specification.md` §12, §14; `openapi.yaml` `createStoryMemory`, `getMemory`, `listPersonMemories`, `listFamilyMemories`.

**Acceptance criteria**

- A Memory with 0, 1 and N photos is created; N + 1 → 409 `MEMORY_PHOTO_LIMIT_REACHED`, nothing stored.
- Without text: accepted with a photo, 400 without any.
- Another member's asset → 403; a non-READY one → 409 `MEDIA_NOT_READY`; one already used (by a Memory or a Person) → 409 `MEDIA_ALREADY_USED`; a `PROFILE_PICTURE` → 400; another Family's → 404 `MEDIA_NOT_FOUND`; the same asset twice → 400.
- A failure after the Memory insert leaves no Memory, Person link or photo row, and the assets stay unattached (atomicity test).
- Photos are returned in order with working pre-signed URLs; list query counts do not grow with the number of photos (fixture of 200 Memories with photos).
- The audit and the logs hold neither caption, story text, storage key nor URL.

**Human check:** publish a Memory with two photos with `curl`, open both URLs.

---

## PR-42 — Edit a Memory's photos (backend)

**Goal:** photos of a Memory can be added, described and removed.

**Scope**

- `updateMemory` with `photos` (§3.2 semantics): add, remove, change caption and taken date, in one transaction with `If-Match`. Removed assets become `ARCHIVED`.
- The limit on additions only (§3.5).
- The text may be emptied only while a photo remains, and the last photo removed only while a text remains.
- Audit `MEMORY_UPDATED`: `photos` with the asset ids before and after; `photoDetails` with the asset id only (`data-model.md` §17).
- Rights unchanged: the creator or an ADMIN, with a role that can write (OQ-041).
- Tests that merge and Person archival leave photos in place, and that an archived Memory serves no photo URL.

**Out of scope:** UI.

**Specs:** `product/mvp.md` §17; `technical/data-model.md` §13, §14bis, §17, §19; `openapi.yaml` `updateMemory`, `archiveMemory`.

**Acceptance criteria**

- Adding up to N and removing photos work; an addition beyond N → 409 `MEMORY_PHOTO_LIMIT_REACHED`, the Memory unchanged.
- With the limit lowered below a Memory's photo count: changing its title or a caption is accepted, removing a photo is accepted, adding one is refused.
- Positions: kept photos keep theirs whatever the request order; new ones follow; no renumbering after a removal.
- Emptying the text of a Memory without photo → 400; removing the last photo of a Memory without text → 400.
- A removed photo's asset is `ARCHIVED` and can be attached nowhere; stale `If-Match` → 409 `CONCURRENT_MODIFICATION`, nothing changed.
- A CONTRIBUTOR edits the photos of their own Memory only; VIEWER never.
- After a merge, the target's Memories keep their photos, without duplicate.

**Human check:** with `curl`, add a third photo, then remove one; restart with `MBIA_MEMORY_MAX_PHOTOS=2` and check that the Memory keeps its photos but refuses a new one.

---

## PR-43 — Photos in Add Memory

**Goal:** add photos from a phone while writing a story.

**Scope**

- SCREEN-006 photos section (`screens.md` SCREEN-006, `family-tree-ux.md` §13):
  - `Add a photo` with multiple selection limited to the free places, and the message when photos are left out or the limit is reached;
  - browser downscale, upload progress, error, `Try again`, `Remove` per photo (reusing PR-38, §3.6);
  - thumbnail, caption and `More information` (taken date, OQ-033) per photo;
  - `Publish` disabled while a photo is being sent or failed;
  - text optional once a photo is ready.
- The limit comes from `FamilyResponse.limits` (§3.5).
- Refusals (`MEMORY_PHOTO_LIMIT_REACHED`, `MEDIA_*`) explained in human language; nothing typed is lost.

**Out of scope:** photos on SCREEN-013 and cards (PR-44), edit (PR-45).

**Specs:** `product/ux/screens.md` SCREEN-006; `product/ux/family-tree-ux.md` §13; `product/mvp.md` §17, §23; `product/ux/design-guidelines.md` §7, §9.

**Acceptance criteria**

- With a limit of 3: choosing 5 files keeps the first 3 and says so; `Add a photo` is then disabled with its explanation (tested with another limit value too).
- A photo larger than 2560 px is sent reduced; an unsupported file is refused before upload.
- A failed upload can be retried or removed; `Publish` waits for every photo.
- A Memory with a title and a photo, without text, is published.
- Every new control has an accessible name; the caption field is labelled with its photo's position.
- E2E: add two photos while writing a story at 375 px, FR and EN.

**Human check:** write a Memory on a phone with three real photos (portrait and landscape), a caption and a year.

---

## PR-44 — Photos on the Memory and its cards

**Goal:** see the photos of a story.

**Scope**

- SCREEN-013: photos after the title, stacked at full width in position order, each with its caption and taken date; alternative text: the caption, otherwise "Photo {n} of {count}" (`screens.md` SCREEN-013). Provisional layout (§3.7, OQ-048).
- `MemoryCard`: the thumbnail of the first photo when there is one (SCREEN-005, SCREEN-015); a Memory without text shows its title only.
- SCREEN-015 keeps no filter (OQ-042).
- Expired photo URLs reload with fresh ones, as avatars do (PR-38).

**Out of scope:** edit (PR-45); a photo viewer (§5, unless OQ-048 decides it).

**Specs:** `product/ux/screens.md` SCREEN-005, SCREEN-013, SCREEN-015; `product/ux/design-guidelines.md` §5, §6, §9; `product/mvp.md` §23.

**Acceptance criteria**

- Photos appear in order, with caption and taken date when present; images load lazily and never make the page scroll sideways at 375 px, in portrait and landscape.
- Alternative texts follow SCREEN-013; the card thumbnail is decorative (the title names the card).
- An expired URL is shown again after a reload of the Memory.
- VIEWER sees the photos, without any action.

**Human check:** open a Memory with three real photos at 375 px in FR and EN, and **decide OQ-048**: confirm the stacked layout, or describe the change wanted.

---

## PR-45 — Photos in Edit Memory

**Goal:** correct the photos of a story.

**Scope**

- SCREEN-014 photos section: the Memory's photos with caption and `More information`, `Remove`, and `Add a photo` within the limit, reusing PR-43 components.
- Above a lowered limit, `Add a photo` stays disabled with its explanation (`screens.md` SCREEN-014).
- Stale version: the SCREEN-012 behavior (explain, `Reload latest version`, never merge); photos uploaded but not saved are simply dropped (the cleanup removes them, OQ-036).

**Specs:** `product/ux/screens.md` SCREEN-014; `product/mvp.md` §17; `openapi.yaml` `updateMemory`.

**Acceptance criteria**

- Add, remove and describe photos, then save: SCREEN-013 shows the result in order.
- The text can be emptied only while a photo remains, and the last photo removed only while a text remains; both explained.
- A CONTRIBUTOR on another member's Memory and a VIEWER never reach the form (as PR-33).
- E2E: add a third photo, see `Add a photo` disabled, remove one, save, at 375 px.

**Human check:** edit a Memory's photos on a phone in two tabs to see the conflict.

---

## PR-45b — Photo grid and viewer on SCREEN-013 (OQ-048)

**Goal:** browse the photos of a story, as the human asked after seeing the stacked layout (§3.7).

**Scope**

- SCREEN-013: the photos as a grid of square thumbnails without text (2 columns on a phone, 3 from tablet width), each a button named by the alternative text of SCREEN-013.
- The photo viewer of SCREEN-013: the display version large, caption, taken date, "Photo {n} of {count}", `Previous photo`, `Next photo`, `Close`, swipe, arrow keys and Escape; focus kept inside and returned to the thumbnail.
- Expired URLs reload with fresh ones, in the grid and in the viewer (PR-44).
- OQ-048 status, `screens.md` SCREEN-013 and this plan updated.

**Out of scope:** zoom, download, share, reordering; any change to Memory cards, SCREEN-006 or SCREEN-014; the API.

**Specs:** `product/ux/screens.md` SCREEN-013; OQ-048; `product/ux/design-guidelines.md` §7, §9; `product/mvp.md` §23.

**Acceptance criteria**

- The thumbnails are in position order and named by caption, otherwise "Photo {n} of {count}"; nothing scrolls sideways at 375 px, in portrait and landscape.
- The viewer opens on the tapped photo, shows its caption and taken date, moves with the buttons, a swipe and the arrow keys, never past the first or the last photo, and closes with `Close` or Escape, focus back on its thumbnail.
- Every control has an accessible name and a 44 px target; a VIEWER browses the photos, without any other action.
- An expired URL is shown again after a reload of the Memory.

**Human check:** open a Memory with three real photos (portrait and landscape, a long caption) at 375 px in FR and EN, browse them in the viewer, and **decide OQ-048**: confirm B, or describe the change wanted.

---

## PR-46 — Phase 4 hardening

**Goal:** the phase is releasable.

**Scope**

- `e2e/phase-4-journey.spec.ts` replaying §1 in FR and EN at 375 px, with `expectAccessibleControls` on every screen and the served photos checked for EXIF/XMP.
- `mvp-release-criteria.spec.ts`: step 8 comment updated (Memory with photo and story covered by `phase-4-journey.spec.ts`).
- Security tests across the phase:
  - `MemoryAndMediaFamilyIsolationApiTest` extended to photos: another Family's `MEMORY_PHOTO` in create and update → 404, no change;
  - `ServedMediaApiTest` extended: every photo URL of a Memory response is a metadata-free JPEG; no storage key, bucket, original file name or caption in logs.
- Accessibility pass on SCREEN-006, SCREEN-013 and SCREEN-014 photos: names, alternatives, focus after adding and removing a photo, long captions at 375 px.
- Check that nothing of §5 exists.

**Specs:** `product/mvp.md` §17, §22, §23, §28; `product/ux/design-guidelines.md` §9; `technical/technical-specification.md` §17.

**Acceptance criteria**

- The §1 journey passes in CI.
- Every Memory and media endpoint still answers 404 to a member of another Family, photos included.
- No served Memory photo contains EXIF or GPS data (every fixture).

**Human check:** run the §1 journey by hand, in FR and EN, at 375 px, with real phone photos.

---

## 5. Not in Phase 4 (planned later)

- invitations (email and link) and joining a Family;
- member management, role changes and leaving a Family;
- activity feed;
- analytics events;
- reordering photos, replacing a photo in place, cropping or editing a photo;
- a photo viewer or gallery beyond the viewer of a Memory tried for OQ-048 (PR-45b);
- albums or more photos per Memory than the setting allows (maximum 10);
- using a Memory photo as a Person's photo, or the reverse (OQ-040);
- removing the deprecated contract items (`createPhotoMemory`, `PHOTO`, Memory-level `media`, `caption`, `takenAt`, the `type` filter);
- restoring an archived Memory in the product (`mvp.md` §17: support only);
- comments, likes and notifications;
- video and audio;
- face recognition;
- timeline and structured events;
- terms and privacy pages;
- deployment.

## 6. Phase 4 exit criteria

- [ ] PR-40 to PR-46 (with PR-45b) merged into `develop`, CI green.
- [ ] The §1 journey passes in Playwright, and manually at 375 px in FR and EN with real phone photos.
- [ ] OQ-048 decided by the human (layout confirmed, or changed in its own PR).
- [ ] Every Memory photo rule has automated tests:
  - at most N photos on addition, and a lowered limit never removes photos;
  - text optional only with a photo;
  - READY, `MEMORY_PHOTO`, own upload, single use;
  - order of addition kept;
  - removed photo archived;
  - atomicity of create and update;
  - Family isolation;
  - metadata removed from every served photo.
- [ ] The contract changes are additive (`api-breaking` green without approval label).
- [ ] No invitation, member, activity or analytics feature or placeholder.
- [ ] `mbia-specs/open-questions.md` has no open question blocking Phase 4.
- [ ] `develop` can be tagged `phase-4-complete`.
