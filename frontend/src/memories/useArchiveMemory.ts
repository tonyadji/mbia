import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from '../families/useFamily';
import { familiesQueryKey } from '../families/useMyFamilies';
import { memoryQueryKey } from './useMemory';

/**
 * Archives a Memory (`DELETE /families/{familyId}/memories/{memoryId}`), from the version it was
 * shown with. It then disappears for the whole Family (mvp.md §17).
 */
export function useArchiveMemory(familyId: string, memoryId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ version }: { version: number }) => {
      // A refusal (403, 404, 409) is thrown as an `ApiError` by the client.
      await apiClient.DELETE('/families/{familyId}/memories/{memoryId}', {
        params: { path: { familyId, memoryId }, header: { 'If-Match': `"${String(version)}"` } },
      });
    },
    onSuccess: () => {
      // No longer readable: forget it rather than refetch a 404.
      queryClient.removeQueries({ queryKey: memoryQueryKey(familyId, memoryId), exact: true });
      // The lists, the Persons' Memories and the Family counts change.
      void queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
      void queryClient.invalidateQueries({ queryKey: familiesQueryKey, exact: true });
    },
  });
}
