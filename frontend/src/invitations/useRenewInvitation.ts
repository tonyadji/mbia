import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { invitationsQueryKey } from './useFamilyInvitations';

/**
 * Renews an invitation (new link, new 14-day expiry; the previous link stops working), from the
 * version it was listed with (mvp.md §18).
 */
export function useRenewInvitation(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ invitationId, version }: { invitationId: string; version: number }) => {
      const { data } = await apiClient.POST(
        '/families/{familyId}/invitations/{invitationId}/renew',
        {
          params: {
            path: { familyId, invitationId },
            header: { 'If-Match': `"${String(version)}"` },
          },
        },
      );
      if (data === undefined) {
        throw new Error('POST …/invitations/{invitationId}/renew returned no body');
      }
      return data;
    },
    // Also after a refusal: the invitation may have been used, revoked or renewed elsewhere.
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: invitationsQueryKey(familyId) });
    },
  });
}
