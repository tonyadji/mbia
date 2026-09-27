import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { invitationsQueryKey } from './useFamilyInvitations';

type InvitationRole = components['schemas']['InvitationRole'];

/**
 * Creates a `LINK` invitation, for a Person of the tree when `personId` is given (mvp.md §18,
 * OQ-050). The response holds the link, returned only this once.
 */
export function useCreateInvitation(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ role, personId }: { role: InvitationRole; personId: string | null }) => {
      const { data } = await apiClient.POST('/families/{familyId}/invitations', {
        params: { path: { familyId } },
        body: { channel: 'LINK', role, personId },
      });
      if (data === undefined) {
        throw new Error('POST /families/{familyId}/invitations returned no body');
      }
      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: invitationsQueryKey(familyId) });
    },
  });
}
