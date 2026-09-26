# Phase 3 — Family Memories

**Status:** Ready  
**Spec baseline:** `mbia-specs/` 0.4 (answers to OQ-032 to OQ-041, see `README.md` changelog)  
**Branch base:** `develop`, after PR-28 (tag `phase-2-complete`)

This is a delivery plan. It does not define product behavior; the specifications do. If this plan and a spec disagree, the spec wins and the plan must be corrected.

## 1. Goal

Deliver the second value moment of `family-tree-ux.md` §15: a User opens a grandparent and finds the stories attached to them. Persons also get a face: a photo uploaded directly to object storage and cleaned of its metadata before anyone sees it.

In this phase, **a Memory is a title and a written text** (a STORY), linked to one or more Persons. Adding media to a Memory comes in a later iteration, once its model and journey are designed (OQ-042). Invitations, members and activity also come later.

At the end of Phase 3, the following journey works without technical intervention, in FR and EN, at phone width:

```text
sign in
→ open a Family with me, my mother and my grandmother
→ from my grandmother's profile: Add a memory
→ write a title and a story, link my grandmother and my mother
→ publish
→ the story appears on my grandmother's and my mother's profiles
→ open it and read it in full
→ find it in the Family "Memories" tab
→ edit the story
→ write a second story, then archive it
→ the Quick View shows my grandmother's Memory count
→ give my grandmother a photo from my phone (large, with GPS location)
→ see the upload progress; the photo appears on her profile and tree card, without location data
→ merge a duplicate of my grandmother that has a Memory: the Memory stays, on the kept profile
```

Roles stay ADMIN, CONTRIBUTOR and VIEWER, without invitations. Every rule is implemented and tested for the three roles, as in Phase 2.

## 2. How to run this phase

### 2.1 Rules

Phase 2 rules apply (`phase-2-core-family-graph.md` §2.1):

- vertical slices;
- **≤ ~700 lines of hand-written application code** per PR (generated code, lockfiles and binary test fixtures excluded; tests are counted separately and may exceed it);
- no new infrastructure or framework without an accepted ADR;
- features of later phases are forbidden even when easy (§5).

A PR does not start while an open question it depends on has no answer; at the time of writing, none does (OQ-042 concerns a later iteration).

### 2.2 Prompt template for the agent

Same as `phase-2-core-family-graph.md` §2.2, with `phase-3-family-memories.md`.

### 2.3 Human review checklist (every PR)

Same as `phase-2-core-family-graph.md` §2.3, plus:

- [ ] No storage key, pre-signed URL or original file name in logs, audit or error responses.
- [ ] No story text in logs, audit or analytics.
- [ ] Every new screen checked at 375 px, in FR and EN.

## 3. Phase-wide constraints

These apply to every PR of this phase. They restrict what is delivered now; they do not change the specs.

### 3.1 Memories are stories only

- The only Memory type created is `STORY` (title and text, both required).
- `createPhotoMemory` is not implemented: it answers 404 `RESOURCE_NOT_FOUND`, as unimplemented operations did in Phase 2. The contract is not changed; OQ-042 will decide it.
- SCREEN-006 opens directly on the story form: no "Add a photo / Tell a story" choice.
- No photo, caption or taken date on a Memory. SCREEN-015 has no Photos / Stories filter, and the API `type` filter is accepted but only `STORY` exists.
- `mvp-release-criteria.spec.ts` step 8 ("add photo and Story") stays `fixme`: the story part is covered by this phase, the photo part waits for OQ-042.

### 3.2 No invitations, members, activity or analytics

- No invitation, member list, role change or "leave the Family" feature.
- No `activities` table, no `listFamilyActivities`, no "recent activity" on Family Home, and no placeholder for any of them.
- No analytics event (`memory_created` arrives with the analytics work, ADR-008).
- Family Home shows the Memory count and `Add a memory` (SCREEN-002); nothing else is added there.

### 3.3 Schema

Phase 3 migrations may create only:

| Migration | Content | Spec |
|---|---|---|
| `V007__memories.sql` (PR-29) | `memories` **without** `media_asset_id`, `caption` and the taken-date columns, `type` checked to `STORY`; `memory_persons`; indexes `idx_memory_person_person` and `idx_memories_family_recent` | `data-model.md` §14, §15, §23.3, §23.4; OQ-042 |
| `V008__media_assets.sql` (PR-35) | `media_assets`, purpose checked to `PROFILE_PICTURE`; `persons.profile_media_asset_id` and `fk_person_profile_media` | `data-model.md` §10, §13 |

