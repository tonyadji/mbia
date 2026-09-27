# Phase 5 — Family collaboration

**Status:** Draft, for the human's review  
**Spec baseline:** `mbia-specs/` 0.6 (answers to OQ-050, OQ-051, OQ-054, OQ-055, OQ-056, see `README.md` changelog)  
**Branch base:** `develop`, after PR-46 (tag `phase-4-complete` once OQ-048 is decided)

This is a delivery plan. It does not define product behavior; the specifications do. If this plan and a spec disagree, the spec wins and the plan must be corrected.

## 1. Goal

The Family stops being one person's work. Its ADMIN invites relatives, by email or by a link shared on WhatsApp, often straight from their profile in the tree; a relative joins in a few taps, recognises themselves in the tree and starts contributing; the ADMIN manages who is in the Family; everyone sees what happened recently on Family Home.

At the end of Phase 5, the following journey works without technical intervention, in FR and EN, at phone width:

```text
sign in as the ADMIN of a Family with me, my mother Awa and my grandmother
→ from Awa's profile: Invite Awa, share a link (Can contribute)
→ as Awa, signed out, open the link: the Family and who invites, not her name
→ sign up, verify the email in the mailbox, come back to the invitation
→ join: "Are you Awa Ngo?" → Yes, it's me
→ Family Home welcomes her; the tree is centred on her
→ she adds a memory about her mother
→ as the ADMIN: Family Home shows "Awa joined the family" and her memory
→ invite a cousin by email (Read only): the email arrives, in the inviter's language
→ the cousin joins, answers No, finds himself in the list by his parent, and links himself
→ Members: Awa "your mother", the cousin "Read only"; change the cousin to Can contribute
→ the cousin leaves the family: his Person stays, no longer linked
→ the ADMIN cannot leave, and reads why
→ an expired, revoked or used link explains itself and suggests asking for a new one
```

This closes `mvp.md` §28: invite relative → relative joins → relative links themselves to an existing Person → relative contributes, and "cross-Family access must fail" with a second real member. `mvp-release-criteria.spec.ts` becomes one real serial test (PR-57).

## 2. How to run this phase

### 2.1 Rules

Phase 3 rules apply (`phase-3-family-memories.md` §2.1):

- vertical slices;
- **≤ ~700 lines of hand-written application code** per PR (generated code, lockfiles and binary test fixtures excluded; tests are counted separately and may exceed it);
- no new infrastructure or framework without an accepted ADR;
- features of later phases are forbidden even when easy (§5).

A PR does not start while an open question it depends on has no answer:

| Question | Status | Blocks |
|---|---|---|
| OQ-052 — a group invitation link | open, first PRs ship single-use links | the human decides **before PR-57** whether it enters this phase (new PRs before PR-57) or is deferred |
| OQ-053 — keeping the invitee inside Mbia | open, not in this phase unless decided with an ADR | nothing; Phase 5 keeps the Keycloak sign-up |

### 2.2 Prompt template for the agent

Same as `phase-2-core-family-graph.md` §2.2, with `phase-5-collaboration.md`.

### 2.3 Human review checklist (every PR)

Same as `phase-4-memory-photos.md` §2.3, plus:

- [ ] No raw invitation token, invitation link or email address in logs, audit, activity or error responses.
- [ ] Every new or changed screen checked at 375 px, in FR and EN.
- [ ] Permissions checked for ADMIN, CONTRIBUTOR, VIEWER and a member of another Family.

## 3. Phase-wide constraints

These apply to every PR of this phase. They restrict what is delivered now; they do not change the specs.

### 3.1 Roles and invitations

- Only the ADMIN invites (OQ-051); invitation roles are CONTRIBUTOR and VIEWER; the ADMIN role is never granted through the product (`mvp.md` §4).
- One invitation = one link = one role, single-use, 14 days (`mvp.md` §18). No group link unless OQ-052 decides it.
- An invitation may carry a suggested Person (OQ-050); accepting never links anyone by itself: the invitee confirms with `claimPerson`.
- The raw token (32 CSPRNG bytes, base64url) is returned only on creation and renewal; only its SHA-256 hash is stored (`data-model.md` §8). It is never logged, audited, put in activity or in an error response.
- The browser keeps the raw token of a pending invitation in local storage until it is accepted or no longer valid (OQ-050), and removes it then.

