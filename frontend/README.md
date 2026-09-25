# frontend

React + TypeScript single-page application (Vite), mobile-first, in French and English.

Rules: [`AGENTS.md`](../AGENTS.md) §8.

## Commands

Requires Node.js ≥ 22.12 (see `.nvmrc`).

```bash
npm ci
npm run dev           # http://localhost:5173
npm run typecheck
npm run lint
npm run i18n:check    # fr and en translations have the same keys, no empty value
npm test              # Vitest
npm run build         # production build in dist/
npm run preview       # serve dist/
npm run format        # Prettier (format:check in CI)
```

## Design tokens

Colors, font (Nunito Sans, self-hosted) and the type scale (display / section / body / caption) are defined only in
`src/styles/theme.css` and used through Tailwind classes (`bg-primary`, `text-text-muted`, `text-display`, …).
Tailwind's default palette is disabled, and a test fails if a color is written anywhere else.

## Translations

Every user-facing string lives in `src/i18n/{fr,en}/<namespace>.json` (namespaces `common` and `errors` for now) and is
read with `useTranslation()` from `react-i18next`. Keys are typed from the French resources.

- Initial language: stored preference (from PR-12), otherwise the browser language (`fr*` → `fr`, anything else → `en`);
  `<html lang>` follows the active language.
- `npm run i18n:check` fails when a key or namespace exists in one language only, or when a value is empty.
- ESLint (`i18next/no-literal-string`) fails on literal text or user-facing attributes in JSX; only the brand name
  "Mbia" is allowed.
- Dates: `formatDate(date, language)` in `src/i18n/formatDate.ts` (`12 mars 1954` / `March 12, 1954`).
