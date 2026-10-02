import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from '../families/useFamily';

/**
 * Removes a relationship (`DELETE /families/{familyId}/relationships/{relationshipId}`), from the
 * version the tree was loaded with. The relationship is archived, never deleted.
 */
export function useArchiveRelationship(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      relationshipId,
      version,
    }: {
      relationshipId: string;
      version: number;
    }) => {
      await apiClient.DELETE('/families/{familyId}/relationships/{relationshipId}', {
        params: {
          path: { familyId, relationshipId },
          header: { 'If-Match': `"${String(version)}"` },
        },
      });
    },
    onSuccess: async () => {
      // Tree, profiles, kinship and removed links of the Family change with the graph.
      await queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
    },
  });
}