### 3.2 Contract (additive, applied with the specs)

The Phase 5 contract changes are **additive** and already in `openapi.yaml` 0.4.0: `api-breaking` must stay green without the `api-breaking-approved` label. The member, invitation and activity operations existed in the contract but were not implemented.

| Change | Detail |
|---|---|
| `info.version` | `0.4.0` |
| `CreateInvitationRequest.personId` | optional; rules in `inviteFamilyMember` (404, 409, 400) |
| `InvitationPerson` (new) | `{ id, displayName }` |
| `InvitationResponse.person` | `InvitationPerson` while the Person is ACTIVE, otherwise null (OQ-056) |
| `EmailDelivery` (new), `InvitationResponse.emailDelivery` | `PENDING`, `SENT`, `FAILED`; null for `LINK` (OQ-055) |
| `AcceptInvitationResponse.suggestedPerson` | `InvitationPerson` or null |
| `MemberResponse` | `linkedPersonDisplayName`, `relationshipToCurrentUser` |
| `ActivityResponse` | `count`, `resourceIds`, `resourceActive`; grouping described on `listFamilyActivities` |
| `inviteFamilyMember` | `404` documented |
| `ProblemDetails.code` | example `INVITATION_ALREADY_PENDING` |

Still to decide in its PR, additively: the parent shown next to each Person in "Are you already in this tree?" (PR-50).

### 3.3 Schema

Phase 5 migrations may create only:

| Migration | Content | Spec |
|---|---|---|
| `V010__family_invitations.sql` (PR-47) | `family_invitations` with its checks, the composite key to `persons`, the partial indexes (pending by email, one pending per Person) | `data-model.md` §8 |
| `V011__activities.sql` (PR-54) | `activities` and its index | `data-model.md` §16 |

`family_memberships` (V002) already has `status` and `removed_at`: no migration for members. Committed migrations stay immutable.

### 3.4 Module placement

- Invitations and their acceptance live in the `invitation` module; memberships stay in `family` (`FamilyMembership`). `invitation` creates or reactivates a membership through a port of `family`, in the acceptance transaction (`technical-specification.md` §14).
- Releasing the linked Person when a member leaves or is removed goes through a port implemented by `genealogy`, as `LinkedPersonsPort`.
- The `activity` module owns `activities`. Other modules record an activity in the transaction of their operation, through in-process events or a port (`architecture.md` §10); `activity` depends on no other business module's internals. Architecture tests decide; they are never weakened.

### 3.5 Email

- Sent through SMTP (`stack.md`), Mailpit locally and in tests (a Testcontainers generic container, no new test library).
- Localized templates FR and EN, rendered server-side from message bundles (`technical-specification.md` §16bis); no template engine unless an ADR accepts one.
- Sent after the transaction commits (`architecture.md` §10); `email_delivery` goes from `PENDING` to `SENT` or `FAILED`; a failure is logged without the address or the link, and a renewal tries again (OQ-055).
- An email holds the Family name, the inviter's name, the role, the link and its expiry; never the suggested Person (the preview does not show it either).

### 3.6 Onboarding

- Sign-up, sign-in and email verification stay on Keycloak (ADR-005). The access-code idea of OQ-053 is not built in this phase.
- "Are you {displayName}?", "Are you already in this tree?" and the welcome follow `mvp.md` §18 and SCREEN-002, SCREEN-010.

### 3.7 Activity

- Only the types of `data-model.md` §16, written from Phase 5 on; no backfill from `audit_entries` (OQ-054).
- Payload names at the time of the action; never a story text, caption, email, token or storage key.

### 3.8 Still out of this phase

No analytics event (`family_invitation_sent`, `family_invitation_accepted` arrive with the analytics work, ADR-008). No notification, chat or comment.

---

## 4. PR list

