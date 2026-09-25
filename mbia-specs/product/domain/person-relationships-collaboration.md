# Mbia MVP — Person, Relationships & Collaboration

**Version:** 0.3  
**Status:** Draft

## 1. Collaboration principle

> Open collaboration, traceable changes, controlled destructive actions.

The MVP does not use a heavy approval workflow. Instead it relies on:

- permissions;
- audit/history;
- soft lifecycle;
- restoration;
- duplicate merge.

## 2. Person edit rules

### Non-linked Person

May be edited by ADMIN or CONTRIBUTOR.

### Linked Person

Identity fields may be edited by:

- the linked User;
- an ADMIN.

Another CONTRIBUTOR may still:

- add relationships involving the Person;
- add photos;
- add Stories;
- associate existing Memories.

This gives a linked User control of their identity without preventing the family from contributing to their shared history.

### Link release

A Person's link to a User is released (Person becomes non-linked) when:

- the linked User unclaims it;
- an ADMIN unclaims it;
- the linked User leaves the Family or is removed from it;
- the linked User's account is deleted.

The release is audited. The Person and its data remain unchanged.

## 3. Change history

Important Person changes must preserve at least:

```text
resource
actor
timestamp
old value
new value
```

User-visible activity may show a simplified version; technical audit retains more detail.

## 4. Duplicate workflow

Duplicate detection is advisory.

No automatic merge.

### 4.1 Possible duplicate candidate

Detection is deterministic. An ACTIVE Person of the same Family is a **possible duplicate candidate** of the Person being created when:

1. `firstName` matches after trim, whitespace collapse, case folding and accent-insensitive comparison; and
2. at least one of `lastName` or `preferredName` matches the same way, when present on both Persons; and
3. if both Persons have a known birth year, the years are equal.

A missing optional value never counts as a match by itself.

When at least one candidate exists and the User has not confirmed, creation is refused with `POSSIBLE_DUPLICATE` and the candidates. The User may view an existing candidate or create anyway.

### 4.2 Merge

ADMIN may merge two Persons.

Merge rules:

- choose explicit target Person;
- target remains ACTIVE;
- duplicate becomes MERGED;
- duplicate stores `mergedIntoPersonId`;
- transfer compatible relations and Memories;
- deduplicate equivalent relationships;
- block unresolved linked-User conflicts;
- never create self-relations;
- perform operation atomically.

## 5. Archiving

ADMIN may archive/restore a Person.

A linked Person cannot be archived until its linked User association is resolved.

An archived Person:

- is hidden from normal tree/search views;
- cannot receive new relationships;
- remains restorable;
- retains historical data.

Only the ADMIN can list archived Persons and open their profile to restore them.

## 6. Relationship structure

Persist:

```text
PARENT_OF
PARTNER_OF
```

Relationship core fields:

```text
id
familyId
type
sourcePersonId
targetPersonId
status
createdAt
createdBy
updatedAt
updatedBy
version
```

### PARENT_OF

Directional.

```text
A PARENT_OF B
=> B is child of A
```

### PARTNER_OF

Symmetric and stored only once.

It does not imply:

- legal marriage;
- traditional marriage;
- current relationship;
- divorce state;
- exclusivity.

## 7. Relationship validity

Block:

- self-relation;
- exact duplicate;
- cross-Family relation;
- relation involving an ARCHIVED or MERGED Person (`PERSON_NOT_ACTIVE`, OQ-011);
- parental cycle.

Warn, but permit confirmation, for probable inconsistencies such as suspicious dates or generation gaps.

### 7.1 Date warnings

For a `PARENT_OF` relation, when both birth years are known:

- `PARENT_BORN_AFTER_CHILD` when `parentBirthYear >= childBirthYear`;
- otherwise, `IMPLAUSIBLE_PARENT_AGE` when the parent's age at the child's birth is `< 12` or `> 80`.

The age is `childBirthYear - parentBirthYear`, also when exact dates are known. A parent born the same year as the child or after gets only `PARENT_BORN_AFTER_CHILD` (OQ-012).

`IMPLAUSIBLE_GENERATION_GAP` is reserved: no threshold is defined yet, so it is never emitted.

When warnings exist and the User has not confirmed, the relation is not created and the warnings are returned. The User may retry with explicit confirmation.

## 8. Relationship removal

ADMIN and CONTRIBUTOR may remove a relationship.

Removal means `ARCHIVED`, not physical delete.

Before removal, UI warns that derived kinship may change.

ADMIN may restore if the restored graph remains valid. The ADMIN finds removed relationships from the profile of either Person involved.

## 9. Derived relations

The kinship engine must identify at least:

```text
parent / child
grandparent / grandchild
sibling
uncle / aunt
nephew / niece
first cousin
```

Two Persons are siblings when they share at least one parent.

The graph may internally know whether siblings share one or two parents, even if the MVP does not always expose half-sibling terminology.

## 10. Relationship path

When a current User is linked to a Person, Mbia should be able to return both a label and an explainable path.

Example:

```text
Paul is your grandfather.

Paul -> parent of -> Marie
Marie -> parent of -> Tony
```

Direction convention: a kinship result always describes **what the target Person is to the reference Person** (`kinship(from = Tony, to = Paul) = GRANDFATHER`). The relationship shown on a Person "to the current User" uses the current User's linked Person as reference.

Paths use ACTIVE Persons and ACTIVE relationships only. Each step is `PARENT`, `CHILD` or `PARTNER`.

A Person that is ARCHIVED or MERGED therefore has no known kinship with anyone but itself: `NONE_KNOWN`, with an empty path (OQ-013).

When several paths exist, return the shortest one; when several shortest paths exist, prefer the one with only `PARENT`/`CHILD` steps; when still tied, choose deterministically by Person UUID order so that results are stable.

User-facing labels (French and English, gender-aware) are defined in `../ux/localization-and-kinship-labels.md`.

If there is a path but no supported concise label, return the path without inventing a kinship term.

If no path is known, say that no known relationship exists in the current Mbia graph; do not claim the people are unrelated in reality.

## 11. Concurrency

Stale forms must not silently overwrite newer updates.

A mutation uses optimistic concurrency with a resource version.

On conflict:

```text
CONCURRENT_MODIFICATION
```

The UI asks the User to reload the latest version before retrying.

## 12. Permission summary

### ADMIN

May:

- create/edit Persons;
- edit linked Persons;
- create/remove/restore relationships;
- archive/restore Persons;
- merge Persons;
- manage User-Person links;
- manage Family membership;
- add Memories;
- edit/archive any Memory.

### CONTRIBUTOR

May:

- create Persons;
- edit non-linked Persons;
- edit own linked Person;
- create/remove relationships;
- add Memories;
- edit/archive own Memories;
- leave the Family.

May not:

- merge Persons;
- archive Persons;
- edit another linked Person's identity;
- edit/archive other members' Memories;
- manage membership.

### VIEWER

Read only, plus:

- claim/unclaim their own Person;
- leave the Family.

## 13. Privacy simplification for MVP

Every ACTIVE Family member can read every Person and Memory in that Family.

The MVP does not implement:

- privacy per branch;
- privacy per Person;
- privacy per field;
- custom role matrices;
- elder approval workflows;
- family voting.

These are future product questions to validate against real usage.
