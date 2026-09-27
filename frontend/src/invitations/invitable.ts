import type { Person } from '../persons/usePerson';

/** Why a Person cannot be invited (mvp.md §18, OQ-050). */
export type InviteBlocker = 'inactive' | 'deceased' | 'linked';

/**
 * An invitation may carry an ACTIVE, living Person linked to no User; the backend refuses any other
 * one (404 `PERSON_NOT_FOUND`, 400 `VALIDATION_FAILED`, 409 `PERSON_ALREADY_CLAIMED`).
 */
export function inviteBlocker(
  person: Pick<Person, 'status' | 'isDeceased' | 'linkedUserId'>,
): InviteBlocker | null {
  if (person.status !== 'ACTIVE') return 'inactive';
  if (person.isDeceased) return 'deceased';
  if (person.linkedUserId != null) return 'linked';
  return null;
}