| PR | Title | Main user value |
|---|---|---|
| PR-47 | Invitations by link (backend) | an ADMIN can create, list, renew and revoke invitations |
| PR-48 | Join a Family (backend) | a relative can preview and accept an invitation |
| PR-49 | Invite screens | invite from a profile, share the link on WhatsApp |
| PR-50 | Join screens & onboarding | join in a few taps and recognise oneself |
| PR-51 | Invitation emails | invite by email, in the inviter's language |
| PR-52 | Members (backend) | list members, change a role, remove, leave |
| PR-53 | Members screen | manage who is in the Family |
| PR-54 | Activity recording (backend) | the Family's actions are recorded for the feed |
| PR-55 | Recent activity on Family Home | see what happened recently |
| PR-56 | Invitations and members in the Person lifecycle | merges and archives keep invitations consistent |
| PR-57 | Phase 5 hardening & MVP north star | release |

---

## PR-47 — Invitations by link (backend)

**Goal:** an ADMIN creates, lists, renews and revokes invitations; the link is returned once.

**Scope**

- `V010__family_invitations.sql` (§3.3).
- `inviteFamilyMember` with channel `LINK`, and `personId` rules (OQ-050); channel `EMAIL` answers 400 `VALIDATION_FAILED` until PR-51.
- `listFamilyInvitations` (PENDING by default; an expired PENDING one is marked `EXPIRED` when read), `renewInvitation`, `revokeInvitation` with `If-Match`.
- `inviteUrl` built from a configured base URL of the frontend (`.env.example`).
- Audit of creation, renewal and revocation, without token or link.

**Out of scope:** acceptance (PR-48), email (PR-51), UI.

**Specs:** `product/mvp.md` §18; OQ-050, OQ-051; `technical/data-model.md` §8, §17; `technical/technical-specification.md` §12, §13; `openapi.yaml` `inviteFamilyMember`, `listFamilyInvitations`, `renewInvitation`, `revokeInvitation`.

**Acceptance criteria**

- ADMIN creates CONTRIBUTOR and VIEWER invitations; CONTRIBUTOR and VIEWER → 403; another Family → 404; role ADMIN → 400.
- `personId`: ARCHIVED, MERGED or unknown → 404 `PERSON_NOT_FOUND`; linked → 409 `PERSON_ALREADY_CLAIMED`; deceased → 400; a second PENDING invitation for the same Person → 409 `INVITATION_ALREADY_PENDING`; after its expiry, a new one is accepted.
- Only the token hash is stored; the link is in the creation and renewal responses only, never in the list, logs or audit.
- Renewal gives a new link and a new expiry, and the old token stops matching; revocation is final; a stale `If-Match` → 409.

**Human check:** create a link invitation for a Person with `curl`, list it, renew it, revoke it.

---

## PR-48 — Join a Family (backend)

**Goal:** a relative previews an invitation and joins the Family.

**Scope**

- `previewInvitation` (public): Family name, inviter, role, status, expiry; never the Person. Unknown token, or the previous link of a renewed invitation → 404 `INVITATION_NOT_FOUND` (OQ-058); expired, revoked or used → 410 with its code, also for an ACTIVE member on acceptance (OQ-059).
- `acceptInvitation`: creates or reactivates the membership and marks the invitation `ACCEPTED`, atomically; `alreadyMember` for an ACTIVE member (invitation untouched); `suggestedPerson` while the Person is ACTIVE and linked to no User.
- Audit of the acceptance.

**Out of scope:** UI (PR-50), activity (PR-54).

**Specs:** `product/mvp.md` §5, §18, §21; OQ-050; `technical/data-model.md` §7, §8; `technical/technical-specification.md` §14; `openapi.yaml` `previewInvitation`, `acceptInvitation`, `claimPerson`.

**Acceptance criteria**

- Preview works signed out and names no Person; each invalid state gives its code.
- Acceptance: new User → ACTIVE membership with the invitation role; REMOVED member → reactivated with the new role; ACTIVE member → `alreadyMember`, invitation still PENDING; an email not verified → 403 `EMAIL_NOT_VERIFIED`.
- Single use under concurrency: two Users accepting the same token at once → exactly one membership (concurrency test).
- A failure after the membership write leaves neither membership nor accepted invitation (atomicity test).
- `suggestedPerson` is null once the Person is linked, archived or merged; `claimPerson` then links the new member.

