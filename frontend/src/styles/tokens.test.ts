import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const srcDir = join(import.meta.dirname, '..');
const tokensFile = join(srcDir, 'styles', 'theme.css');

const colorLiteral = /#[0-9a-f]{3,8}\b|\b(?:rgba?|hsla?|oklch|oklab)\(/i;
const arbitraryColorClass = /\b(?:bg|text|border|ring|fill|stroke|from|to|via|outline)-\[/;

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === 'generated' ? [] : sourceFiles(path);
    return /\.(tsx?|css)$/.test(entry.name) && !entry.name.includes('.test.') ? [path] : [];
  });
}

describe('design tokens', () => {
  it('are the only place where colors are written', () => {
    const offenders = sourceFiles(srcDir)
      .filter((file) => file !== tokensFile)
      .filter((file) => {
        const content = readFileSync(file, 'utf8');
        return colorLiteral.test(content) || arbitraryColorClass.test(content);
      })
      .map((file) => relative(srcDir, file));

    expect(offenders).toEqual([]);
  });

  it('define the primary, accent, background and text colors', () => {
    const tokens = readFileSync(tokensFile, 'utf8');

    for (const token of ['primary', 'accent', 'background', 'text', 'text-muted']) {
      expect(tokens).toMatch(new RegExp(`--color-${token}:\\s*#[0-9a-f]{6};`, 'i'));
    }
  });
});
