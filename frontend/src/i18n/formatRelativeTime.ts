import type { Language } from './language';

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
];

/**
 * How long ago `date` was, in the largest whole unit: `il y a 2 heures` / `2 hours ago`, `hier` /
 * `yesterday`; under a minute, `maintenant` / `now`.
 */
export function formatRelativeTime(date: Date, language: Language, now = new Date()): string {
  const format = new Intl.RelativeTimeFormat(language, { numeric: 'auto' });
  const seconds = Math.max(0, Math.round((now.getTime() - date.getTime()) / 1000));
  for (const [unit, length] of UNITS) {
    if (seconds >= length) return format.format(-Math.floor(seconds / length), unit);
  }
  return format.format(0, 'second');
}