**Human check:** with two test users, preview then accept a link with `curl`, and open the same link again.

---

## PR-49 — Invite screens

**Goal:** an ADMIN invites a relative from their profile and shares the link.

**Scope**

- SCREEN-005: `Invite {firstName}` for the ADMIN on a living Person linked to no User; `Invitation pending` with `Renew` when one exists.
- SCREEN-009 with channel `Share a link` (email in PR-51): `Can contribute` preselected, success screen with `Copy link`, `Share` (device share sheet), the pre-filled message naming the inviter and the Person, the 14-day explanation, `Invite someone else`.
- Refusals (`PERSON_ALREADY_CLAIMED`, `INVITATION_ALREADY_PENDING`, …) explained in human language.

**Out of scope:** SCREEN-008 (PR-53), joining (PR-50).

**Specs:** `product/ux/screens.md` SCREEN-005, SCREEN-009; `product/mvp.md` §18; OQ-050; `product/ux/design-guidelines.md` §7, §9.

**Acceptance criteria**

- The action appears only for an ADMIN, on a living Person linked to no User, and becomes `Invitation pending` after an invitation.
- The link is shown once, can be copied and shared; the message names the inviter and the Person, in FR and EN.
- Every control has an accessible name; E2E at 375 px, FR and EN.

**Human check:** invite a relative from their profile on a phone and send the link through WhatsApp.

---

## PR-50 — Join screens & onboarding

**Goal:** a relative joins in a few taps and recognises themselves in the tree.

**Scope**

- SCREEN-010: preview, `Join the family`, Keycloak sign-in or sign-up and back; the remembered invitation (§3.1), including after email verification in another tab; invalid states.
- "Are you {displayName}?" (`Yes, it's me` → `claimPerson`, `No`, `Later`), then "Are you already in this tree?" with a parent or birth year per Person (additive contract change decided here), `No` → create Person (VIEWER: explanation and `Later`).
- The welcome of SCREEN-002 on the first arrival; the tree centred on the linked Person.

**Out of scope:** the access-code flow (OQ-053).

**Specs:** `product/ux/screens.md` SCREEN-002, SCREEN-010; `product/mvp.md` §18, §21; OQ-050; `product/ux/localization-and-kinship-labels.md` §4.

**Acceptance criteria**

- Signed out, the link leads to sign-up and back to the same invitation, including when the email is verified from the mailbox in another tab; the token leaves local storage once accepted or invalid.
- With a suggested Person: one tap links the new member; the tree is then centred on them.
- Without, or after `No`: two Persons with the same name are told apart by their parent or birth year; a VIEWER cannot create a Person.
- Expired, revoked, used and already-member cases each show their message.
- E2E at 375 px, FR and EN: sign-up with a new Keycloak user, email verified through Mailpit, join and link.

**Human check:** open an invitation link on a phone, signed out, and join with a new account.

---

## PR-51 — Invitation emails

**Goal:** an ADMIN invites by email, in their language.

**Scope**

- Channel `EMAIL` of `inviteFamilyMember` and the resend on `renewInvitation` (§3.5), with `emailDelivery` (OQ-055).
- FR and EN templates; the email in the inviter's current language.
- SCREEN-009 `Send by email`, `Invitation sent`; SCREEN-008 "The email could not be sent" on a `FAILED` invitation.
- `AGENTS.md` §4 and `.env.example` for the SMTP settings.

**Specs:** `product/mvp.md` §18; OQ-055; `technical/technical-specification.md` §16bis; `product/ux/screens.md` SCREEN-009.

**Acceptance criteria**

- The email arrives in Mailpit with the Family, the inviter, the role, a working link and the expiry, in FR and in EN; it never names the suggested Person.
- A renewal sends a new email whose link works, and the old one stops working.
- The mail provider down: the invitation exists with `emailDelivery` `FAILED`, SCREEN-008 says so, and a renewal once the provider is back sends the email and sets `SENT` (tested).
- No email address or link in the logs.

