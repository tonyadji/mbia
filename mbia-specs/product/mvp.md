# Mbia — MVP Functional Specification

**Version:** 0.1  
**Status:** Draft / Product discovery  
**Target:** first commercially testable release

## 1. MVP hypothesis

> Families are willing to create and collaboratively enrich a private digital space that represents their family relationships and preserves memories attached to family members.

The MVP must support the complete value path:

```text
Create family
    ↓
Add people
    ↓
Create relationships
    ↓
View family tree
    ↓
Add memories
    ↓
Invite relatives
    ↓
Collaborate
```

## 2. Platform

Responsive web application, designed mobile-first.

Native mobile applications are out of scope for the MVP.

## 3. Core domain concepts

```text
User
Family
FamilyMembership
Person
FamilyRelationship
Memory
```

## 4. Roles

### ADMIN

May:

- view the Family;
- create and edit Persons;
- create and remove relationships;
- add Memories;
- invite Users;
- manage Family members;
- edit Family information;
- archive/restore Persons;
- merge duplicate Persons.

### CONTRIBUTOR

May:

- view the Family;
- create Persons;
- edit non-linked Persons;
- edit their own linked Person;
- create/remove relationships;
- add Memories.

May not manage memberships, merge Persons or archive Persons.

### VIEWER

Read-only access.

## 5. Membership lifecycle

Product states:

```text
INVITED
ACTIVE
REMOVED
```

Technical persistence may model a pending invitation separately from an active membership.

## 6. Person

A Person belongs to exactly one Family in the MVP.

Minimum create requirement:

```text
firstName
```

Other fields are optional.

Core fields:

```text
id
familyId
firstName
middleNames
lastName
preferredName
gender
birthDate
birthDatePrecision
deathDate
deathDatePrecision
isDeceased
profilePicture
biography
status
linkedUserId
createdAt
createdBy
updatedAt
updatedBy
version
```

### Date precision

```text
EXACT
YEAR_ONLY
UNKNOWN
```

The system must never fabricate a precise date from an approximate year.

### Person lifecycle

```text
ACTIVE
ARCHIVED
MERGED
```

## 7. User ↔ Person association

A User may identify one Person in a Family as themselves.

Within one Family:

```text
1 User -> max 1 Person
1 Person -> max 1 User
```

A linked Person receives additional identity protection. Another Contributor may add Memories or relationships around that Person but may not alter the linked Person's identity fields.

## 8. Explicit relationship types

The MVP persists only:

```text
PARENT_OF
PARTNER_OF
```

`PARENT_OF` is directional.

`PARTNER_OF` is symmetric and does not imply legal marriage, current relationship status or monogamy.

A Person may have multiple partners.

## 9. Derived kinship

The MVP must derive at least:

```text
parent / child
grandparent / grandchild
sibling
uncle / aunt
nephew / niece
first cousin
```

Derived relations are not stored as independent source-of-truth facts.

## 10. Graph invariants

Block definite inconsistencies:

- self-parent;
- self-child;
- self-partner;
- cross-Family relationship;
- duplicate identical relationship;
- parental cycle.

Probable inconsistencies such as suspicious age gaps generate warnings, not hard blocks.

Example:

```text
parent born after child
→ warning
→ user may confirm
```

## 11. Duplicate detection

When creating a Person, Mbia should detect similar Persons using fields such as name and birth year.

A possible duplicate is never auto-merged and never blocks creation.

User choices:

```text
View existing person
Create anyway
```

## 12. Person merge

ADMIN only.

One Person is selected as the target. The duplicate becomes `MERGED`.

Move/deduplicate where possible:

- relationships;
- Memories;
- media associations;
- other references.

Block merge when it creates unresolved contradictions, for example two different linked Users.

Merge must be atomic.

## 13. Archiving

Persons and relationships use logical archiving rather than normal physical deletion.

ADMIN may archive/restore Persons.

ADMIN and CONTRIBUTOR may remove a relationship; removal archives it.

Restoration of a relationship is ADMIN-only and must re-run graph validity checks.

## 14. Family creation

A User creates a Family by providing its name.

The creator becomes ADMIN automatically.

After Family creation, onboarding should encourage:

