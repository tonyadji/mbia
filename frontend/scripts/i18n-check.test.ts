// @vitest-environment node
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { checkTranslations } from './i18n-check.js';

const script = join(import.meta.dirname, 'i18n-check.js');
let dir: string;

function write(language: string, namespace: string, content: unknown) {
  mkdirSync(join(dir, language), { recursive: true });
  writeFileSync(join(dir, language, `${namespace}.json`), JSON.stringify(content));
}

function runCli(): { status: number; output: string } {
  try {
    const output = execFileSync(process.execPath, [script, dir], {
      encoding: 'utf8',
      stdio: 'pipe',
    });
    return { status: 0, output };
  } catch (error) {
    const failure = error as { status: number; stderr: string };
    return { status: failure.status, output: failure.stderr };
  }
}

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'mbia-i18n-'));
  write('fr', 'common', { home: { title: 'Accueil' }, save: 'Enregistrer' });
  write('en', 'common', { home: { title: 'Home' }, save: 'Save' });
});

afterEach(() => {
  rmSync(dir, { recursive: true, force: true });
});

describe('i18n:check', () => {
  it('passes when both languages have the same keys and no empty value', () => {
    expect(checkTranslations(dir)).toEqual([]);
    expect(runCli().status).toBe(0);
  });

  it('fails when a key exists in one language and not the other', () => {
    write('en', 'common', { home: {}, save: 'Save' });

    expect(checkTranslations(dir)).toEqual([
      'en/common.json: missing key "home.title" (present in fr)',
    ]);
    const result = runCli();
    expect(result.status).toBe(1);
    expect(result.output).toContain('missing key "home.title"');
  });

  it('fails when a key exists only in English', () => {
    write('en', 'common', { home: { title: 'Home' }, save: 'Save', cancel: 'Cancel' });

    expect(checkTranslations(dir)).toEqual([
      'fr/common.json: missing key "cancel" (present in en)',
    ]);
  });

  it('fails when a value is empty', () => {
    write('fr', 'common', { home: { title: '  ' }, save: 'Enregistrer' });

    expect(checkTranslations(dir)).toEqual(['fr/common.json: "home.title" is empty']);
    expect(runCli().status).toBe(1);
  });

  it('fails when a value is neither a string nor an object', () => {
    write('en', 'common', { home: { title: 'Home' }, save: 42 });

    expect(checkTranslations(dir)).toEqual([
      'en/common.json: "save" must be a string or an object',
    ]);
  });

  it('fails when a namespace exists in one language only', () => {
    write('fr', 'errors', { unexpected: 'Erreur' });

    expect(checkTranslations(dir)).toEqual(['en/errors.json: missing file']);
    expect(runCli().status).toBe(1);
  });

  it('passes on the real translation files', () => {
    expect(checkTranslations(join(import.meta.dirname, '..', 'src', 'i18n'))).toEqual([]);
  });
});
