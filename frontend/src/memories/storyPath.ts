/** An entry of the family story: a year, or the Memories without a year (OQ-064). */
export type StoryEntry = number | 'undated';

/** SCREEN-016 — What happened in {year}, or the undated Memories. */
export function familyStoryPath(familyId: string, entry: StoryEntry) {
  return `/families/${familyId}/story/${String(entry)}`;
}

/** The year of the route, a number from 1 to 9999 written without leading zero; otherwise `null`. */
export function parseStoryYear(value: string | undefined): number | null {
  return value !== undefined && /^[1-9]\d{0,3}$/.test(value) ? Number(value) : null;
}
