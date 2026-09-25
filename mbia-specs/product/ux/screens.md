# Mbia MVP — UI Screens Specification

**Version:** 0.2  
**Status:** Draft

## 1. Source of truth

Visual mockups are design references. This text is authoritative for behavior, data, permissions, actions and navigation.

If a mockup and this document conflict, this document wins until the spec is updated.

Known mockup deviations:

- the search result label "Cousin éloigné" is out of MVP scope; such a Person shows the `RELATED` label ("Membre de votre famille");
- mockup texts are in French only; every screen also exists in English (`localization-and-kinship-labels.md`).

---

# SCREEN-001 — Welcome / Onboarding

## Access

Public.

## Goal

Explain Mbia in one glance and let a new User start.

## Content

- Mbia logo;
- short value proposition;
- family-oriented visual;
- `Create my family`;
- `Sign in`.

## Primary action

```text
Create my family
→ registration if needed
→ Family creation
```

No advanced feature list or genealogy configuration on this screen.

---

# SCREEN-002 — Family Home

## Logical route

```text
/families/{familyId}
```

## Access

ACTIVE ADMIN / CONTRIBUTOR / VIEWER.

## Data

- Family name;
- Person count;
- Memory count;
- recent activity.

## Primary action

`View family tree`

## Secondary actions

ADMIN / CONTRIBUTOR only:

- `Add a relative` when the current User has a linked Person (choices: `family-tree-ux.md` §9.1; the Family carries `myLinkedPersonId`, OQ-010), otherwise `Add a person`;
- add Memory.

## Empty state

When the Family has no Persons:

```text
Welcome to the {familyName} family

Let's add the first person.

[ Start with me ]
[ Add someone else ]
```

ADMIN / CONTRIBUTOR see both actions; a VIEWER sees the explanatory text only.

`Start with me` opens SCREEN-004 in "Start with me" mode.

---

# SCREEN-003 — Family Tree

## Logical route

```text
/families/{familyId}/tree
```

## Access

ACTIVE ADMIN / CONTRIBUTOR / VIEWER.

## Behavior

Determine a focused Person and render a local graph.

Primary visible structure (layout rules: `family-tree-ux.md` §6.1):

- parents;
- focused Person;
- partners;
- children;
- siblings chip.

Empty state (Family without Persons): same as the Family Home empty state.

Loading: skeleton Person cards. When recentering, keep the current tree visible until the new one is loaded; never blank the canvas.

Tap/click Person:

```text
→ Person Quick View
```

Search is available (SCREEN-007).

ADMIN/CONTRIBUTOR may see contextual add actions.

VIEWER does not see mutation actions.

---

# SCREEN-COMPONENT-001 — Person Quick View

## Mobile

Bottom sheet.

## Desktop

Side panel/popover.

## Display

- photo;
- display name;
- birth/death years;
- relationship to current User;
- child count;
- Memory count.

## Actions

- view profile;
- center tree on Person;
- add parent/partner/child if authorized.

---

# SCREEN-004 — Add Relative

## Access

ADMIN / CONTRIBUTOR.

## Modes

- **Start with me:** creates a new Person and links it to the current User in the same operation.
- **Standalone Person** ("Add someone else", "Add a person"): creates a Person without relationship.
- **Relative of a Person:** created from a current Person and a human relationship label.

Example:

```text
Add the father of Marie
```

The relationship choice may preset the gender (`family-tree-ux.md` §9.1).

## First option (relative mode)

Search existing Family Persons (SCREEN-007).

## Second option

Create a new Person.

When a new Person is created but the relationship is then refused, the Person stays created: explain that they were added but not linked, and let the User link them from the tree or profile.

## Initial fields

```text
firstName *
lastName
profilePicture
```

## Optional fields

```text
middleNames
preferredName
gender
birthDate
birthDatePrecision
isDeceased
deathDate
deathDatePrecision
biography
```

The system creates the technical relationship automatically.

Possible duplicate: if the server reports similar Persons, show them as cards with `View existing person` (links the existing Person instead) and `Create anyway`. Never merge automatically.

Date warnings (for example parent born after child): explain the warning in human language with `Correct information` and `Add relationship anyway`.

---

# SCREEN-005 — Person Profile

## Logical route

```text
/families/{familyId}/persons/{personId}
```

## Access

Any ACTIVE Family member.

## Header

- photo;
- display name;
- birth/death information;
- relationship to current User.

## Section order

```text
Memories
Family
Removed links   (ADMIN only)
About
History
```

## Family section

- parents;
- partners;
- children;
- siblings.

For a parent, partner or child, ADMIN / CONTRIBUTOR see `Remove link` (SCREEN-COMPONENT-003). Siblings have no such action: the sibling link comes from shared parents.

## Removed links (ADMIN only)

Below the Family section, a collapsed "Removed links" area lists the removed relationships of this Person, most recent first:

- the other Person (display name, archived mark when relevant);
- the human relationship ("Marie's father");
- removal date;
- `Restore`.

When restoring is refused (for example the other Person is archived, or the link would now make someone their own ancestor), explain why in human language.

The area is hidden when there is nothing to restore.

## Archived Person

When the Person is ARCHIVED, the profile shows a clear "Archived" notice and no mutation action except, for the ADMIN, `Restore`. An archived profile is reached only from the ADMIN "Archived people" list (SCREEN-007) or from a removed link.

## About section

- middle names and preferred name when relevant;
- birth/death details;
- biography.

## History section

Presentation-safe recent important changes (`technical/data-model.md` §18), collapsed by default.

## Mutation actions

Show only when permission rules allow them:

- `Edit` (SCREEN-012);
- `This is me` when the Person can be claimed;
- unlink from the current User's own linked Person;
- ADMIN: archive / restore, merge a duplicate (SCREEN-COMPONENT-004).

