# Mbia — Product Specification

**Version:** 0.1  
**Status:** Work in progress

## 1. Purpose

Mbia is a digital family heritage platform centered on family members and the relationships between them.

The product specification has two goals:

- define a real commercial product that can be tested with families quickly;
- serve as a stable reference package for future Nambé-generated implementations.

The product must therefore be precise enough that an implementation agent does not have to invent important product decisions.

## 2. Product vision

Mbia enables a family to build, preserve and transmit its shared history across generations.

The genealogy graph is the organizing structure for that heritage. The long-term product may contain:

- people;
- family relationships;
- events;
- photographs;
- videos;
- documents;
- stories and testimonies;
- family timelines and memories.

Mbia is not merely a family-tree drawing tool. The tree is the human graph around which the family's digital heritage is organized.

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

> Allow a family to build, preserve and transmit its history across generations.

A user should eventually be able to open the profile of a grandparent and find photographs, stories, wedding memories, family testimonies and other historical material while understanding exactly how that person is related to them.

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

A piece of family heritage associated with one or more Persons. The MVP supports photos and written stories.

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

Test whether families find enough value in a private collaborative family space to begin documenting their relatives and shared memories.

Core loops:

```text
STRUCTURE
Person + relationship + tree

PRESERVE
Photo + Story

TRANSMIT
Invitation + collaboration
```

### V1 — family heritage

Candidates:

- structured family events;
- richer albums and media;
- documents;
- richer profiles;
- improved permissions;
- search and history;
- timelines and event-oriented memories.

### V2 — intelligent family memory

Candidates:

- natural-language search;
- family questions and answers;
- biography generation;
- audio transcription;
- contradiction detection;
- relationship suggestions;
- family timelines and summaries;
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
