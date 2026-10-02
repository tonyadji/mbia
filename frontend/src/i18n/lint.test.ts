// @vitest-environment node
import { ESLint } from 'eslint';
import { join } from 'node:path';

const frontendDir = join(import.meta.dirname, '..', '..');
// Linting as an existing file keeps the type-aware configuration working.
const filePath = join(frontendDir, 'src', 'pages', 'WelcomePage.tsx');

async function literalStringErrors(jsx: string) {
  const eslint = new ESLint({ cwd: frontendDir });
  const [result] = await eslint.lintText(`export function Sample() {\n  return ${jsx};\n}\n`, {
    filePath,
  });
  return (result?.messages ?? []).filter(
    (message) => message.ruleId === 'i18next/no-literal-string',
  );
}

describe('lint rule against literal strings in JSX', () => {
  it('fails on a literal text in JSX', async () => {
    expect(await literalStringErrors('<p>Bienvenue</p>')).toHaveLength(1);
  });

  it('fails on a literal user-facing attribute', async () => {
    expect(await literalStringErrors('<img alt="Photo de famille" src="/a.png" />')).toHaveLength(
      1,
    );
  });

  it('allows the brand name', async () => {
    expect(await literalStringErrors('<h1>Mbia</h1>')).toEqual([]);
  });
}, 60_000);
