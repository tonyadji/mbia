import type { components } from '../api/generated/schema';
import { formatDate } from '../i18n/formatDate';
import type { Language } from '../i18n/language';

type PartialDate = components['schemas']['PartialDate'];

/**
 * A partial date as the active language writes it: the full date, only the year for `YEAR_ONLY`
 * (localization-and-kinship-labels.md §1), `null` when unknown. Never invents a precision.
 */
export function formatPartialDate(date: PartialDate, language: Language): string | null {
  if (date.precision === 'EXACT' && date.date) {
    const [year = 0, month = 1, day = 1] = date.date.split('-').map(Number);
    const local = new Date(0);
    // setFullYear keeps years below 100 as written, and the local calendar day whatever the time zone.
    local.setFullYear(year, month - 1, day);
    local.setHours(0, 0, 0, 0);
    return formatDate(local, language);
  }
  if (date.precision === 'YEAR_ONLY' && date.year != null) {
    return String(date.year);
  }
  return null;
}

/** Only the year of a partial date, `null` when unknown. */
export function yearOf(date: PartialDate): string | null {
  if (date.precision === 'EXACT' && date.date) return String(Number(date.date.slice(0, 4)));
  if (date.precision === 'YEAR_ONLY' && date.year != null) return String(date.year);
  return null;
}
