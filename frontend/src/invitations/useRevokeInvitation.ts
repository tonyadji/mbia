import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { invitationsQueryKey } from './useFamilyInvitations';

/**
 * Revokes an invitation, finally: its link stops working at once (mvp.md §18, OQ-057), from the
 * version it was listed with.
 */
export function useRevokeInvitation(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ invitationId, version }: { invitationId: string; version: number }) => {
      await apiClient.POST('/families/{familyId}/invitations/{invitationId}/revoke', {
        params: {
          path: { familyId, invitationId },
          header: { 'If-Match': `"${String(version)}"` },
        },
      });
    },
    // Also after a refusal: the invitation may have been used or revoked elsewhere.
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: invitationsQueryKey(familyId) });
    },
  });
}
