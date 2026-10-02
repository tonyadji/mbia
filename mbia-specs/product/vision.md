# Mbia — Product Specification

**Version:** 0.2  
**Status:** Work in progress

## 1. Purpose

Mbia is a digital family heritage platform that answers one question: **what is the story of my family?** It gathers the stories and memories of a family, and places them among the family members and the relationships between them.

The product specification has two goals:

- define a real commercial product that can be tested with families quickly;
- serve as a stable reference package for future Nambé-generated implementations.

The product must therefore be precise enough that an implementation agent does not have to invent important product decisions.

## 2. Product vision

Mbia enables a family to tell, preserve and transmit its shared history across generations.

The family's story comes first: what happened, what the elders remember, what the family wants to pass on. The genealogy graph is the index of that story: it shows who is who and how everyone is related. The long-term product may contain:

- people;
- family relationships;
- events;
- photographs;
- videos;
- documents;
- stories and testimonies;
- family timelines and memories;
- family questions asked to the elders and the whole family.

Mbia is not a family-tree drawing tool. A family comes to Mbia to answer "what is the story of my family?", not "what is my family tree?". The tree is the human graph that indexes that story.

## 3. Initial positioning

Mbia is designed first for African families, with particular attention to Cameroonian realities, while avoiding a data model that is unnecessarily specific to one culture.

The product must be able to evolve toward cases such as:

- large and extended families;
- several generations;
- diaspora families across cities and countries;
- half-siblings;
- several partnerships during one person's life;
- blended families;
- traditional, administrative and preferred names;
- unknown information;
- deceased family members;
- orally transmitted family history.

## 4. Core value proposition

> Allow a family to tell, preserve and transmit its history across generations.

A user reads the story of their family through time, year after year, and tells what they know of it. They should eventually be able to open the profile of a grandparent and find photographs, stories, wedding memories, family testimonies and other historical material while understanding exactly how that person is related to them.

## 5. Fundamental concepts

### User

A Mbia account that can access one or more family spaces.

### Person

An individual represented in a family history. A Person does not need to have a Mbia account.

**User ≠ Person.**

### Family

A private collaborative family space containing people, relationships, memories and members with access rights.

### FamilyMembership

A User's participation in a Family, including role and status.

### FamilyRelationship

An explicit relationship fact between Persons. The MVP persists a small set of structural facts and derives other kinship labels from them.

### Memory

A moment of the family's story, told about one or more Persons and dated when the family knows when it happened. The MVP supports written stories with photos.

## 6. Graph principle

The family graph is a core domain primitive. It must:

1. store fundamental relationships;
2. allow traversal of the family;
3. derive indirect kinship;
4. explain how two people are connected.

Example:

```text
Paul PARENT_OF Marie
Marie PARENT_OF Tony
```

Mbia can derive that Paul is Tony's grandparent.

Derived relations are not persisted as independent facts when they can be calculated from the graph.

## 7. Version strategy

### MVP — validate commercial interest

Test whether families find enough value in a private collaborative family space to tell and read their family's story together.

Core loops, in this order:

```text
TELL
Person + Memory (story, photos, when it happened)

READ
Family story through time (year by year)

TRANSMIT
Invitation + collaboration

STRUCTURE
Relationships + tree, as the index of the story
```

### V1 — family heritage

Candidates:

- structured family events;
- richer albums and media;
- documents;
- richer profiles;
- improved permissions;
- search and history;
- family questions: a member asks the family a question, the others answer (`mvp.md` §27);
- events and ceremonies in the family story.

### V2 — intelligent family memory

Candidates:

- natural-language search;
- family questions and answers;
- biography generation;
- audio transcription;
- contradiction detection;
- relationship suggestions;
- family summaries;
- document import assistance.

## 8. Specification rule

Every important feature should ultimately define:

- user story;
- business rules;
- nominal path;
- edge cases;
- acceptance criteria;
- expected UX;
- data model implications;
- API behavior when relevant.

An implementation agent may choose implementation details. It must not invent product meaning, permission rules, business conflict behavior or required data semantics.

## 9. Benchmark rule for Nambé

A stable specification snapshot such as `Mbia MVP Specification 1.0` can be frozen and supplied unchanged to Nambé, Codex, Claude Code or another implementation system.

The result may then be compared on:

- specification compliance;
- functional completeness;
- business-rule correctness;
- code quality;
- architecture;
- tests;
- security;
- maintainability;
- UX;
- amount of human intervention required.

The benchmark must never dictate the product. Mbia is first a real product, then a benchmark.