---

# SCREEN-006 — Add Memory

## Access

ADMIN / CONTRIBUTOR.

## Initial choice

```text
Add a photo
Tell a story
```

## Photo flow

Fields:

```text
file *            (JPEG, PNG, WEBP; max 15 MB)
caption optional
relatedPersons[] * (at least one)
```

When launched from a Person profile, preselect that Person; otherwise preselect the User's linked Person when it exists.

## Story flow

Fields:

```text
title *
content *
relatedPersons[] * (at least one)
```

---

# SCREEN-007 — Search Person

## Access

Any ACTIVE Family member.

## Entry points

- Family Tree;
- Add Relative, "search existing";
- general navigation.

## Search fields

- firstName;
- lastName;
- preferredName.

Matching and ordering: `mvp.md` §19. The search starts after 2 characters, debounced (about 250 ms). An empty query may list the first page of Persons.

In general navigation, the ADMIN also has an "Archived people" view: the same search over archived Persons only; a result opens the archived profile (SCREEN-005). This view is never offered from the tree or Add Relative.

## Result card

- avatar/photo;
- display name;
- birth/death years when known;
- relationship to current User when known.

## Navigation

General search:

```text
result -> Person Profile
```

Tree-context search:

```text
result -> recenter tree
```

Add Relative search:

```text
result -> used as the Person of the pending relationship
```

---

# SCREEN-008 — Family Members

## Logical route

```text
/families/{familyId}/members
```

## Display

- member name;
- User-facing role label;
- membership status when relevant;
- the linked Person, when any.

ADMIN sees `Invite a relative`.

ADMIN also sees a "Pending invitations" section:

- email (or "Shared link" when no email);
- role;
- expiry date;
- actions: `Renew` (new link, and email resent for email invitations), `Revoke` (with confirmation).

ADMIN member actions (not on themselves): change role (Can contribute / Read only), remove from Family (with confirmation explaining that their contributions stay).

Non-ADMIN members see a `Leave this family` action (with confirmation). The only ADMIN does not see it.

---

# SCREEN-009 — Invite Member

## Access

ADMIN only.

## Fields

```text
channel *        Send by email | Share a link
email            required for "Send by email", optional otherwise
permission *
```

User-facing values:

```text
Can contribute -> CONTRIBUTOR
Read only -> VIEWER
```

On success, "Send by email":

```text
Invitation sent
```

On success, "Share a link":

- show the link with `Copy link` and `Share` (uses the device share sheet when available, which includes WhatsApp);
- a pre-filled message in the current language, for example: "Rejoins la famille ADJI sur Mbia : {link}";
- explain that the link works once and expires in 14 days.

The link cannot be displayed again later; the ADMIN can `Renew` it from the Members screen.

---

# SCREEN-010 — Accept Invitation

## Logical route

```text
/invitations/{token}
```

## Access

Public (preview), authenticated (acceptance).

## Display

- Family name;
- name of the inviting member;
- offered permission (Can contribute / Read only);
- `Join the family`.

## Behavior

```text
Join the family
→ if signed out: Keycloak sign-in (with sign-up option), then return here
→ accept
→ "Are you already in this tree?" (see mvp.md §18)
→ Family Home
```

States:

- already a member of this Family: go to Family Home directly;
- expired, revoked or already used: explain and suggest asking the ADMIN for a new link.

---

# SCREEN-011 — Account Settings

## Access

Any authenticated User.

## Content

- display name (editable);
- email (read-only, managed by the identity provider);
- language: Français / English;
- change password (link to Keycloak account page);
- sign out;
- `Delete my account` → explains the procedure and opens the support contact (mvp.md §30);
- links to terms of use, privacy policy and support.

---

# SCREEN-012 — Edit Person

## Access

ADMIN / CONTRIBUTOR, within the linked-Person protection rules (`domain/person-relationships-collaboration.md` §2).

## Sections

```text
Identity
Life
About
```

## Behavior

The form sends the version of the Person it loaded. On `CONCURRENT_MODIFICATION`:

```text
This person was changed since you opened the page.
[ Reload latest version ]
```

Never merge form values automatically.

---

# SCREEN-COMPONENT-002 — Siblings List

Opened from the `Siblings (n)` chip next to the focused Person (`family-tree-ux.md` §6.1).

Mobile: bottom sheet. Desktop: side panel. Selecting a sibling recenters the tree.

---

# SCREEN-COMPONENT-003 — Remove Relationship Confirmation

Message:

> Removing this link may change family relationships calculated by Mbia.

Actions: `Cancel`, `Remove link`.

---

# SCREEN-COMPONENT-004 — Merge Persons

## Access

ADMIN only.

## Display

A focused modal or panel, not a general data-merging editor. Source and target summaries side by side, with an explanation:

- the target remains;
- the source becomes merged into the target;
- relationships (and Memories) are moved and deduplicated;
- when both have a value for an identity field, the target value is kept.

## Behavior

Requires explicit confirmation. When the merge is refused, explain the conflict; never offer to force it.

---

# Global UI rules

## Responsive

Mobile-first. Desktop changes layout, not product concepts.

## One primary action

At most one visually dominant primary action per screen.

## Human language

Do not expose internal technical names when a natural family-oriented label exists.

## Progressive disclosure

Hide optional complexity until requested.

## Feedback

Mutations give immediate understandable feedback.

## Empty states

Every important empty state explains what is missing and gives a next action.

## Loading

Prefer local skeletons/loaders over blocking the entire screen.

## Errors

Never expose raw HTTP/framework/database error text.

## Permissions

Unauthorized mutation actions should normally be hidden in the UI, while backend authorization remains mandatory.
