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

`View my tree`

## Secondary actions

- add relative;
- add Memory.

## Empty state

When the Family has no Persons:

```text
Welcome to the {familyName} family

Let's add the first person.

[ Start with me ]
[ Add someone else ]
```

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

Tap/click Person:

```text
→ Person Quick View
```

Search is available.

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

## Context

Created from a current Person and a human relationship label, or with no relationship ("Someone else").

Example:

```text
Add the father of Marie
```

The relationship choice may preset the gender (`family-tree-ux.md` §9.1).

## First option

Search existing Family Persons.

## Second option

Create a new Person.

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

Possible duplicate: if the server reports similar Persons, show them with `View existing person` (links the existing Person instead) and `Create anyway`.

Date warnings (for example parent born after child): show the warning in human language with `Confirm` and `Cancel`.

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
About
```

## Family section

- parents;
- partners;
- children;
- siblings.

## Mutation actions

Show only when permission rules allow them.

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

## Search fields

- firstName;
- lastName;
- preferredName.

## Result card

- avatar/photo;
- display name;
- birth year when known;
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
