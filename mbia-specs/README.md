# Mbia — Specification Pack

This repository-ready pack contains the product and technical specifications for the Mbia MVP.

## Goal

The pack has one main purpose:

1. define a commercially testable Mbia MVP precisely enough for a human team or coding agent to build it;


## Authority order

```text
Product vision and MVP rules
        ↓
Domain specifications
        ↓
UX/UI specifications
        ↓
Technical specifications
        ↓
OpenAPI contract
        ↓
Automated tests
        ↓
Implementation
```

When two documents conflict, the higher level in this list wins until the contradiction is explicitly resolved in the specs.

## Structure

```text
mbia-specs/
├── README.md
├── open-questions.md
├── delivery/
│   ├── phase-1-walking-skeleton.md
│   ├── phase-2-core-family-graph.md
│   ├── phase-3-family-memories.md
│   ├── phase-4-memory-photos.md
│   └── phase-5-collaboration.md
├── product/
│   ├── vision.md
│   ├── mvp.md
│   ├── domain/
│   │   └── person-relationships-collaboration.md
│   └── ux/
│       ├── family-tree-ux.md
│       ├── screens.md
│       ├── design-guidelines.md
│       ├── localization-and-kinship-labels.md
│       └── references/
│           └── mbia-mvp-mockups.png
└── technical/
    ├── README.md
    ├── technical-specification.md
    ├── stack.md
    ├── architecture.md
    ├── data-model.md
    ├── genealogy.md
    ├── adr/
    │   ├── README.md
    │   └── ADR-001 … ADR-008
    └── api/
        └── openapi.yaml
```

The visual mockup is a design reference. Product rules and screen behavior are defined in text and remain authoritative.

## Changelog

### 0.6.6 — 2026-09-27

- Members screen (OQ-062): the ADMIN's role label is `Family administrator` (FR "Administrateur de la famille"); on SCREEN-008, `FIRST_COUSIN` takes its neutral form, the member list carrying no gender. `screens.md` SCREEN-008.

### 0.6.5 — 2026-09-27

- Members (OQ-061): `listFamilyMembers` returns the ACTIVE members only, never their email; an unknown, other-Family or REMOVED membership answers 404 `RESOURCE_NOT_FOUND` on `updateMemberRole` and `removeFamilyMember`; role changes, removals and departures are audited `MEMBERSHIP_ROLE_CHANGED`, `MEMBERSHIP_REMOVED` and `MEMBERSHIP_LEFT`. `data-model.md` §7, §17; `openapi.yaml` `listFamilyMembers`, `updateMemberRole`, `removeFamilyMember`, `MemberResponse` (descriptions only).

### 0.6.4 — 2026-09-27

- Invitation emails (OQ-060): the email is sent right after the invitation is saved, before the response, which carries `emailDelivery` SENT or FAILED; SCREEN-009 shows `Invitation sent` or "The email could not be sent"; until the Members screen (PR-53), the failure is shown on `Invitation pending` of the Person's profile (SCREEN-005); a renewal sends the email again in the invitation's stored language. `screens.md` SCREEN-005, SCREEN-009; `data-model.md` §8; `openapi.yaml` `inviteFamilyMember`, `renewInvitation` (descriptions only).

### 0.6.3 — 2026-09-27

- "Are you already in this tree?" (SCREEN-010, `mvp.md` §18): new operation `listClaimablePersons`, the ACTIVE Persons linked to no user, each with one parent, the first in the order of the tree (OQ-015), or null; choosing a Person asks "Are you {displayName}?" before linking (`openapi.yaml`, additive; `screens.md` SCREEN-010; `data-model.md` §23.1; plan phase 5 §3.2).

### 0.6.2 — 2026-09-27

- The previous link of a renewed invitation answers 404 `INVITATION_NOT_FOUND`, like an unknown token (OQ-058); an ACTIVE member accepting an expired, revoked or used link of their Family gets 410, the invitation's state being checked first (OQ-059). `data-model.md` §8; `openapi.yaml` `previewInvitation`, `acceptInvitation` (descriptions only).

### 0.6.1 — 2026-09-27

- Renewing or revoking an ACCEPTED or REVOKED invitation answers 410 (`INVITATION_ALREADY_USED`, `INVITATION_REVOKED`); an EXPIRED invitation can be revoked (OQ-057; `data-model.md` §8; `openapi.yaml` `renewInvitation`, `revokeInvitation`, additive).

### 0.6 — 2026-09-27

Family collaboration (Phase 5), from the Phase 5 review with the human (OQ-050 to OQ-056):

- inviting from a Person's profile: the invitation carries a suggested Person, never shown before sign-in; one pending invitation per Person (`INVITATION_ALREADY_PENDING`); the invitee confirms "Are you {name}?" with one tap; the pending invitation is remembered in the browser; Persons told apart by a parent or birth year; a welcome on the first arrival (OQ-050; `mvp.md` §18, `screens.md` SCREEN-002, SCREEN-005, SCREEN-008 to SCREEN-010, `data-model.md` §8);
- only the ADMIN invites (OQ-051, unchanged);
- recent activity: the types shown, grouping of consecutive actions, links while ACTIVE, names at the time, 10 lines on Family Home, starting empty (OQ-054; `mvp.md` §20, `screens.md` SCREEN-002, `data-model.md` §16);
- `openapi.yaml` 0.4.0, additive: `CreateInvitationRequest.personId`, `InvitationPerson`, `InvitationResponse.person`, `AcceptInvitationResponse.suggestedPerson`, `MemberResponse.linkedPersonDisplayName` and `.relationshipToCurrentUser`, `ActivityResponse.count`, `.resourceIds`, `.resourceActive`, code `INVITATION_ALREADY_PENDING`;
- an invitation email that cannot be sent: the invitation exists, its `emailDelivery` says `FAILED`, and a renewal tries again (OQ-055; `mvp.md` §18, SCREEN-008, `data-model.md` §8, `InvitationResponse.emailDelivery`);
- an invitation whose Person is archived or merged stays valid, without offering or showing that Person while it is not ACTIVE; a merge never moves it (OQ-056);
- open: a group invitation link, to decide before the end of Phase 5 (OQ-052); keeping the invitee inside Mbia with an access code (OQ-053);
- delivery plan `delivery/phase-5-collaboration.md`.

