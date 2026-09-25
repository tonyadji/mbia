# Mbia — Specification Pack

This repository-ready pack contains the product and technical specifications for the Mbia MVP.

## Goal

The pack has one main purpose:

1. define a commercially testable Mbia MVP precisely enough for a human team or coding agent to build it;


## Authority order

```text
Product vision and MVP rules
        ↓
Domain specifications
        ↓
UX/UI specifications
        ↓
Technical specifications
        ↓
OpenAPI contract
        ↓
Automated tests
        ↓
Implementation
```

When two documents conflict, the higher level in this list wins until the contradiction is explicitly resolved in the specs.

## Structure

```text
mbia-specs/
├── README.md
├── open-questions.md
├── delivery/
│   ├── phase-1-walking-skeleton.md
│   └── phase-2-core-family-graph.md
├── product/
│   ├── vision.md
│   ├── mvp.md
│   ├── domain/
│   │   └── person-relationships-collaboration.md
│   └── ux/
│       ├── family-tree-ux.md
│       ├── screens.md
│       ├── design-guidelines.md
│       ├── localization-and-kinship-labels.md
│       └── references/
│           └── mbia-mvp-mockups.png
└── technical/
    ├── README.md
    ├── technical-specification.md
    ├── stack.md
    ├── architecture.md
    ├── data-model.md
    ├── genealogy.md
    ├── adr/
    │   ├── README.md
    │   └── ADR-001 … ADR-008
    └── api/
        └── openapi.yaml
```

The visual mockup is a design reference. Product rules and screen behavior are defined in text and remain authoritative.

## Changelog

### 0.3 — 2026-09-25

Precisions needed to deliver Phase 2 (core family graph):

- deterministic possible-duplicate rule (`person-relationships-collaboration.md` §4.1);
- relationship date-warning thresholds (§7.1) and deterministic kinship path tie-breaking (§10);
- display-name rule and search matching/ordering (`mvp.md` §6, §19);
- tree focus final tie-break (`family-tree-ux.md` §6);
- Family Home actions by role, Edit Person screen, Siblings list, remove-link confirmation, merge panel, profile History section (`screens.md`);
- genealogy module technical specification (`technical/genealogy.md`);
- ADMIN restoration flow (OQ-006): `searchPersons?status=ARCHIVED`, `listArchivedPersonRelationships`, profile "Removed links" area and archived-Person notice, search "Archived people" view (additive contract change);
- `profileMediaAssetId` ignored while media is not delivered (OQ-005);
- Phase 2 delivery plan (`delivery/phase-2-core-family-graph.md`).

### 0.2 — 2026-09-25

Closed specification gaps before implementation:

- identity provider: Keycloak, just-in-time user provisioning, verified email required (ADR-005);
- French + English from the MVP; gender-aware kinship labels and kinship direction convention (`localization-and-kinship-labels.md`, ADR-006);
- invitations by email **or shareable link**, single-use, 14-day expiry, list/renew/revoke;
- membership lifecycle: leave a Family, last-ADMIN rule, linked Person released on removal;
- Memory permissions and "at least one Person" rule aligned across documents;
- fixed three-row tree layout and gender presets when adding a parent/child;
- photo limits, EXIF stripping and derivatives (ADR-007);
- personal data rights procedure (`mvp.md` §30);
- pseudonymous analytics with PostHog EU (ADR-008);
- repository layout aligned on `mbia-specs/`.
