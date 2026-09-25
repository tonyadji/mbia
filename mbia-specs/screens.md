# Mbia MVP — UI Screens Specification

**Version:** 0.1  
**Status:** Draft

## 1. Source of truth

Visual mockups are design references. This text is authoritative for behavior, data, permissions, actions and navigation.

If a mockup and this document conflict, this document wins until the spec is updated.

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

Primary visible structure:

- parents;
- focused Person;
- partners;
- children.

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

Always created from a current Person and a human relationship label.

Example:

```text
Add a parent of Marie
```

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
file *
caption optional
relatedPersons[]
```

When launched from a Person profile, preselect that Person.

## Story flow

Fields:

```text
title *
content *
relatedPersons[]
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
- membership status when relevant.

ADMIN sees `Invite a relative`.

---

# SCREEN-009 — Invite Member

## Access

ADMIN only.

## Fields

```text
email *
permission *
```

User-facing values:

```text
Can contribute -> CONTRIBUTOR
Read only -> VIEWER
```

On success:

```text
Invitation sent
```

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
