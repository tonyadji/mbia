import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from '../families/useFamily';
import { personQueryKey } from './usePerson';

/**
 * Archives a Person (`POST …/archive`) or restores it (`POST …/restore`), ADMIN only, from the
 * version the profile was loaded with (mvp.md §13). A linked Person cannot be archived
 * (`PERSON_ALREADY_CLAIMED`, OQ-023).
 */
export function useArchivePerson(familyId: string, personId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ archive, version }: { archive: boolean; version: number }) => {
      const params = {
        path: { familyId, personId },
        header: { 'If-Match': `"${String(version)}"` },
      };
      const { data } = archive
        ? await apiClient.POST('/families/{familyId}/persons/{personId}/archive', { params })
        : await apiClient.POST('/families/{familyId}/persons/{personId}/restore', { params });
      if (data === undefined) {
        throw new Error(`…/persons/{personId}/${archive ? 'archive' : 'restore'} returned no body`);
      }
      return data;
    },
    onSuccess: async (person) => {
      queryClient.setQueryData(personQueryKey(familyId, personId), person);
      // The tree, the searches and the Family counts change with the Person's status.
      await queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
    },
  });
}
