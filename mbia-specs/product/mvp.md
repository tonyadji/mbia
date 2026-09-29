# Mbia — MVP Functional Specification

**Version:** 0.3  
**Status:** Draft / Product discovery  
**Target:** first commercially testable release

## 1. MVP hypothesis

> Families are willing to create, and enrich together, a private space where they tell and pass on the stories and memories of their family, the family tree showing who is who.

The MVP answers "what is the story of my family?" before "what is my family tree?" (`vision.md` §1). It must support the complete value path:

```text
Create family
    ↓
Add people
    ↓
Add memories
    ↓
View family story
    ↓
Invite relatives
    ↓
Collaborate
    ↓
Create relationships
    ↓
View family tree
```

## 2. Platform

Responsive web application, designed mobile-first.

Native mobile applications are out of scope for the MVP.

### Languages

The MVP ships in **French (default) and English**. Every user-facing text, email and authentication page exists in both languages. The User can change language in account settings.

Language rules and kinship labels are defined in `ux/localization-and-kinship-labels.md`.

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
- merge duplicate Persons;
- edit or archive any Memory.

In the MVP, the ADMIN role cannot be granted through the product: the Family creator is its only ADMIN. Transferring the ADMIN role (for example after a death or a conflict) is handled manually by Mbia support.

### CONTRIBUTOR

May:

- view the Family;
- create Persons;
- edit non-linked Persons;
- edit their own linked Person;
- create/remove relationships;
- add Memories;
- edit or archive their own Memories.

May not manage memberships, merge Persons, archive Persons or edit/archive other members' Memories.

### VIEWER

Read-only access, except that a VIEWER may identify themselves in the tree (claim/unclaim their own Person) and leave the Family.

## 5. Membership lifecycle

Product states:

```text
INVITED
ACTIVE
REMOVED
```

Technical persistence may model a pending invitation separately from an active membership.

Rules:

- ADMIN may remove a CONTRIBUTOR or VIEWER, and change a member's role between CONTRIBUTOR and VIEWER.
- ADMIN cannot change their own role.
- Any CONTRIBUTOR or VIEWER may leave a Family by themselves.
- The Family must always keep one ACTIVE ADMIN: the last ADMIN cannot leave or be removed (`LAST_ADMIN_REQUIRED`).
- Removing a member or leaving the Family:
  - never deletes the Persons, relationships or Memories they contributed;
  - releases their linked Person (the Person stays in the tree, no longer linked to that User);
  - is recorded in audit and activity.
- A removed User invited again returns with the role of the new invitation.

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

If the Person is not deceased, the death date is `UNKNOWN`. A deceased Person may still have an unknown death date.

### Display name

1. When `preferredName` is not blank, it is the display name.
2. Otherwise the display name is `firstName` followed by `lastName` when present.
3. `middleNames` appear on the full profile, not in tree cards.

The display name is computed for presentation; it is not stored.

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

The matching rule is defined in `domain/person-relationships-collaboration.md` §4.1.

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

Archived items stay hidden from the tree and normal search, but the ADMIN can find them to restore them: the list of archived Persons, and the removed relationships of each Person.

## 14. Family creation

A User creates a Family by providing its name.

The creator becomes ADMIN automatically.

After Family creation, onboarding starts with the family's story:

```text
Tell a first memory
→ who is it about? (me, or someone else: a Person created on the way)
→ the story, its photos, when it happened
→ read it in the family story
→ add relatives, invite them
```

Building the tree first stays possible (`Add a person`), but it is no longer the first gesture. Details: SCREEN-002, SCREEN-006 (OQ-065).

## 15. Family tree

The tree is not required to render the entire Family at once.

The MVP uses a focused Person and displays a local graph around that Person, primarily:

- parents;
- focused Person;
- partners;
- children.

Users can recenter the tree on another Person.

The exact layout rules are defined in `ux/family-tree-ux.md` §6.

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

A Memory is a story told about one or more Persons: a title, a text and up to a few photos (OQ-042). There is one kind of Memory; photos are added to it rather than being a separate kind of Memory.

