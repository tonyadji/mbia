import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export type Invitation = components['schemas']['InvitationResponse'];
export type CreatedInvitation = components['schemas']['CreatedInvitationResponse'];

export function invitationsQueryKey(familyId: string) {
  return [...familyQueryKey(familyId), 'invitations'] as const;
}

/**
 * The Family's PENDING invitations (`GET /families/{familyId}/invitations`), ADMIN only (OQ-051).
 * An expired invitation is no longer PENDING, so its Person can be invited again.
 */
export function useFamilyInvitations(familyId: string, { enabled = true } = {}) {
  return useQuery({
    queryKey: invitationsQueryKey(familyId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/invitations', {
        params: { path: { familyId } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/invitations returned no body');
      }
      return data;
    },
    retry: retryUnlessClientError,
    enabled,
  });
}