### 0.5.1 — 2026-09-27

- OQ-048, still open: the stacked photo layout of SCREEN-013, rendered in PR-44, was not satisfactory. SCREEN-013 now shows a grid of thumbnails and a photo viewer (option B), provisional until the human has inspected it (`screens.md` SCREEN-013; `delivery/phase-4-memory-photos.md` §3.7, PR-45b).

### 0.5 — 2026-09-26

Photos in Memories, from the answer to OQ-042:

- one kind of Memory: a title, a text (optional when there is a photo) and up to N photos, N being an application setting (3 at launch, never above 10); a lowered limit never removes photos (`mvp.md` §17);
- each photo with an optional caption and taken date, in the order of addition, uploaded while the Memory is written or edited (`family-tree-ux.md` §13, `screens.md` SCREEN-006, SCREEN-013, SCREEN-014);
- Memory cards show the first photo; no type filter on Family Memories (SCREEN-005, SCREEN-015);
- `memory_photos` table, `MEMORY_PHOTO` media, Memory audit of photos (`data-model.md` §3, §13, §14, §14bis, §17);
- error code `MEMORY_PHOTO_LIMIT_REACHED` and the Memory transaction with its photos (`technical-specification.md` §12, §14);
- `mvp.md` §28 now reads "add a Memory with a photo and its story";
- delivery plan `delivery/phase-4-memory-photos.md`, including the additive contract changes applied in PR-40;
- OQ-048 opened: the stacked photo layout of SCREEN-013 is provisional until the human has seen it rendered.

### 0.4 — 2026-09-26

Precisions needed to deliver Phase 3 (Family Memories), from OQ-032 to OQ-041:

- Memory screens: Memories section of the profile, Memory (SCREEN-013), Edit Memory (SCREEN-014), Family Memories (SCREEN-015); a story is plain text; optional taken date of a photo (`screens.md`);
- Memory list order and paging (`openapi.yaml`, `data-model.md` §23.3–23.4);
- Memories of an archived Person and rights of a creator who became VIEWER (`mvp.md` §17, `data-model.md` §15, `person-relationships-collaboration.md` §12);
- Memory and media error codes `MEMORY_NOT_FOUND`, `MEDIA_NOT_FOUND` (`openapi.yaml`, `technical-specification.md` §12);
- audit of Memory mutations (`data-model.md` §17);
- setting, replacing and removing a Person's photo, `removeProfilePicture` (`openapi.yaml`, SCREEN-012);
- lifecycle of an uploaded photo: uploader only, single use (`MEDIA_ALREADY_USED`), unattached READY assets deleted after 24 h (`data-model.md` §13, OQ-036);
- delivery plan `delivery/phase-3-family-memories.md`: Memories are stories only in Phase 3; media in Memories wait for OQ-042.

### 0.3 — 2026-09-25

Precisions needed to deliver Phase 2 (core family graph):

- deterministic possible-duplicate rule (`person-relationships-collaboration.md` §4.1);
- relationship date-warning thresholds (§7.1) and deterministic kinship path tie-breaking (§10);
- display-name rule and search matching/ordering (`mvp.md` §6, §19);
- tree focus final tie-break (`family-tree-ux.md` §6);
- Family Home actions by role, Edit Person screen, Siblings list, remove-link confirmation, merge panel, profile History section (`screens.md`);
- genealogy module technical specification (`technical/genealogy.md`);
- ADMIN restoration flow (OQ-006): `searchPersons?status=ARCHIVED`, `listArchivedPersonRelationships`, profile "Removed links" area and archived-Person notice, search "Archived people" view (additive contract change);
- `profileMediaAssetId` ignored while media is not delivered (OQ-005);
- Phase 2 delivery plan (`delivery/phase-2-core-family-graph.md`).

### 0.2 — 2026-09-25

Closed specification gaps before implementation:

- identity provider: Keycloak, just-in-time user provisioning, verified email required (ADR-005);
- French + English from the MVP; gender-aware kinship labels and kinship direction convention (`localization-and-kinship-labels.md`, ADR-006);
- invitations by email **or shareable link**, single-use, 14-day expiry, list/renew/revoke;
- membership lifecycle: leave a Family, last-ADMIN rule, linked Person released on removal;
- Memory permissions and "at least one Person" rule aligned across documents;
- fixed three-row tree layout and gender presets when adding a parent/child;
- photo limits, EXIF stripping and derivatives (ADR-007);
- personal data rights procedure (`mvp.md` §30);
- pseudonymous analytics with PostHog EU (ADR-008);
- repository layout aligned on `mbia-specs/`.
