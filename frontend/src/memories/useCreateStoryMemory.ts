import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { familyQueryKey } from '../families/useFamily';
import { familiesQueryKey } from '../families/useMyFamilies';
import { familyMemoriesQueryKey } from './useFamilyMemories';
import { personMemoriesQueryKey } from './usePersonMemories';

export type CreateStoryMemoryRequest = components['schemas']['CreateStoryMemoryRequest'];
export type Memory = components['schemas']['MemoryResponse'];

/** Creates a written story linked to Persons (`POST /families/{familyId}/memories/stories`). */
export function useCreateStoryMemory(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateStoryMemoryRequest) => {
      const { data } = await apiClient.POST('/families/{familyId}/memories/stories', {
        params: { path: { familyId } },
        body,
      });
      if (data === undefined) {
        throw new Error('POST /families/{familyId}/memories/stories returned no body');
      }
      return data;
    },
    onSuccess: (memory) => {
      // The Memory count and list of the Family changed, and the Memories of each related Person.
      void queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId), exact: true });
      void queryClient.invalidateQueries({ queryKey: familiesQueryKey, exact: true });
      void queryClient.invalidateQueries({ queryKey: familyMemoriesQueryKey(familyId) });
      for (const person of memory.relatedPersons) {
        void queryClient.invalidateQueries({
          queryKey: personMemoriesQueryKey(familyId, person.id),
        });
      }
    },
  });
}
