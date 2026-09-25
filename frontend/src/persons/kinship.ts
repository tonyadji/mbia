import type { TFunction } from 'i18next';
import type { components } from '../api/generated/schema';

type Gender = components['schemas']['Gender'];
export type KinshipCode = components['schemas']['KinshipCode'];
export type KinshipPathStep = components['schemas']['KinshipPathStep'];

/** The label form of a Person's gender: OTHER and UNKNOWN use the neutral form. */
export type GenderForm = 'male' | 'female' | 'neutral';

export function genderForm(gender: Gender): GenderForm {
  if (gender === 'MALE') return 'male';
  if (gender === 'FEMALE') return 'female';
  return 'neutral';
}

/**
 * What a Person is to the current User, as a badge (localization-and-kinship-labels.md §3), from
 * that Person's `relationshipToCurrentUser` and `gender`. Gendered codes already carry the gender;
 * `FIRST_COUSIN` takes the form of the target Person's gender. `NONE_KNOWN` has no badge.
 */
export function kinshipLabel(
  t: TFunction<'person'>,
  code: KinshipCode,
  gender: Gender,
): string | null {
  if (code === 'NONE_KNOWN') return null;
  if (code === 'FIRST_COUSIN') return t(`kinship.label.FIRST_COUSIN.${genderForm(gender)}`);
  return t(`kinship.label.${code}`);
}

/** The name and gender of a Person of the path. */
export interface PathPerson {
  displayName: string;
  gender: Gender;
}

/**
 * One sentence per step of a kinship path, gendered by the step's `to` Person
 * (localization-and-kinship-labels.md §4), for example "Marie is Tony's mother"
 */
export function kinshipPathSentences(
  t: TFunction<'person'>,
  path: KinshipPathStep[],
  persons: Record<string, PathPerson>,
): string[] {
  const personOf = (id: string) => {
    const found = persons[id];
    if (found === undefined) throw new Error(`Person ${id} of the kinship path is missing`);
    return found;
  };
  return path.map((step) => {
    const from = personOf(step.fromPersonId);
    const to = personOf(step.toPersonId);
    return t(`kinship.step.${step.relation}.${genderForm(to.gender)}`, {
      from: from.displayName,
      to: to.displayName,
    });
  });
}
