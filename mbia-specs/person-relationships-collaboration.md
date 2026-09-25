# Mbia MVP — Person, Relationships & Collaboration

**Version:** 0.2  
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
- parental cycle.

Warn, but permit confirmation, for probable inconsistencies such as suspicious dates or generation gaps.

## 8. Relationship removal

ADMIN and CONTRIBUTOR may remove a relationship.

Removal means `ARCHIVED`, not physical delete.

Before removal, UI warns that derived kinship may change.

ADMIN may restore if the restored graph remains valid.

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
- manage Family membership.

### CONTRIBUTOR

May:

- create Persons;
- edit non-linked Persons;
- edit own linked Person;
- create/remove relationships;
- add Memories;
- edit own Memories.

May not:

- merge Persons;
- archive Persons;
- edit another linked Person's identity;
- manage membership.

### VIEWER

Read only.

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
