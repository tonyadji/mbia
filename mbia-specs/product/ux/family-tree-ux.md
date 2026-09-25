# Mbia MVP — Family Tree & UX Specification

**Version:** 0.1  
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

Photo flow:

```text
choose/take photo
→ optional caption
→ identify people in photo
→ publish
```

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
