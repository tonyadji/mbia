import { readFileSync } from 'node:fs';

export type Language = 'fr' | 'en';

/**
 * The application's own translations (src/i18n), so tests expect exactly what users read and never duplicate a
 * label. `key` is `namespace:path.to.key`; `{{name}}` placeholders are replaced from `values`.
 */
export function t(language: Language, key: string, values: Record<string, string> = {}): string {
  const [namespace = '', path = ''] = key.split(':');
  const file = new URL(`../../src/i18n/${language}/${namespace}.json`, import.meta.url);
  let node: unknown = JSON.parse(readFileSync(file, 'utf8'));
  for (const part of path.split('.')) {
    node = (node as Record<string, unknown>)[part];
  }
  if (typeof node !== 'string') throw new Error(`No translation ${language}/${key}`);
  return node.replace(/\{\{(\w+)\}\}/g, (_, name: string) => values[name] ?? '');
}
