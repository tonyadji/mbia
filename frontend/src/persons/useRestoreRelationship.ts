import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from '../families/useFamily';

/**
 * Restores a removed relationship (`POST …/relationships/{relationshipId}/restore`, ADMIN only),
 * from the version of the removed links list. The server re-runs every current block; date
 * warnings come back in `warnings` without blocking (OQ-021).
 */
export function useRestoreRelationship(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      relationshipId,
      version,
    }: {
      relationshipId: string;
      version: number;
    }) => {
      const { data } = await apiClient.POST(
        '/families/{familyId}/relationships/{relationshipId}/restore',
        {
          params: {
            path: { familyId, relationshipId },
            header: { 'If-Match': `"${String(version)}"` },
          },
        },
      );
      if (data === undefined) {
        throw new Error('POST …/relationships/{relationshipId}/restore returned no body');
      }
      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
    },
  });
}
