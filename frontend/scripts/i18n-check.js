// Checks that French and English translations have the same namespaces and keys, with no empty value (ADR-006).
// Usage: node scripts/i18n-check.js [translations directory, default src/i18n]
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const LANGUAGES = ['fr', 'en'];

function namespacesOf(dir, language) {
  const languageDir = join(dir, language);
  if (!existsSync(languageDir)) return [];
  return readdirSync(languageDir)
    .filter((file) => file.endsWith('.json'))
    .map((file) => file.slice(0, -'.json'.length));
}

function flatten(value, prefix, entries, problems, location) {
  for (const [key, child] of Object.entries(value)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof child === 'string') {
      if (child.trim() === '') problems.push(`${location}: "${path}" is empty`);
      entries.add(path);
    } else if (child !== null && typeof child === 'object' && !Array.isArray(child)) {
      flatten(child, path, entries, problems, location);
    } else {
      problems.push(`${location}: "${path}" must be a string or an object`);
      entries.add(path);
    }
  }
  return entries;
}

/** Returns the list of problems found in `dir`; an empty list means the translations are consistent. */
export function checkTranslations(dir) {
  const problems = [];
  const namespaces = new Set(LANGUAGES.flatMap((language) => namespacesOf(dir, language)));

  if (namespaces.size === 0) problems.push(`${dir}: no translation file found`);

  for (const namespace of [...namespaces].sort()) {
    const keysByLanguage = new Map();
    for (const language of LANGUAGES) {
      const location = `${language}/${namespace}.json`;
      const file = join(dir, location);
      if (!existsSync(file)) {
        problems.push(`${location}: missing file`);
        continue;
      }
      let content;
      try {
        content = JSON.parse(readFileSync(file, 'utf8'));
      } catch (error) {
        problems.push(`${location}: invalid JSON (${error.message})`);
        continue;
      }
      if (content === null || typeof content !== 'object' || Array.isArray(content)) {
        problems.push(`${location}: must contain a JSON object`);
        continue;
      }
      keysByLanguage.set(language, flatten(content, '', new Set(), problems, location));
    }

    for (const [language, keys] of keysByLanguage) {
      for (const [otherLanguage, otherKeys] of keysByLanguage) {
        if (language === otherLanguage) continue;
        for (const key of keys) {
          if (!otherKeys.has(key)) {
            problems.push(
              `${otherLanguage}/${namespace}.json: missing key "${key}" (present in ${language})`,
            );
          }
        }
      }
    }
  }

  return problems;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const dir = resolve(process.argv[2] ?? join(import.meta.dirname, '..', 'src', 'i18n'));
  const problems = checkTranslations(dir);
  if (problems.length > 0) {
    console.error(`i18n check failed (${problems.length} problem(s)):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }
  console.log(`i18n check passed: ${LANGUAGES.join(' and ')} translations are consistent.`);
}
