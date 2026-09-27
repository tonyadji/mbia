const KEY = 'mbia.pendingInvitation';

/**
 * The raw token of the invitation being joined, kept in the browser from the first visit until it
 * is accepted or no longer valid, so that signing up, verifying the email in another tab or signing
 * in always leads back to it (mvp.md §18, OQ-050). Storage may be unavailable: then nothing is kept.
 */
export function rememberInvitation(token: string) {
  try {
    localStorage.setItem(KEY, token);
  } catch {
    // Private mode or blocked storage: the invitation link still works in this tab.
  }
}

export function pendingInvitation(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

/** Removes the token once the invitation is accepted or no longer valid (plan §3.1). */
export function forgetInvitation(token: string) {
  try {
    if (localStorage.getItem(KEY) === token) localStorage.removeItem(KEY);
  } catch {
    // Nothing was kept.
  }
}

export function invitationPath(token: string, { join = false } = {}) {
  return `/invitations/${encodeURIComponent(token)}${join ? '?join=1' : ''}`;
}