Fields include:

```text
title            required
content          the story text; required when the Memory has no photo
happenedAt       optional: when it happened, with its precision (below)
photos[]         0 to N, in the order they were added
  photo          an uploaded image (§23)
  caption        optional
  takenAt        optional, with takenAtPrecision (exact date, year only or unknown)
relatedPersons[]
createdBy
createdAt
updatedAt
```

### When a Memory happened

A Memory may say when it happened, with the precision of a birth date (§6): an exact date, a year only, or unknown (the default). It is the date of the story, not of its photos: a photo keeps its own optional taken date, and neither fills the other.

- One date per Memory: a Memory spanning several years ("her childhood in Mokolo, 1950–1960") takes one year in the MVP; periods are out of scope.
- A date in the future is refused (provisional, OQ-063).
- The date can be set, changed or removed when the Memory is written or edited.
- Memories written before this rule have no date: they are undated.
- The year of a Memory places it in the family story (§20).

### Photos of a Memory

- N is an application setting, 3 at launch and never above 10. The limit applies only when photos are added: a Memory that already has more photos than a lowered limit keeps them, and may lose some, but gains none until it is below the limit.
- Photos are uploaded while the Memory is written or edited, and belong to it once it is published or saved.
- Photos keep the order in which they were added; they are not reordered. A photo can be removed from the Memory; it is not replaced in place.
- A photo of a Memory is never used as a Person's photo, and a Person's photo is never added to a Memory (OQ-040).
- More photos per Memory, albums or a gallery of the Family stay out of scope (§27).

### Common Memory rules

- Every Memory is linked to **at least one** ACTIVE Person. The rule is checked when a Memory is created, and when an edit changes its related Persons (an edit of the title or text alone is not refused, OQ-043); a Person newly linked must be ACTIVE, while an archived Person already linked may stay. Archiving a Person never changes nor hides its Memories (OQ-035).
- The creator may edit or archive their own Memory; ADMIN may edit or archive any Memory of the Family. Both need a role that can write: a VIEWER, even the creator, is read-only (OQ-041).
- Archiving hides the Memory everywhere. Restoring an archived Memory is not available in the MVP product; support may restore it on request.

Structured events are out of scope for the MVP.

## 18. Invitation

ADMIN may invite a relative as:

```text
CONTRIBUTOR
VIEWER
```

### Invitation channels

Two channels, same rules:

```text
EMAIL  -> Mbia sends the invitation link to an email address
LINK   -> Mbia shows the link; the ADMIN shares it (WhatsApp, SMS, …)
```

For `LINK`, the email address is optional and only informative.

### Inviting a Person of the tree

Most relatives invited are already in the tree. An ADMIN may therefore invite **from a Person's profile** (`Invite {firstName}`), as well as from the Members screen (OQ-050):

- the Person must be ACTIVE, living and linked to no User;
- the invitation then carries this Person as a suggestion; it never binds the invitee to it;
- when that Person is archived or merged before acceptance, the invitation stays valid; the Person is neither offered to the invitee nor shown with the invitation while it is not ACTIVE, and a merge does not move it to the kept Person (OQ-056);
- a Person has at most one pending invitation (`INVITATION_ALREADY_PENDING`): the ADMIN renews it instead;
- the Person is never shown before the invitee is signed in.

Only the ADMIN invites (OQ-051). A link works once: a reusable group link is deferred (OQ-052).

Rules:

- one invitation = one link = one role; the link is **single-use**;
- an invitation expires **14 days** after creation or renewal;
- the invitation is not bound to an email address: the first authenticated User who accepts it joins the Family (the preview shows who invited and to which Family, so the recipient can recognise it);
- the full link is shown to the ADMIN only when the invitation is created or renewed (it is not stored in clear);
- ADMIN can see pending invitations, **revoke** one, or **renew** one (new link, new expiry; the previous link stops working; for `EMAIL`, the email is sent again);
- the invitation email is sent in the inviter's current language;
- an ACTIVE member who opens a link for their own Family is simply taken to the Family; the invitation stays pending;
- expired, revoked or already used links show a clear message and suggest asking the ADMIN for a new link;
- the browser remembers a pending invitation until it is accepted or no longer valid, so that signing up, verifying the email or opening another tab always brings the invitee back to it (OQ-050);
- the email is sent after the invitation is saved: when it cannot be sent, the invitation still exists, the ADMIN sees that the email could not be sent, and `Renew` tries again (OQ-055).

