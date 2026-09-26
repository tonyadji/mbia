import type { TFunction } from 'i18next';
import type { components } from '../api/generated/schema';
import type { RelativeChoiceGroup } from './AddRelativeMenu';

type Gender = components['schemas']['Gender'];
export type CreateRelationshipRequest = components['schemas']['CreateRelationshipRequest'];
export type RelationshipWarning = components['schemas']['RelationshipWarning'];

/** The human choices of the "Add…" menu (family-tree-ux.md §9.1). */
export const RELATIONS = [
  'FATHER',
  'MOTHER',
  'PARENT',
  'SON',
  'DAUGHTER',
  'CHILD',
  'PARTNER',
] as const;
export type Relation = (typeof RELATIONS)[number];

export const PARENT_RELATIONS = ['FATHER', 'MOTHER', 'PARENT'] as const satisfies Relation[];
export const CHILD_RELATIONS = ['SON', 'DAUGHTER', 'CHILD'] as const satisfies Relation[];

/** Where Add Relative goes back to once done. */
export type RelativeOrigin = 'home' | 'profile' | 'tree';

export function isRelativeOrigin(value: string | null): value is RelativeOrigin {
  return value === 'home' || value === 'profile' || value === 'tree';
}

export function isRelation(value: string | null): value is Relation {
  return RELATIONS.includes(value as Relation);
}

export function isParentRelation(relation: Relation) {
  return (PARENT_RELATIONS as readonly Relation[]).includes(relation);
}

/** The gender preset by a choice; only a default, still editable (§9.1). */
export function presetGender(relation: Relation): Gender {
  if (relation === 'FATHER' || relation === 'SON') return 'MALE';
  if (relation === 'MOTHER' || relation === 'DAUGHTER') return 'FEMALE';
  return 'UNKNOWN';
}

/**
 * The technical relationship derived from the human choice (family-tree-ux.md §9): a new parent
 * is the source of `PARENT_OF`, a new child its target; partners are symmetric.
 */
export function relationshipBetween(
  relation: Relation,
  anchorId: string,
  relativeId: string,
  confirmWarnings: boolean,
): CreateRelationshipRequest {
  if (relation === 'PARTNER') {
    return {
      type: 'PARTNER_OF',
      sourcePersonId: anchorId,
      targetPersonId: relativeId,
      confirmWarnings,
    };
  }
  return isParentRelation(relation)
    ? { type: 'PARENT_OF', sourcePersonId: relativeId, targetPersonId: anchorId, confirmWarnings }
    : { type: 'PARENT_OF', sourcePersonId: anchorId, targetPersonId: relativeId, confirmWarnings };
}

/** SCREEN-004 in "Relative of a Person" mode. */
export function addRelativePath(
  familyId: string,
  anchorId: string,
  relation: Relation,
  from: RelativeOrigin,
) {
  const query = new URLSearchParams({ relativeOf: anchorId, relation, from });
  return `/families/${familyId}/persons/new?${query.toString()}`;
}

/**
 * The "Add…" choices from a Person (family-tree-ux.md §9.1): parents and children with their
 * gendered shortcuts, then a partner. `only` keeps one kind, for the tree's add slots.
 */
export function relativeChoiceGroups(
  t: TFunction<'person'>,
  familyId: string,
  anchorId: string,
  from: RelativeOrigin,
  only?: 'parent' | 'child' | 'partner',
): RelativeChoiceGroup[] {
  const choice = (relation: Relation) => ({
    label: t(`relative.choices.${relation}`),
    to: addRelativePath(familyId, anchorId, relation, from),
  });
  const groups = {
    parent: { heading: t('relative.parents'), choices: PARENT_RELATIONS.map(choice) },
    child: { heading: t('relative.children'), choices: CHILD_RELATIONS.map(choice) },
    partner: { choices: [choice('PARTNER')] },
  };
  return only ? [groups[only]] : [groups.parent, groups.child, groups.partner];
}
