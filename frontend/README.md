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
npm test              # Vitest
npm run build         # production build in dist/
npm run preview       # serve dist/
npm run format        # Prettier (format:check in CI)
```

## Design tokens

Colors, font (Nunito Sans, self-hosted) and the type scale (display / section / body / caption) are defined only in
`src/styles/theme.css` and used through Tailwind classes (`bg-primary`, `text-text-muted`, `text-display`, …).
Tailwind's default palette is disabled, and a test fails if a color is written anywhere else.
