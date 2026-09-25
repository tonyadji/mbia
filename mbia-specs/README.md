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
├── product/
│   ├── vision.md
│   ├── mvp.md
│   ├── domain/
│   │   └── person-relationships-collaboration.md
│   └── ux/
│       ├── family-tree-ux.md
│       ├── screens.md
│       ├── design-guidelines.md
│       └── references/
│           └── mbia-mvp-mockups.png
└── technical/
    ├── README.md
    ├── technical-specification.md
    ├── stack.md
    ├── architecture.md
    ├── data-model.md
    └── api/
        └── openapi.yaml
```

The visual mockup is a design reference. Product rules and screen behavior are defined in text and remain authoritative.