**Human check:** invite yourself by email in FR and EN, and open both emails on a phone.

---

## PR-52 — Members (backend)

**Goal:** the ADMIN manages who is in the Family; a member can leave.

**Scope**

- `listFamilyMembers` with `linkedPersonDisplayName` and `relationshipToCurrentUser` (kinship computed, never stored).
- `updateMemberRole` (CONTRIBUTOR ↔ VIEWER), `removeFamilyMember` (remove, or leave on oneself) with `If-Match`; the linked Person released in the same transaction; `LAST_ADMIN_REQUIRED` with the ADMIN memberships locked.
- Audit of role changes, removals and departures.

**Specs:** `product/mvp.md` §4, §5; `product/domain/person-relationships-collaboration.md` §2; `technical/data-model.md` §7, §17; `openapi.yaml` `listFamilyMembers`, `updateMemberRole`, `removeFamilyMember`.

**Acceptance criteria**

- Every member lists the members; kinship is correct for the caller; another Family → 404.
- ADMIN changes roles and removes others; CONTRIBUTOR and VIEWER → 403; the ADMIN cannot change their own role, leave or be removed (409 `LAST_ADMIN_REQUIRED`), concurrency included.
- Leaving or being removed releases the linked Person and keeps every contribution; a removed member gets 404 on the Family; invited again, they come back with the new role.

**Human check:** with `curl`, change a role, remove a member, and let another leave.

---

## PR-53 — Members screen

**Goal:** manage who is in the Family.

**Scope**

- SCREEN-008 in the primary navigation (`family-tree-ux.md` §4): members with kinship or linked Person, role, `Invite a relative` (SCREEN-009), pending invitations with their Person, `Renew`, `Revoke`; ADMIN actions with confirmations; `Leave this family`; the sentence for the only ADMIN.
- After leaving, the User lands on their other Family or on Family creation.

**Specs:** `product/ux/screens.md` SCREEN-008, SCREEN-009; `product/ux/family-tree-ux.md` §4; `product/mvp.md` §5.

**Acceptance criteria**

- Each role sees only its actions; confirmations explain that contributions stay.
- A renewed invitation shows its new link once; a revoked one disappears.
- E2E at 375 px, FR and EN.

**Human check:** manage the members of a test Family on a phone.

---

## PR-54 — Activity recording (backend)

**Goal:** the Family's actions are recorded for the feed.

**Scope**

- `V011__activities.sql` (§3.3).
- Recording of the ten types of `data-model.md` §16 in the transaction of their operation, from `genealogy`, `memory`, `invitation` and `family` (§3.4).
- Presentation-safe payloads (§3.7).

**Specs:** `technical/data-model.md` §16; OQ-054; `technical/technical-specification.md` §14, §15; `technical/architecture.md` §8.

**Acceptance criteria**

- Each of the ten operations writes one activity with its actor, resource and names; edits, role changes and invitations write none.
- A failed operation writes no activity (atomicity test on at least one operation per module).
- No story text, caption, email, token or storage key in any payload (test over every type).
- Architecture tests pass unchanged.

**Human check:** perform the ten actions and read the `activities` rows.

---

## PR-55 — Recent activity on Family Home

**Goal:** see what happened recently.

**Scope**

- `listFamilyActivities` with the grouping and `resourceActive` of `data-model.md` §16.
- SCREEN-002: the 10 most recent lines, grouped, linked while ACTIVE, names as recorded, relative time; the section hidden when empty.

**Specs:** `product/mvp.md` §20; `product/ux/screens.md` SCREEN-002; OQ-054; `openapi.yaml` `listFamilyActivities`.

**Acceptance criteria**

- Six Persons added in a row by one member → one line "added 6 people"; a gap of more than one hour, another member or another type starts a new line.
- A line of an archived Person or Memory has no link; a merged Person's line leads nowhere.
- VIEWER sees the feed; another Family → 404; list query count does not grow with the page (fixture).
- E2E at 375 px, FR and EN.

