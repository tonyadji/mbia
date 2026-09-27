import { useMutation } from '@tanstack/react-query';
import { apiClient } from '../api/client';

/**
 * Removes a member from the Family, or leaves it when the member is the current User, from the
 * version it was listed with; their linked Person is released and their contributions stay
 * (mvp.md §5). The caller refreshes what depends on it: the list for a removal, the families for
 * a departure.
 */
export function useRemoveMember(familyId: string) {
  return useMutation({
    mutationFn: async ({ memberId, version }: { memberId: string; version: number }) => {
      await apiClient.DELETE('/families/{familyId}/members/{memberId}', {
        params: {
          path: { familyId, memberId },
          header: { 'If-Match': `"${String(version)}"` },
        },
      });
    },
  });
}