```text
Add myself
→ add parent
→ add second parent
→ expand family
```

The User may also start with someone else.

## 15. Family tree

The tree is not required to render the entire Family at once.

The MVP uses a focused Person and displays a local graph around that Person, primarily:

- parents;
- focused Person;
- partners;
- children.

Users can recenter the tree on another Person.

## 16. Person profile

Display at least:

- photo/avatar;
- display name;
- birth/death information;
- relationship to current User when known;
- parents;
- partners;
- children;
- siblings;
- Memories;
- biography.

## 17. Memories

MVP types:

```text
PHOTO
STORY
```

### Photo

A photo may be linked to multiple Persons.

Fields include:

```text
file/media reference
caption
takenAt
takenAtPrecision
relatedPersons[]
createdBy
createdAt
```

### Story

Fields include:

```text
title
content
relatedPersons[]
createdBy
createdAt
updatedAt
```

Structured events are out of scope for the MVP.

## 18. Invitation

ADMIN may invite by email as:

```text
CONTRIBUTOR
VIEWER
```

Existing User:

```text
login
→ accept
→ Family access
```

New User:

```text
register
→ accept
→ Family access
```

After joining, ask:

> Are you already present in this tree?

Choices:

```text
Yes -> select Person
No -> create Person
Later
```

## 19. Search

Search within a Family at minimum by:

```text
firstName
lastName
preferredName
```

## 20. Family home

Show at minimum:

- Family name;
- access to tree;
- Person count;
- Memory count;
- recent activity;
- add Person action;
- add Memory action.

## 21. Authentication

Minimum product capabilities:

- sign-up;
- sign-in;
- sign-out;
- forgot password.

Authentication may be delegated to an external OIDC identity provider.

## 22. Privacy and isolation

Family is private by default.

An external User must not be able to discover or read another Family's people, Memories or graph.

Every backend Family-scoped operation must verify membership.

## 23. Media

MVP photo formats:

```text
JPEG
PNG
WEBP
```

Videos are out of scope.

The implementation may resize, compress and generate thumbnails.

## 24. Commercial readiness

The public product needs at least:

- landing page;
- account creation;
- terms of use;
- privacy policy;
- support/contact mechanism.

Billing is not required for an initial closed test, but architecture must not make plans/subscriptions impossible later.

## 25. Analytics

Track at least:

```text
user_registered
family_created
person_created
relationship_created
memory_created
family_invitation_sent
family_invitation_accepted
tree_viewed
person_profile_viewed
```

Primary funnel:

```text
User registered
→ Family created
→ first Person
→ 3 Persons
→ first relationship
→ first Memory
→ first invite
→ invite accepted
→ second-user contribution
```

## 26. MVP metrics

Candidate metrics:

- % accounts creating a Family;
- % Families with ≥ 5 Persons;
- % Families with ≥ 10 Persons;
- % Families with at least 1 Memory;
- % Families sending at least 1 invite;
- invite acceptance rate;
- % Families with multiple active Contributors;
- D+7 return;
- D+30 return.

Success thresholds are intentionally not fixed yet.

## 27. Out of scope

Do not add spontaneously:

- native mobile apps;
- video/audio;
- voice testimonies;
- structured events;
- advanced albums;
- facial recognition;
- generative AI;
- conversational assistant;
- biography generation;
- OCR;
- GEDCOM import/export;
- unknown-relative discovery;
- public trees;
- cross-Family matching;
- public social network;
- chat/comments/likes;
- advanced cousin degree engine;
- legal/traditional marriage modeling;
- advanced geolocation;
- family timeline;
- mandatory billing;
- marketplace.

## 28. Release criteria

The MVP is not ready until a real family can independently complete:

```text
sign up
→ create Family
→ create own Person
→ add parents
→ view tree
→ add grandparent
→ see derived kinship
→ add photo and Story
→ invite relative
→ relative joins
→ relative links themselves to existing Person
→ relative contributes
```

Cross-Family access must fail.

## 29. Definition of MVP complete

A family must be able to complete, without technical assistance:

```text
onboarding
→ creation
→ exploration
→ preservation
→ invitation
→ collaboration
```
