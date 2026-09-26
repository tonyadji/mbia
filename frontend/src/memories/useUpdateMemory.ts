import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { familyMemoriesQueryKey } from './useFamilyMemories';
import { memoryQueryKey } from './useMemory';
import { personMemoriesQueryKey } from './usePersonMemories';

export type UpdateMemoryRequest = components['schemas']['UpdateMemoryRequest'];
type Memory = components['schemas']['MemoryResponse'];

/**
 * Changes a story (`PATCH /families/{familyId}/memories/{memoryId}`) from the version the form was
 * loaded with: a newer version on the server answers 409 `CONCURRENT_MODIFICATION`.
 */
export function useUpdateMemory(familyId: string, memoryId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ version, body }: { version: number; body: UpdateMemoryRequest }) => {
      const { data } = await apiClient.PATCH('/families/{familyId}/memories/{memoryId}', {
        params: { path: { familyId, memoryId }, header: { 'If-Match': `"${String(version)}"` } },
        body,
      });
      if (data === undefined) {
        throw new Error('PATCH /families/{familyId}/memories/{memoryId} returned no body');
      }
      return data;
    },
    onSuccess: (memory) => {
      const before = queryClient.getQueryData<Memory>(memoryQueryKey(familyId, memoryId));
      queryClient.setQueryData(memoryQueryKey(familyId, memoryId), memory);
      // The lists show the title, and the Persons it was on or is now on changed.
      // Exact: the Memory's own query shares this prefix and was just set.
      void queryClient.invalidateQueries({
        queryKey: familyMemoriesQueryKey(familyId),
        exact: true,
      });
      const persons = new Set(
        [...(before?.relatedPersons ?? []), ...memory.relatedPersons].map((p) => p.id),
      );
      for (const personId of persons) {
        void queryClient.invalidateQueries({
          queryKey: personMemoriesQueryKey(familyId, personId),
        });
      }
    },
  });
}
