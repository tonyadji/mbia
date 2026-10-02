import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from '../families/useFamily';
import { personQueryKey } from './usePerson';

/**
 * Merges the duplicate `sourceId` into `targetId` (`POST …/persons/{sourceId}/merge`), ADMIN only,
 * from the versions of both profiles the ADMIN has seen (mvp.md §12, genealogy.md §12). The answer
 * is the kept Person.
 */
export function useMergePerson(familyId: string, sourceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: {
      targetPersonId: string;
      sourceVersion: number;
      targetVersion: number;
    }) => {
      const { data } = await apiClient.POST('/families/{familyId}/persons/{personId}/merge', {
        params: { path: { familyId, personId: sourceId } },
        body,
      });
      if (data === undefined) {
        throw new Error('…/persons/{personId}/merge returned no body');
      }
      return data;
    },
    onSuccess: async (kept) => {
      queryClient.setQueryData(personQueryKey(familyId, kept.id), kept);
      // Both profiles, the tree, the searches and the Family counts change.
      await queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
    },
  });
}
