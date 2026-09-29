# Mbia MVP — UI Screens Specification

**Version:** 0.3  
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

In this order (`mvp.md` §20):

- Family name;
- **the family story**: the strip of years of `mvp.md` §20 under the title "Our story" (OQ-067). Each entry is a button with its year and its number of Memories ("1975 · 3 memories"); the last entry, when there are Memories without a year, uses the undated label. The strip scrolls sideways, by swipe and by keyboard, and opens on the most recent year (OQ-064). An entry opens SCREEN-016. While the Family has Persons but no Memory, the strip is replaced by a short invitation: "Your family's story starts with a first memory", with `Tell a memory` for ADMIN / CONTRIBUTOR (text only for a VIEWER);
- Person count;
- Memory count;
- recent activity: the 10 most recent lines of `mvp.md` §20, each with who, what and when ("Tony added 6 people · 2 hours ago"), leading to its Person or Memory while it is ACTIVE (OQ-054). Without any activity yet, the section is not shown.

## Welcome after joining

On the first arrival after accepting an invitation (SCREEN-010), a short message above the Family: "Welcome to the {familyName} family", with `View the family tree` (centred on the member's linked Person when there is one) and, for a CONTRIBUTOR, `Add a memory`. It is shown once and can be closed (OQ-050).

## Primary action

ADMIN / CONTRIBUTOR: `Tell a memory` (SCREEN-006) (provisional, OQ-066). A VIEWER has no primary action: the family story is the content of the screen.

## Secondary actions

- `View family tree`, for every member;
- ADMIN / CONTRIBUTOR only: `Add a relative` when the current User has a linked Person (choices: `family-tree-ux.md` §9.1; the Family carries `myLinkedPersonId`, OQ-010), otherwise `Add a person`.

## Empty state

When the Family has no Persons:

```text
Welcome to the {familyName} family

Tell the story of your family, one memory at a time.

[ Tell a first memory ]
[ Add a person ]
```

ADMIN / CONTRIBUTOR see both actions; a VIEWER sees the explanatory text only.

`Tell a first memory` opens SCREEN-006, which asks first who the memory is about (SCREEN-006 "Who is this memory about?", OQ-065). `Add a person` opens SCREEN-004 in "Standalone Person" mode.

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

Empty state (Family without Persons): explains that nobody is in the tree yet; ADMIN / CONTRIBUTOR see `Start with me` (SCREEN-004 "Start with me" mode) and `Add someone else`; a VIEWER sees the text only. It no longer mirrors Family Home, which starts with a memory (`mvp.md` §14).

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

## Memories section

The Person's ACTIVE Memories, most recently added first, 20 at a time with `Show more` (OQ-034): Memory cards (the thumbnail of the first photo when there is one, the title and the first lines of the text), each opening SCREEN-013. ADMIN / CONTRIBUTOR see `Add a memory` (SCREEN-006 with this Person preselected).

Empty state: explains that no Memory is linked to this Person yet; ADMIN / CONTRIBUTOR also see `Add a memory`.

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
- ADMIN: `Invite {firstName}` (SCREEN-009 with this Person) when the Person is living and linked to no User; when an invitation for this Person is already pending, `Invitation pending` with `Renew` instead (OQ-050); for an email invitation whose email could not be sent, "The email could not be sent" (OQ-055, OQ-060); `Renew` of an email invitation sends the email again and shows the result as SCREEN-009 does, instead of the link;
- unlink from the current User's own linked Person;
- ADMIN: archive / restore, merge a duplicate (SCREEN-COMPONENT-004), from the duplicate's profile (OQ-029).

---

# SCREEN-006 — Add Memory

## Access

ADMIN / CONTRIBUTOR.

## Fields

One form, with no initial choice (OQ-042):

```text
title *
content            required when no photo is added
happenedAt         optional: `When did it happen?`, as exact date, year only or unknown,
                   like a birth date; a date in the future is refused (`mvp.md` §17, OQ-063)
photos[]           0 to the Family's limit (3 at launch)
  caption          optional
  takenAt          optional, behind `More information`: `When was this photo taken?`,
                   as exact date, year only or unknown, like a birth date (OQ-033)
relatedPersons[] * (at least one)
```

A story is plain text: line breaks are kept; no formatting is interpreted (OQ-032).

## Photos

- `Add a photo` opens the file picker (JPEG, PNG, WEBP; max 15 MB), which may select several files. Only the free places are kept, in the order chosen; the others are not added and a message says how many photos a Memory can have.
- At the limit, `Add a photo` is disabled and the same message explains why.
- Each photo is reduced in the browser and sent as soon as it is chosen, with its progress; on failure, explain in human language and offer `Try again` or `Remove`. Its thumbnail is shown with its caption field and `More information`.
- `Publish` stays disabled while a photo is being sent or has failed.
- Photos keep the order in which they were added; they are not reordered.

## Preselection

When launched from a Person profile, preselect that Person; otherwise preselect the User's linked Person when it exists.

## Who is this memory about?

When the Family has no Person yet (`Tell a first memory`, SCREEN-002), the form starts by asking who the memory is about, before the title (provisional, OQ-065):

```text
Who is this memory about?

[ Me ]            creates the User's own Person, linked to them (as SCREEN-004 "Start with me")
[ Someone else ]  first name *, last name
```

In every other case, the related Persons are chosen as today, and a Person who is not in the tree yet can be added from the same field (`Add {typed name}`: first name and last name only).

A Person created from this form is created when the Memory is published, just before it; possible duplicates (`mvp.md` §11) are offered as choices. When the Person is created but the Memory is then refused, the Person stays created, the form keeps what was written and says so, and the User can publish again (OQ-065).

---

# SCREEN-013 — Memory

## Logical route

```text
/families/{familyId}/memories/{memoryId}
```

## Access

Any ACTIVE Family member. An archived Memory is not found (OQ-037).

## Display

- its title;
- when it happened, when known ("In 1975", "On 12 March 1975"), linking to the year of the family story (SCREEN-016);
- its photos, in the order they were added, as a grid of square thumbnails without text (2 columns on a phone, 3 from tablet width). Each thumbnail is a button named by the photo's alternative text: its caption, otherwise "Photo {n} of {count}". A tap opens the photo viewer on that photo. This layout is provisional until the human has confirmed it (OQ-048: the stacked layout was not kept);
- its full text, as plain text with line breaks kept, when there is one;
- the related Persons, each linking to their profile; an archived Person is shown by name, marked "archived", and links to their profile only for the ADMIN (OQ-035);
- who added it and when ("Former member" when the account was deleted).

## Photo viewer

Over the whole screen, for every member (VIEWER included): the display version of the photo, as large as the screen allows without cropping, then its caption, when it was taken if known, and "Photo {n} of {count}". `Previous photo` and `Next photo` move between the photos in their order, disabled on the first and the last (no wrap-around); a horizontal swipe does the same. `Close` and Escape close it. The arrow keys move between photos on a keyboard. Focus stays inside the viewer and comes back to the thumbnail it was opened from. No zoom, download, share or edit action (OQ-048, provisional).

## Actions

For the creator or an ADMIN, with a role that can write (OQ-041):

- `Edit` (SCREEN-014);
- `Archive`: a Modal confirms that the Memory will disappear for the whole Family; afterwards the User returns to where they came from.

(OQ-032)

---

# SCREEN-014 — Edit Memory

## Access

The creator or an ADMIN, with a role that can write (OQ-041).

## Fields

The fields of SCREEN-006, with the Memory's date, which can be changed or removed, and its photos: their caption and `More information` can be changed, a photo can be removed, and `Add a photo` adds new ones within the limit. A photo is not replaced in place, nor reordered.

When a lowered limit leaves the Memory with more photos than allowed, its photos stay; `Add a photo` stays disabled until enough are removed (`mvp.md` §17).

## Behavior

When the related Persons are changed, they must keep at least one ACTIVE Person; the title and text can be corrected without changing them (OQ-043). The text may be emptied only while the Memory keeps a photo. An archived Person already on the Memory may stay, but cannot be added (OQ-035).

The form sends the version of the Memory it loaded. On `CONCURRENT_MODIFICATION`, it behaves as SCREEN-012: explain and offer `Reload latest version`, never merge values.

(OQ-032)

---

# SCREEN-015 — Family Memories

## Logical route

```text
/families/{familyId}/memories
```

The `Memories` entry of the primary navigation (`family-tree-ux.md` §4).

## Access

Any ACTIVE Family member.

## Display

The Family's ACTIVE Memories, most recently added first, 20 at a time with `Show more` (OQ-034): Memory cards as on the profile (SCREEN-005), each opening SCREEN-013. No filter (OQ-042).

ADMIN / CONTRIBUTOR see `Add a memory` (SCREEN-006).

## Empty state

Explains that the Family has no Memory yet; ADMIN / CONTRIBUTOR also see `Add a memory`, a VIEWER sees the text only.

(OQ-032)

---

# SCREEN-016 — What happened in {year}

## Logical route

```text
/families/{familyId}/story/{year}
/families/{familyId}/story/undated
```

## Access

Any ACTIVE Family member, VIEWER included (`mvp.md` §20).

## Display

- the title "What happened in {year}", or the undated label for the Memories without a year (OQ-067);
- the strip of years of SCREEN-002, the current entry marked (not by color only) and kept in view, so that another year opens without going back; `Previous year` and `Next year` move to the neighbouring entries of the strip, disabled at its ends;
- the ACTIVE Memories of that year, in the order of `mvp.md` §20 (OQ-064), 20 at a time with `Show more`: Memory cards as on SCREEN-015, each with its date ("12 March" when exact, nothing more when the year only), opening SCREEN-013;
- ADMIN / CONTRIBUTOR see `Tell a memory` (SCREEN-006).

A year with no ACTIVE Memory left (its last Memory archived or moved to another year) shows an empty state that explains it and offers the other years of the strip. An invalid year (not a number from 1 to 9999) is not found.

Later, events and ceremonies of the year appear on this screen too (`mvp.md` §27); the MVP shows Memories only.

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
- User-facing role label: `Family administrator` (FR "Administrateur de la famille"), `Can contribute`, `Read only` (OQ-062);
- membership status when relevant;
- the linked Person, when any.

ADMIN sees `Invite a relative`.

For each member, their kinship to the current User when both have a linked Person ("Awa · your mother"), otherwise the name of their linked Person (OQ-050). The member list does not carry the Person's gender: `FIRST_COUSIN` takes its neutral form (OQ-062).

ADMIN also sees a "Pending invitations" section:

- the Person it was sent for, while that Person is ACTIVE ("For Awa Ngo", OQ-056); otherwise the email, or "Shared link" when no email;
- role;
- expiry date;
- for an email invitation whose email could not be sent, "The email could not be sent" (OQ-055);
- actions: `Renew` (new link, and email resent for email invitations), `Revoke` (with confirmation).

ADMIN member actions (not on themselves): change role (Can contribute / Read only), remove from Family (with confirmation explaining that their contributions stay).

Non-ADMIN members see a `Leave this family` action (with confirmation). The only ADMIN does not see it: in its place, a sentence explains that a family always keeps its administrator, and that support can hand the role over (`mvp.md` §4).

---

# SCREEN-009 — Invite Member

## Access

ADMIN only. Opened from SCREEN-008 (`Invite a relative`) or from a Person's profile (`Invite {firstName}`, SCREEN-005): the invitation then carries this Person, named at the top of the screen ("Invite Awa Ngo") (OQ-050).

## Fields

```text
channel *        Send by email | Share a link      (Share a link first on a phone)
email            required for "Send by email", optional otherwise
permission *     Can contribute preselected
```

User-facing values:

```text
Can contribute -> CONTRIBUTOR
Read only -> VIEWER
```

On success, "Send by email" (the email is sent in the inviter's current language before the screen answers, OQ-060):

```text
Invitation sent
```

or, when the mail provider refused or could not be reached, "The email could not be sent": the invitation is saved all the same and `Renew` sends the email again (OQ-055).

On success, "Share a link":

- show the link with `Copy link` and `Share` (uses the device share sheet when available, which includes WhatsApp);
- a pre-filled message in the current language, naming the inviter and, when there is one, the Person, for example: "Tony t'invite dans la famille ADJI sur Mbia, ton profil Awa Ngo t'y attend : {link}", or without Person: "Tony t'invite dans la famille ADJI sur Mbia : {link}";
- explain that the link works once and expires in 14 days.

The link cannot be displayed again later; the ADMIN can `Renew` it from the Members screen.

`Invite someone else` starts a new invitation from the success screen.

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

The Person the invitation was sent for is never shown here (OQ-050).

## Behavior

```text
Join the family
→ if signed out: Keycloak sign-in (with sign-up option), then return here
→ accept
→ "Are you {displayName}?" when the invitation carries a Person that is still ACTIVE and linked to no User
→ otherwise, or after "No": "Are you already in this tree?" (see mvp.md §18)
→ Family Home, with its welcome (SCREEN-002)
```

The browser remembers the invitation from the first visit until it is accepted or no longer valid: after signing up, verifying the email (even from the mailbox, in another tab) or signing in, the User comes back here (OQ-050).

"Are you {displayName}?" shows the Person's name, one parent or birth year, and three actions: `Yes, it's me` (links the User to the Person), `No`, `Later`.

"Are you already in this tree?" lists the Persons that can be claimed, searchable, each with one parent when known (the path sentence of `localization-and-kinship-labels.md` §4, for example "Awa Ngo is the daughter of Marie Ngo"), otherwise the birth year (`listClaimablePersons`). The parent shown is the first in the order of the tree (OQ-015: the eldest known). Choosing a Person asks "Are you {displayName}?" again before linking, and `No` there goes back to the list.

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

## Photo

In Identity: `Add a photo` when the Person has none, otherwise `Change the photo` and `Remove the photo`. A new photo is uploaded, never chosen from Memories; it is shown centre-cropped in the round avatar, with no crop step (OQ-040). The same photo control is the `profilePicture` field of SCREEN-004.

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

A focused modal or panel, not a general data-merging editor, opened from the profile of the duplicate: the ADMIN first searches the profile to keep (SCREEN-007), then sees source and target summaries side by side, with an explanation:

- the target remains;
- the source becomes merged into the target;
- relationships (and Memories) are moved and deduplicated;
- when both have a value for an identity field, the target value is kept.

## Behavior

Requires explicit confirmation, then opens the kept profile. When the merge is refused, explain the conflict (OQ-026); never offer to force it.

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