`profile_media_asset_id` is created with `media_assets`, as `data-model.md` §10 requires. Enumerations are checked `VARCHAR` columns, as in V001–V006; values of later iterations are added by later migrations. Not created in this phase: `family_invitations`, `activities`.

### 3.4 Module placement

- Memories live in the `memory` module. Media assets, upload and image processing live there too, since later iterations attach media to Memories (OQ-042). No new module is created.
- `memory` depends on `genealogy.application` to check related Persons. `genealogy` never depends on `memory`. When genealogy needs memory data (a merge moves `memory_persons`, a Person's photo), genealogy declares a port in its `application` package and `memory.infrastructure` implements it. This is the pattern of `family.application.LinkedPersonsPort` (Phase 2).
- The audit port (`AuditLog`, `AuditEntry`) and its adapter `JpaAuditLog` move from `genealogy` to `shared`, because `memory` writes audit entries too (PR-29). `PersonAuditValues` and the Person history stay in `genealogy`. The move changes no behaviour and no test expectation.

### 3.5 Object storage

- Only the S3 API, through AWS SDK for Java v2 (`stack.md`, ADR-004, ADR-009). No RustFS-specific API.
- The bucket stays private. Its CORS configuration allows `PUT` from the frontend origin only. It is set by `rustfs-init` locally, and documented for production in `infrastructure/`.
- Pre-signed URLs are signed for the endpoint the browser reaches, configurable separately from the backend's internal endpoint.
- Pre-signed PUT: a short validity chosen by the implementation (for example 15 minutes), returned as `expiresAt`. Pre-signed GET: 60 minutes (`data-model.md` §13). URLs are never stored.
- Storage keys are generated by the backend: `families/{familyId}/media/{mediaAssetId}/{upload|display|thumbnail}`. They are never derived from the file name, and never returned, logged or audited.
- Backend tests run against RustFS through Testcontainers, pinned to the same image as `docker-compose.yml`.

### 3.6 Image processing

- Synchronous, in `completeMediaUpload`, exactly as ADR-007. No queue, no worker.
- Libraries: TwelveMonkeys ImageIO and `metadata-extractor`, already accepted by ADR-007 and listed in `stack.md`. They are added in PR-36.
- Browser side: downscale to a long edge of 2560 px or less and re-encode as JPEG (quality ≈ 0.85) with the Canvas API, without a library. When the browser cannot decode the file, the original is uploaded as is.
- Accepted files: JPEG, PNG and WEBP (`mvp.md` §23). The file picker restricts them, and any other type is refused before upload with a human message.
- Media rules: `data-model.md` §13 (OQ-036) and OQ-040.

### 3.7 Merge and archive

- The Phase 2 restriction on merge is lifted (`phase-2-core-family-graph.md` §3.2): merge now also moves `memory_persons` links, in the same transaction (`data-model.md` §19 step 4).
- Archived Persons on Memories follow OQ-035.

### 3.8 Test fixtures

Small committed images (≤ 100 KB each), generated by a script kept in the repository:

- JPEG with EXIF GPS and orientation 6;
- PNG and WEBP;
- a text file renamed `.jpg`;
- a JPEG declared as PNG;
- an image header above 40 megapixels;
- one E2E photo larger than 2560 px, with GPS.

---

## 4. PR list

| PR | Title | Main user value |
|---|---|---|
| PR-29 | Create a Memory (backend) | stories exist |
| PR-30 | Add a Memory screen | write a story from a phone |
| PR-31 | Memories on the profile & Memory screen | read a grandparent's stories |
| PR-32 | Family Memories | find any story |
| PR-33 | Edit & archive a Memory | correct or remove a story |
| PR-34 | Memories and the Person lifecycle | merge and archive keep Memories right |
| PR-35 | Object storage & upload slot | a photo can reach storage |
| PR-36 | Upload completion & image processing | photos are safe to show |
| PR-37 | Person photo (backend) | Persons carry a photo |
| PR-38 | Person photo screens | faces in the tree |
| PR-39 | Phase 3 hardening | release |

---

## PR-29 — Create a Memory (backend)

**Goal:** a story exists, linked to one or more Persons.

**Scope**

- `V007__memories.sql` (§3.3).
- `memory` domain: `Memory` (STORY: title and content required, `data-model.md` §14), `MemoryRepository`, `CreateStoryMemoryUseCase` and `GetMemoryUseCase`.
- Related Persons are checked through `genealogy.application`: at least one, all of the Family, ACTIVE when added (OQ-035), no duplicate.
- Memory and associations are written in one transaction; the audit uses `MEMORY_CREATED` (OQ-039). The audit port moves to `shared` (§3.4).
- `FamilyStats.memoryCount` counts ACTIVE Memories.
- `createPhotoMemory` answers 404 (§3.1).

**Out of scope:** lists, update, archive, UI, media.

**Specs:** `product/mvp.md` §17; `technical/data-model.md` §14, §15; `product/domain/person-relationships-collaboration.md` §12, §13; `openapi.yaml` `createStoryMemory`, `getMemory`.

**Acceptance criteria**

- ADMIN and CONTRIBUTOR create; VIEWER → 403; another Family → 404 `FAMILY_NOT_FOUND`.
- No related Person → 400.
- A related Person that is unknown, of another Family or MERGED → 404 `PERSON_NOT_FOUND`; an ARCHIVED one → 409 `PERSON_NOT_ACTIVE` (OQ-037).
- Blank or missing title or content, or a title above 250 or content above 50,000 characters → 400.
- A failure after the Memory insert leaves neither Memory nor association (atomicity test).
- `getMemory` works for every member; an unknown or other-Family Memory → 404 `MEMORY_NOT_FOUND`.
- The audit entry holds no story text.

**Human check:** create a story with `curl`, then read it back.

---

## PR-30 — Add a Memory screen

**Goal:** write a story from a phone.

**Scope**

- SCREEN-006, story form (§3.1): title, text, related Persons.
- Person picker: reuses the SCREEN-007 search. Preselection follows `family-tree-ux.md` §13. `Publish` stays disabled until a Person is chosen.
- Entry points: Family Home `Add a memory` (ADMIN / CONTRIBUTOR, SCREEN-002). The profile entry arrives with its section in PR-31.
- After publishing, the User lands on the Memory (SCREEN-013, PR-31); until PR-31, on the first related Person's profile.
- `MemoryCard` component (`design-guidelines.md` §7).

**Out of scope:** Memory lists, edit, archive, photos.

**Specs:** `product/ux/screens.md` SCREEN-002, SCREEN-006; `product/ux/family-tree-ux.md` §13; `product/mvp.md` §17; `product/ux/design-guidelines.md` §7.

**Acceptance criteria**

- `Publish` is disabled without a Person; preselection tested from Family Home with and without a linked Person.
- Refusals (`PERSON_NOT_ACTIVE`, validation) are explained in human language; the text typed is never lost on an error.
- VIEWER sees no `Add a memory`.
- E2E: write a story at 375 px.

**Human check:** write a long story on a phone, in FR and EN.

---

## PR-31 — Memories on the profile & Memory screen

**Goal:** open a grandparent and read their stories.

**Scope**

- `listPersonMemories`, most recently added first, pages of 20 (OQ-034), with bounded queries.
- SCREEN-005 Memories section, first in the section order: story cards, "Show more", `Add a memory` (ADMIN / CONTRIBUTOR), and an empty state.
- SCREEN-013 Memory:
  - title;
  - full text as plain text with line breaks kept;
  - related Persons, archived ones marked (OQ-035);
  - author and date.

**Specs:** `product/ux/screens.md` SCREEN-005, SCREEN-013; `product/ux/family-tree-ux.md` §12, §15; `product/mvp.md` §16; `technical/data-model.md` §23.3; `openapi.yaml` `listPersonMemories`, `getMemory`.

**Acceptance criteria**

- The profile lists only ACTIVE Memories of that Person, in the OQ-034 order; another Family's Person → 404.
- A Memory shared by two Persons appears on both profiles.
- Query count independent of the number of Memories (fixture of 200 Memories).
- Every member, VIEWER included, reads Memories.
- A story's text is shown as typed: no HTML or Markdown is interpreted.

**Human check:** open the grandmother's profile, then a story, at 375 px.

---

## PR-32 — Family Memories

**Goal:** find any story of the Family.

**Scope**

- `listFamilyMemories` (OQ-034, OQ-037).
- SCREEN-015 Family Memories and the `Memories` navigation tab (`family-tree-ux.md` §4), without type filter (§3.1).
- Family Home Memory count (SCREEN-002).

**Specs:** `product/ux/family-tree-ux.md` §4; `product/ux/screens.md` SCREEN-002, SCREEN-015; `technical/data-model.md` §23.4; `openapi.yaml` `listFamilyMemories`.

**Acceptance criteria**

- Archived Memories never listed; another Family → 404 `FAMILY_NOT_FOUND`.
- 250-Memory smoke test: pages complete and ordered, bounded query count.
- Empty state with `Add a memory` for ADMIN / CONTRIBUTOR, text only for a VIEWER.

**Human check:** browse the Family Memories at 375 px.

---

## PR-33 — Edit & archive a Memory

**Goal:** correct or remove a story.

**Scope**

- `updateMemory`:
  - `If-Match`;
  - the partial-update semantics of OQ-008;
  - fields irrelevant to a story rejected;
  - related Persons changed per OQ-035;
  - audit `MEMORY_UPDATED`, one entry per changed field, without the texts (OQ-039).
- `archiveMemory`: `If-Match`, audit `MEMORY_ARCHIVED`.
- Rights: the creator or an ADMIN, with a role that can write (OQ-041).
- SCREEN-014 Edit Memory, with the stale-version message of SCREEN-012; archive confirmation.

**Specs:** `product/mvp.md` §17; `product/domain/person-relationships-collaboration.md` §12; `product/ux/screens.md` SCREEN-013, SCREEN-014; `technical/technical-specification.md` §13; `technical/architecture.md` §12; `openapi.yaml` `updateMemory`, `archiveMemory`.

**Acceptance criteria**

- A CONTRIBUTOR edits and archives their own Memory, not another member's (403 `PERMISSION_DENIED`); the ADMIN does both; a VIEWER does neither.
- Stale version → 409 `CONCURRENT_MODIFICATION`; the UI offers "Reload latest version" and never merges values.
- `caption` or `takenAt` on a story → 400; removing the last ACTIVE Person → 400.
- After archiving: the Memory is absent from every list and `getMemory` → 404 `MEMORY_NOT_FOUND`; the row still exists (support can restore it).

**Human check:** edit a story in two tabs to see the conflict; archive a story.

---

## PR-34 — Memories and the Person lifecycle

**Goal:** merging and archiving Persons keep their Memories right.

**Scope**

- `mergePerson` moves `memory_persons` links from source to target, deduplicating, in the merge transaction (`data-model.md` §19 step 4, §15). It goes through a genealogy port implemented by `memory` (§3.4).
- Archived Persons on Memories per OQ-035: kept on the Memory, shown as archived, not selectable.
- Quick View Memory count: `listPersonMemories` with `size=1` when the Quick View opens (OQ-038).

**Specs:** `technical/data-model.md` §15, §19; `product/mvp.md` §12, §13; `product/ux/screens.md` SCREEN-COMPONENT-001.

**Acceptance criteria**

- After a merge, every Memory of the source is on the target, without duplicate association. A merge refused for a cycle leaves the Memory links unchanged (atomicity).
- Archiving a Person keeps their Memories visible on the other Persons' profiles and in the Family list; restoring changes nothing to them.
- The Quick View shows the count of ACTIVE Memories, and loads nothing more for the tree itself.

**Human check:** merge a duplicate that has a story; archive then restore a Person linked to a story.

---

## PR-35 — Object storage & upload slot

**Goal:** the browser can upload a Person photo directly to private object storage.

**Scope**

- `V008__media_assets.sql` (§3.3).
- `memory` module, media part: `MediaAsset`, `MediaAssetRepository`, an `ObjectStorage` port and its S3 adapter (AWS SDK v2), and `CreateMediaUploadUseCase` implementing `createMediaUpload`.
  - It checks purpose (`PROFILE_PICTURE` only in this phase, §3.3), MIME type and size (`MEDIA_TOO_LARGE`).
  - It stores a `PENDING_UPLOAD` asset and returns a pre-signed PUT with its required headers.
- Configuration: bucket, internal endpoint, public endpoint and credentials, through `.env.example`.
- `rustfs-init` sets the bucket CORS (§3.5). ADR-009 records that pre-signed PUT works on RustFS, or is reopened if it does not.
- `AGENTS.md` §4 updated if a command or local service changes.

**Out of scope:** completion and processing (PR-36), attaching to a Person (PR-37), UI.

**Specs:** `technical/technical-specification.md` §16; `technical/data-model.md` §13; ADR-004, ADR-009; `openapi.yaml` `createMediaUpload`.

**Acceptance criteria**

- ADMIN and CONTRIBUTOR get an upload slot; VIEWER → 403; another Family → 404.
- More than 15 MB → 400 `MEDIA_TOO_LARGE`; another MIME type, or purpose `MEMORY_PHOTO` → 400.
- Testcontainers (RustFS):
  - a file PUT to the returned URL with the returned headers is stored;
  - an anonymous GET on it → 403;
  - an expired URL is refused.
- The storage key is not in the response, the logs or the errors.

**Human check:** create a slot with `curl`, upload a file, see it in the RustFS console, check it is not publicly readable.

---

## PR-36 — Upload completion & image processing

**Goal:** an uploaded photo becomes a safe, viewable image, and nothing else remains stored.

**Scope**

- `CompleteMediaUploadUseCase` implementing `completeMediaUpload`, per ADR-007 §3:
  - existence and size;
  - magic bytes against the declared type;
  - at most 40 megapixels;
  - EXIF orientation;
  - `display` and `thumbnail` JPEG derivatives without metadata;
  - original deleted;
  - `READY`, or `FAILED` + 400 `MEDIA_INVALID`.
- Only the uploader completes their asset (OQ-036). Completing a READY asset again returns it unchanged.
- Pre-signed GET URLs of both derivatives in `MediaAssetResponse`.
- Scheduled cleanup (ADR-007 §4, OQ-036), with an in-process Spring scheduler (no new infrastructure):
  - `PENDING_UPLOAD` assets older than 24 h;
  - READY assets unattached 24 h after `ready_at`.

**Specs:** ADR-007; `technical/data-model.md` §13; `product/mvp.md` §23; `openapi.yaml` `completeMediaUpload`.


**Acceptance criteria**

- JPEG, PNG and WEBP fixtures become READY. Their derivatives are JPEG, within 2048 px / 480 px, correctly oriented, and **contain no EXIF, GPS or XMP metadata** (asserted by reading them back).
- The uploaded original no longer exists after completion, whether it succeeds or fails.
- A renamed text file, a MIME mismatch, more than 40 megapixels, and a missing object → `FAILED`, 400 `MEDIA_INVALID`.
- Another member's asset → 403; another Family's → 404 `MEDIA_NOT_FOUND`.
- The cleanup deletes the objects and marks the assets `FAILED`, and never touches an attached asset.

**Human check:** upload a phone photo with location, then download the display URL and check with an EXIF viewer that no location is left.

---

## PR-37 — Person photo (backend)

**Goal:** a Person carries a photo.

**Scope**

- `createPerson` / `updatePerson` use `profileMediaAssetId` (asset READY, purpose `PROFILE_PICTURE`, uploaded by the caller, unused, OQ-036). OQ-005 ends: the value is no longer ignored.
- `removeProfilePicture: true` on `updatePerson` removes it (OQ-040). A replaced or removed asset becomes `ARCHIVED`.
- The photo follows the Person edit rules, linked-Person protection included (`person-relationships-collaboration.md` §2).
- `profilePictureUrl` is filled everywhere it exists:
  - Person responses;
  - summaries;
  - tree nodes;
  - search results;
  - `RelatedPersonReference`;
  - the URLs of all returned Persons are signed without extra queries.

**Specs:** `product/mvp.md` §16; `technical/data-model.md` §10, §13; `openapi.yaml` `createPerson`, `updatePerson`.


**Acceptance criteria**

- Only someone allowed to edit the Person changes their photo.
- Another member's asset, a non-READY or already used one → refused (`MEDIA_NOT_READY`, `MEDIA_ALREADY_USED`, OQ-036).
- Tree and search query counts unchanged with photos (250-Person fixture).
- The phase-2 test "`profileMediaAssetId` is ignored" is replaced by the new rule. This follows the spec change, not a weakening.

**Human check:** set, replace and remove a Person's photo with `curl`.

---

## PR-38 — Person photo screens

**Goal:** faces in the tree.

**Scope**

- Photo picker with browser downscale (§3.6), upload progress, completion, human error and retry (`family-tree-ux.md` §13).
- "Photo" in the quick create form (`family-tree-ux.md` §5) and in SCREEN-012: add, change, remove (OQ-040).
- `Avatar` shows the thumbnail, centre-cropped in its circle, with initials as the fallback:
  - on cards;
  - in the Quick View;
  - on the profile;
  - in search results;
  - on Memories.
- Expired pre-signed URLs: images reload with fresh URLs instead of staying broken.

**Specs:** `product/ux/family-tree-ux.md` §5, §6, §13; `product/ux/design-guidelines.md` §5, §6; `product/ux/screens.md` SCREEN-004, SCREEN-012; `product/mvp.md` §23.

**Acceptance criteria**

- A photo larger than 2560 px is sent downscaled; an unsupported file is refused before upload, in human language.
- Upload failure and `MEDIA_INVALID` are explained, with retry.
- A VIEWER, or a CONTRIBUTOR on another member's linked Person, sees no photo action.
- An image whose URL expired is shown again after reload (test with a short URL lifetime).
- E2E: give a photo to a Person at 375 px; the tree card shows it.

**Human check:** give a real phone photo to three Persons, in portrait and landscape, and see them on the tree at 375 px.

---

## PR-39 — Phase 3 hardening

**Goal:** the phase is releasable.

**Scope**

- `e2e/phase-3-journey.spec.ts` replaying §1 in FR and EN at 375 px, with `expectAccessibleControls` on every screen.
- `mvp-release-criteria.spec.ts`: step 8 comment updated (§3.1).
- Security tests across the phase:
  - cross-Family access to Memories and media (API);
  - no metadata in any served derivative;
  - no storage key in any response.
- Accessibility pass on the new screens:
  - image alternatives (the Person's name);
  - focus in SCREEN-013 and SCREEN-014;
  - readable long texts at 375 px.
- Check that nothing of §5 exists.

**Specs:** `product/mvp.md` §22, §23, §28; `product/ux/design-guidelines.md` §9; `technical/technical-specification.md` §17.

**Acceptance criteria**

- The §1 journey passes in CI.
- Every Memory or media endpoint answers 404 to a member of another Family (one parameterised API test).
- No served image contains EXIF or GPS data (checked on every fixture).

**Human check:** run the §1 journey by hand, in FR and EN, at 375 px, with a real phone photo.

---

## 5. Not in Phase 3 (planned later)

- media in Memories: photos, captions, taken dates (OQ-042);
- invitations (email and link) and joining a Family;
- member management, role changes and leaving a Family;
- activity feed;
- analytics events;
- restoring an archived Memory in the product (`mvp.md` §17: support only);
- comments, likes and notifications;
- video and audio;
- albums;
- face recognition;
- photo editing or cropping;
- timeline and structured events;
- terms and privacy pages;
- deployment.

## 6. Phase 3 exit criteria

- [ ] PR-29 to PR-39 merged into `develop`, CI green.
- [ ] The §1 journey passes in Playwright, and manually at 375 px in FR and EN with a real phone photo.
- [ ] Every Memory rule has automated tests:
  - at least one ACTIVE Person;
  - STORY invariants;
  - creator or ADMIN rights;
  - Family isolation;
  - optimistic concurrency;
  - merge moving Memories atomically.
- [ ] Every media rule has automated tests:
  - MIME, size, magic bytes and 40-megapixel checks;
  - metadata removed from every derivative;
  - original deleted;
  - single use of an asset;
  - cleanup;
  - Family isolation.
- [ ] No media in Memories, and no invitation, member, activity or analytics feature or placeholder.
- [ ] ADR-009's open point (pre-signed PUT on RustFS) is closed.
- [ ] `mbia-specs/open-questions.md` has no open question blocking Phase 3.
- [ ] `develop` can be tagged `phase-3-complete`.
