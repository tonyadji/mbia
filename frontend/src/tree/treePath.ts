/** SCREEN-003, optionally centred on a Person. */
export function familyTreePath(familyId: string, focusPersonId?: string) {
  const query = focusPersonId ? `?${new URLSearchParams({ focus: focusPersonId }).toString()}` : '';
  return `/families/${familyId}/tree${query}`;
}

/** Navigation state set by Add Relative from the tree, for the success message. */
export interface FamilyTreeState {
  /** `existing` when a Person already in the Family was linked, rather than a new one added. */
  relativeAdded?: { name: string; anchor: string; existing?: boolean };
}