### Acceptance flow

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

After joining, when the invitation carries a Person that is still ACTIVE and linked to no User, ask first (OQ-050):

> Are you {displayName}?

```text
Yes, it's me -> the User is linked to this Person
No           -> the question below
Later
```

Otherwise, or after `No`, ask:

> Are you already present in this tree?

Choices:

```text
Yes -> select Person
No -> create Person
Later
```

In the list of Persons to select, each Person shows one of their parents when one is known, otherwise their birth year, so that two Persons with the same name can be told apart.

A VIEWER cannot create Persons: for a VIEWER, `No` explains that a contributor can add them, and offers `Later`.

Then Family Home welcomes the new member once, with `View the family tree` (centred on their Person when linked) and, for a CONTRIBUTOR, `Add a memory`.

Keeping the invitee inside Mbia during sign-up (an access code sent by email instead of the Keycloak pages) is an open question (OQ-053).

## 19. Search

Search within a Family at minimum by:

```text
firstName
lastName
preferredName
```

Rules:

- only ACTIVE Persons are returned;
- matching is a substring match after trimming, case-insensitive and accent-insensitive, in each of these fields and in "firstName lastName" (so "Marie Dup" finds Marie Dupont) (OQ-022);
- results are paginated and ordered by display name (locale-independent, case- and accent-folded: "Éloïse" sorts with "Eloise"), then creation date, then UUID (OQ-022).

## 20. Family home

Show at minimum:

- Family name;
- the family story (below), first on the screen;
- add Memory action (the primary action, provisional, OQ-066);
- recent activity (below);
- Person count;
- Memory count;
- access to tree;
- add Person action.

### Family story

The family story answers "what is the story of my family?" by showing the Family's Memories through time. It is visible to every member, VIEWER included, and shows only ACTIVE Memories.

On Family Home, a strip of years that scrolls sideways (provisional details, OQ-064):

- one entry per year that has at least one Memory, in chronological order, opened on the most recent year, each with its number of Memories;
- a last entry for the Memories without a year, when there are any;
- hidden while the Family has no Memory, replaced by the invitation to tell a first memory.

A year opens "What happened in {year}" (SCREEN-016):

- the Memories of that year: those with an exact date first, in date order, then those with the year only, in the order they were added (provisional, OQ-064);
- a way to move to another year of the strip without going back.

The Memories without a year open the same screen, titled with the undated label (OQ-067), most recently added first.

Later versions add events and ceremonies to the year screen (§27); the MVP shows Memories only.

### Recent activity

A short, readable feed of what happened in the Family, for every member (OQ-054):

- shown: a Person added, archived, restored or merged; a relationship added or removed; a Memory added; a member who joined, left or was removed. Edits, role changes and invitations are audited, not shown;
- consecutive actions of the same member and type, each within one hour of the previous one, form one line ("Tony added 6 people");
- each line leads to its Person or Memory while it is ACTIVE; an archived item is named without a link;
- names are those at the time of the action;
- Family Home shows the 10 most recent lines;
- the feed starts when Phase 5 is deployed: earlier actions are not shown.

## 21. Authentication

Minimum product capabilities:

- sign-up;
- sign-in;
- sign-out;
- forgot password;
- email verification.

Authentication is delegated to Keycloak (see `technical/adr/ADR-005-keycloak-identity-provider.md`). Sign-up, sign-in and password reset pages are hosted by Keycloak, themed as Mbia, in French and English.

A User must verify their email address before using Mbia.

When an unauthenticated visitor accepts an invitation, they are sent to sign-in (with a sign-up option) and brought back to the same invitation afterwards.

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