**Human check:** use a test Family for a few minutes with two members, then read Family Home.

---

## PR-56 — Invitations and members in the Person lifecycle

**Goal:** Person archives and merges keep invitations and links consistent.

**Scope**

- A pending invitation whose Person is archived or merged stays valid and keeps its Person, which is neither offered on acceptance nor shown ("For {name}") while it is not ACTIVE; restored, it is again; a merge never moves it (OQ-056). SCREEN-005 and SCREEN-008 accordingly.
- A linked member's Person archived or merged: the rules of `data-model.md` §19 already apply; tests only.

**Specs:** OQ-050, OQ-056; `technical/data-model.md` §8, §19; `product/mvp.md` §18.

**Acceptance criteria**

- Tested: invitation for a Person then archived, restored, merged (as source and as target).
- The acceptance never returns a `suggestedPerson` that is not ACTIVE or already linked, and nothing links a User to a Person without their `claimPerson`.

**Human check:** invite a Person, archive it, then accept the invitation.

---

## PR-57 — Phase 5 hardening & MVP north star

**Goal:** the phase and the MVP journey are releasable.

**Blocked by:** OQ-052 decided (group link in this phase or deferred).

**Scope**

- `e2e/phase-5-journey.spec.ts` replaying §1 in FR and EN at 375 px, with `expectAccessibleControls` on every screen.
- `mvp-release-criteria.spec.ts`: the `fixme` steps replaced by one serial journey of `mvp.md` §28, from sign-up to the relative's contribution, and the cross-Family step with a second real member.
- Security tests: isolation of every member, invitation and activity operation across Families; token hash only; no token, link, email, story text or caption in logs, audit or activity.
- Accessibility pass on SCREEN-008, SCREEN-009, SCREEN-010 and the Family Home feed.
- Check that nothing of §5 exists.

**Specs:** `product/mvp.md` §5, §18, §20, §22, §28; `product/ux/design-guidelines.md` §9; `technical/technical-specification.md` §17.

**Acceptance criteria**

- The §1 journey and the MVP north star pass in CI.
- Every Family-scoped operation of the phase answers 404 to a member of another Family.
- No `test.fixme` left in `mvp-release-criteria.spec.ts`.

**Human check:** run the §1 journey by hand with two phones, in FR and EN.

---

## 5. Not in Phase 5 (planned later)

- a group invitation link and join requests, unless OQ-052 brings them into this phase;
- the access-code onboarding of OQ-053;
- granting or transferring the ADMIN role in the product (support only, `mvp.md` §4);
- invitations by a CONTRIBUTOR (OQ-051);
- analytics events;
- notifications, comments, likes, chat;
- a full activity history, filters or "show more";
- rebuilding past activity from the audit;
- self-service account deletion (`mvp.md` §30: support);
- terms and privacy pages, deployment.

## 6. Phase 5 exit criteria

- [ ] PR-47 to PR-57 merged into `develop`, CI green.
- [ ] The §1 journey passes in Playwright, and manually with two phones at 375 px in FR and EN.
- [ ] `mvp-release-criteria.spec.ts` has no `fixme` left and passes.
- [ ] OQ-052 decided by the human: group link delivered in this phase, or deferred.
- [ ] OQ-055 and OQ-056 implemented and tested.
- [ ] Every collaboration rule has automated tests:
  - only the ADMIN invites; roles CONTRIBUTOR and VIEWER only;
  - single use, expiry, renewal, revocation, one pending invitation per Person;
  - token stored hashed, never logged;
  - atomic acceptance, concurrency included; reactivation of a removed member;
  - no link to a Person without the member's confirmation;
  - last ADMIN protected; leaving and removal release the linked Person and keep contributions;
  - activity types, grouping, payload safety, atomicity;
  - Family isolation of every new operation.
- [ ] The contract changes are additive (`api-breaking` green without approval label).
- [ ] No analytics event, notification or feature of §5.
- [ ] `mbia-specs/open-questions.md` has no open question blocking Phase 5.
- [ ] `develop` can be tagged `phase-5-complete`.
