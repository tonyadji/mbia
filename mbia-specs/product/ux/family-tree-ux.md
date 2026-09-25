# Mbia MVP — Family Tree & UX Specification

**Version:** 0.2  
**Status:** Draft

## 1. UX principle

The complexity belongs in the genealogy engine, not in the interface.

The User should never need to understand graph concepts in order to use Mbia.

Use human language such as:

```text
Add my father
Add my mother
Add a child
Add a partner
Add a memory
Invite a relative
```

Never expose technical language such as:

```text
Create node
Create PARENT_OF
Graph edge
FamilyRelationship
```

## 2. Product feel

The MVP should feel:

- simple;
- warm;
- modern;
- family-oriented;
- accessible to non-technical Users.

It should not feel like scientific genealogy software, an admin console or a complex social network.

## 3. Mobile-first

Critical actions must work comfortably on a phone:

- view tree;
- navigate family;
- open Person;
- add Person;
- add photo;
- invite relative.

Desktop mainly provides more room for the tree and may replace bottom sheets with side panels.

## 4. Primary navigation

Within a Family:

```text
Home
Tree
Memories
Members
```

Search remains easy to access.

Avoid deep navigation hierarchies in the MVP.

## 5. Progressive disclosure

Request only the information useful at the current step.

Quick Person create initially shows:

```text
First name *
Last name
Photo
```

Optional details are behind an additional-information section.

Avoid long initial forms.

## 6. Tree model

The tree always has a `focusedPerson`.

Default priority:

1. User's linked Person;
2. last focused Person;
3. another suitable Person in the Family.

The MVP renders a local neighborhood, primarily:

```text
parents
focused Person + partners
children
```

Siblings remain accessible but must not make every view unreadable.

The last focused Person is remembered per Family on the device (browser storage). If it is no longer ACTIVE, fall back to the next rule.

"Another suitable Person" means: the ACTIVE Person with the most active relationships; ties broken by earliest creation, then by lowest UUID.

The last focused Person is known only by the browser, which sends it as the requested focus. When no focus is requested, the server applies rules 1 and 3.

### 6.1 Layout

The MVP tree uses a **fixed three-row layout**, not a generic graph-layout engine:

```text
Row 1   [Parent] ─── [Parent]            ← all parents of the focus
                  │
Row 2   [Partner] ─ [FOCUS] ─ [Partner]  ← focus + partners   (Siblings (n))
                  │
Row 3   [Child] [Child] | [Child]         ← children, grouped by other parent
```

Row 1 — parents:

- all ACTIVE parents of the focused Person, side by side;
- two parents who are partners of each other are joined by a horizontal line;
- more than two parents are displayed in the same row, without special hierarchy;
- when the User may add relationships and the focus has fewer than two parents, an empty "+ Add a parent" slot is shown.

Row 2 — focus and partners:

- the focused Person is centred and slightly larger;
- partners are placed next to the focus: the first to the right, the next to the left, alternating, ordered by relationship creation date;
- siblings are **not** drawn as cards: a "Siblings (n)" chip next to the focus opens a list (bottom sheet on mobile, panel on desktop); selecting a sibling recenters the tree on them;
- when authorized, a "+" affordance adds a partner.

Row 3 — children:

- children of the focus are grouped by their other parent: first one group per partner (same order as row 2), then one group for children whose other parent is unknown or is not a partner of the focus;
- each group is visually connected to the corresponding couple;
- within a group, children are ordered by birth date (unknown dates last), then by creation date;
- when authorized, a "+ Add a child" slot is shown.

Continuation indicators:

- a parent card whose own parents exist shows a small "↑" indicator;
- a child card whose own children exist shows a small "↓" indicator;
- tapping a card opens the Quick View; recentering uses its "Center the tree" action.

Overflow:

- when a row is wider than the screen, the canvas can be panned horizontally; the focus is centred on load;
- zoom in/out controls are available (as in the mockup); pinch-zoom on mobile;
- recentering animates smoothly (≤ 300 ms).

The API returns everything needed for this layout in one call (`GET /tree`, depth 1). Depth 2 is reserved for later desktop enhancements.

## 7. Person card

A tree card contains only:

- photo/avatar;
- display name;
- birth year when known;
- death year when applicable.

Do not show biography, full relation lists or administrative metadata in tree cards.

## 8. Person quick view

Tap/click a Person to open a lightweight detail view.

Mobile: bottom sheet.  
Desktop: side panel or equivalent.

Show:

- photo;
- name;
- birth/death years;
- relationship to current User;
- child count;
- Memory count.

Actions:

- view full profile;
- center tree on Person;
- add parent/partner/child when authorized.

## 9. Contextual relationship creation

When adding from Marie:

```text
Add parent
```

Mbia derives the technical relationship automatically.

If creating a new parent of Marie:

```text
newPerson PARENT_OF Marie
```

If creating a new child of Marie:

```text
Marie PARENT_OF newPerson
```

If creating a partner:

```text
Marie PARTNER_OF newPerson
```

First offer search for an existing Person, then creation of a new Person.

### 9.1 Gendered shortcuts

The "Add…" menu offers human choices that also preset the new Person's gender:

```text
Add a parent   -> Father (MALE) | Mother (FEMALE) | Other parent (UNKNOWN)
Add a child    -> Son (MALE)    | Daughter (FEMALE) | Child (UNKNOWN)
Add a partner  -> no preset (UNKNOWN)
```

Examples: "Ajouter le père de Marie", "Add Marie's daughter".

The preset gender is only a default: it stays visible and editable in the optional details. When linking an **existing** Person, their gender is never changed.

From the Family home, when the current User has a linked Person, "Add a relative" offers: My father, My mother, My partner, My child, Someone else. "Someone else" creates a Person without relationship.

## 10. Multiple parents and partners

The UI must not assume exactly two parents or one partner.

It may optimize common cases visually, but the domain must allow additional parent and partner relationships.

The product must not encode “current spouse” unless that fact is explicitly modeled later.

## 11. Large families

Do not attempt to display hundreds of Persons at once.

Use focused navigation, recentering and branch expansion.

The tree's job is to help exploration, not to render the entire graph simultaneously.

## 12. Person profile priority

Recommended visual order:

1. identity;
2. relationship to me;
3. Memories;
4. family relationships;
5. biography/additional information.

The profile should feel like family memory, not a database record.

## 13. Memory UX

Use the User-facing term **Souvenir** / **Memory** depending on locale.

MVP actions:

```text
Add a photo
Tell a story
```

Every Memory must be linked to at least one Person. When the flow starts from a Person, that Person is preselected; otherwise, the User's linked Person is preselected when it exists. "Publish" stays disabled until at least one Person is selected.

Photo flow:

```text
choose/take photo
→ optional caption
→ identify people in photo (at least one)
→ publish
```

While the photo is uploaded and processed, show progress; on failure, explain in human language and allow retry.

Story flow:

```text
title
→ story text
→ related Persons
→ publish
```

## 14. Error language

Never display technical errors directly.

Bad:

```text
Cycle detected in directed graph
```

Good:

```text
This link cannot be added because it would make Paul one of his own ancestors.
```

## 15. Aha moments

### First value moment

The User sees a small family tree they created themselves.

### Second value moment

The User opens a grandparent and sees photos or stories attached to them.

### Third value moment

An invited relative contributes a memory the original User did not have.

These moments should guide prioritization of UX polish.
