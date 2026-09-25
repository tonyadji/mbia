import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from '../families/useFamily';
import { personQueryKey } from './usePerson';

/**
 * Links the current User to a Person as "me" (`POST …/claim`) or releases the link
 * (`DELETE …/claim`), from the version the profile was loaded with.
 */
export function useClaimPerson(familyId: string, personId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ claim, version }: { claim: boolean; version: number }) => {
      const params = {
        path: { familyId, personId },
        header: { 'If-Match': `"${String(version)}"` },
      };
      const { data } = claim
        ? await apiClient.POST('/families/{familyId}/persons/{personId}/claim', { params })
        : await apiClient.DELETE('/families/{familyId}/persons/{personId}/claim', { params });
      if (data === undefined) {
        throw new Error('…/persons/{personId}/claim returned no body');
      }
      return data;
    },
    onSuccess: async (person) => {
      queryClient.setQueryData(personQueryKey(familyId, personId), person);
      // What every other Person is to the current User changes with their linked Person.
      await queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
    },
  });
}
