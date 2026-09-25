import type { Language } from './language';

/** Formats a calendar date for the active language: `12 mars 1954` / `March 12, 1954`. */
export function formatDate(date: Date, language: Language): string {
  return new Intl.DateTimeFormat(language, { dateStyle: 'long' }).format(date);
}
