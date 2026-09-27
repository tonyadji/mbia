import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { membersQueryKey } from './useFamilyMembers';

type InvitationRole = components['schemas']['InvitationRole'];

/**
 * Changes a member's role between CONTRIBUTOR and VIEWER, ADMIN only, from the version it was
 * listed with (mvp.md §5).
 */
export function useUpdateMemberRole(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      memberId,
      version,
      role,
    }: {
      memberId: string;
      version: number;
      role: InvitationRole;
    }) => {
      const { data } = await apiClient.PATCH('/families/{familyId}/members/{memberId}', {
        params: {
          path: { familyId, memberId },
          header: { 'If-Match': `"${String(version)}"` },
        },
        body: { role },
      });
      if (data === undefined) {
        throw new Error('PATCH …/members/{memberId} returned no body');
      }
      return data;
    },
    // Also after a refusal: the member may have changed or left in the meantime.
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: membersQueryKey(familyId) });
    },
  });
}