Rules:

- maximum file size: 15 MB;
- large photos are reduced in the browser before upload to save mobile data;
- Mbia removes all photo metadata, including GPS location, before any photo is shown;
- Mbia shows thumbnails in lists and a larger version on open; the original file is not kept.

Technical details: `technical/adr/ADR-007-image-processing.md`.

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
family_story_viewed
family_invitation_sent
family_invitation_accepted
tree_viewed
person_profile_viewed
```

Primary funnel:

```text
User registered
→ Family created
→ first Memory
→ first dated Memory
→ family story viewed
→ first invite
→ invite accepted
→ second-user contribution
→ first relationship
```

## 26. MVP metrics

Candidate metrics:

- % accounts creating a Family;
- % Families with at least 1 Memory;
- % Families with ≥ 5 Memories;
- % Families whose story covers ≥ 3 different years;
- % Families whose story is viewed by ≥ 2 members;
- % Families with Memories written by ≥ 2 members;
- % Families sending at least 1 invite;
- invite acceptance rate;
- % Families with ≥ 5 Persons;
- D+7 return;
- D+30 return.

Success thresholds are intentionally not fixed yet.

Analytics are pseudonymous: they never contain names, email addresses, Person data, story text or photos. Tool choice: `technical/adr/ADR-008-product-analytics.md`.

## 27. Out of scope

Do not add spontaneously:

- native mobile apps;
- video/audio;
- voice testimonies;
- structured events, ceremonies (they will join the year screen of the family story later);
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
- chat/comments/likes, and family questions (below);
- advanced cousin degree engine;
- legal/traditional marriage modeling;
- advanced geolocation;
- periods spanning several years in the family story;
- mandatory billing;
- marketplace.

### Family questions (V1)

Planned for V1, not in the MVP (`vision.md` §7): a member asks the family a question ("Who was grandmother Marie's father?"); it opens a thread where the other members answer; the author marks the question answered. How it joins the family story, who may ask, the kinds of answers and how members learn about new questions are open (OQ-068).

### Ephemeral contributors (idea, not decided)

Recorded so that the idea is not lost; not planned, no rule applies yet. A person outside the Family who knows part of its story (a family friend, for example) would contribute to one resource (a Memory, then a family question) through a single-use contribution link and a code given by the one who asked, valid 24 hours by default. Such access would be an exception to Family isolation (§22) and to authentication through Keycloak (ADR-005): it would need an ADR and its own review before any work.

## 28. Release criteria

The MVP is not ready until a real family can independently complete:

```text
sign up
→ create Family
→ tell a first Memory about a Person created on the way, with a photo, its story and its year
→ add people
→ add memories
→ view the family story, and what happened in a year
→ invite relative
→ relative joins
→ relative links themselves to existing Person
→ relative contributes a Memory, which appears in the family story
→ create relationships (parents, a grandparent)
→ view tree
→ see derived kinship
```

Cross-Family access must fail.

## 29. Definition of MVP complete

A family must be able to complete, without technical assistance:

```text
onboarding
→ creation
→ telling
→ reading the family story
→ invitation
→ collaboration
→ structuring the tree
```

## 30. Personal data rights

Mbia stores data about living people who may not have an account, including children. The MVP handles data rights as follows:

- **Terms of use** state that contributors must only publish content they have the right to share, and must respect the people concerned.
- **Account deletion:** the User requests it from account settings (link to support). Support processes it within 30 days:
  - all memberships are removed and linked Persons released;
  - the Keycloak account is deleted;
  - the Mbia User record is anonymised (email, name and identity subject removed);
  - content contributed to a Family stays in that Family, attributed to "Former member", unless the User also asks for specific content to be removed.
- **Request from a Person without account** (or their legal guardian) to remove their data: sent to support; the ADMIN may archive the Person immediately; support performs permanent deletion when required.
- **Family deletion:** requested by the ADMIN through support.
- Photo location metadata is always removed (§23).

A self-service deletion feature is not required for the MVP, but the support procedure must be written and tested before the first real family is onboarded.
