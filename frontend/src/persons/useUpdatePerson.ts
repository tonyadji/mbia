import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { personHistoryQueryKey } from './usePersonHistory';
import { personQueryKey } from './usePerson';
import { personSearchQueryKey } from './usePersonSearch';

export type UpdatePersonRequest = components['schemas']['UpdatePersonRequest'];

/**
 * Changes a Person (`PATCH /families/{familyId}/persons/{personId}`) from the version the form was
 * loaded with: a newer version on the server answers 409 `CONCURRENT_MODIFICATION`.
 */
export function useUpdatePerson(familyId: string, personId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ version, body }: { version: number; body: UpdatePersonRequest }) => {
      const { data } = await apiClient.PATCH('/families/{familyId}/persons/{personId}', {
        params: { path: { familyId, personId }, header: { 'If-Match': `"${String(version)}"` } },
        body,
      });
      if (data === undefined) {
        throw new Error('PATCH /families/{familyId}/persons/{personId} returned no body');
      }
      return data;
    },
    onSuccess: (person) => {
      queryClient.setQueryData(personQueryKey(familyId, personId), person);
      // Names and dates shown in search results may have changed.
      void queryClient.invalidateQueries({ queryKey: personSearchQueryKey(familyId) });
      void queryClient.invalidateQueries({ queryKey: personHistoryQueryKey(familyId, personId) });
    },
  });
}
