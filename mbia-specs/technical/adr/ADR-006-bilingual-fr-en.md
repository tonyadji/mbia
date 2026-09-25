# ADR-006 — French + English from the MVP

**Status:** Accepted

## Context

Mbia first targets Cameroonian families, in Cameroon and abroad. Cameroon is officially bilingual, and diaspora members may prefer English.

## Decision

Ship French (default) and English in the MVP. Product rules are in `product/ux/localization-and-kinship-labels.md`.

- Frontend: `i18next` + `react-i18next`, one namespace per feature, JSON resource files under `frontend/src/i18n/{fr,en}/`.
- Backend: the API returns stable codes, never translated sentences. Only emails are rendered server-side, from localized templates.
- CI fails when a translation key exists in one language and not the other.

## Consequences

- Every UI change requires both translations.
- Kinship labels are gender-aware in French and are defined once in the spec so that no label is invented.
