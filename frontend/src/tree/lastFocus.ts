/**
 * The last focused Person of each Family, remembered on this device (family-tree-ux.md §6). Browser
 * storage may be missing or blocked: the tree then simply lets the server choose the focus.
 */
const key = (familyId: string) => `mbia.tree.focus.${familyId}`;

export function readLastFocus(familyId: string): string | undefined {
  try {
    return window.localStorage.getItem(key(familyId)) ?? undefined;
  } catch {
    return undefined;
  }
}

export function writeLastFocus(familyId: string, personId: string) {
  try {
    window.localStorage.setItem(key(familyId), personId);
  } catch {
    // Not remembered on this device.
  }
}

export function clearLastFocus(familyId: string) {
  try {
    window.localStorage.removeItem(key(familyId));
  } catch {
    // Nothing to clear.
  }
}
