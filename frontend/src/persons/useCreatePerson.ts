import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { familyQueryKey } from '../families/useFamily';
import { familiesQueryKey } from '../families/useMyFamilies';

export type CreatePersonRequest = components['schemas']['CreatePersonRequest'];

/** Creates a Person in a Family (`POST /families/{familyId}/persons`). */
export function useCreatePerson(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreatePersonRequest) => {
      const { data } = await apiClient.POST('/families/{familyId}/persons', {
        params: { path: { familyId } },
        body,
      });
      if (data === undefined) {
        throw new Error('POST /families/{familyId}/persons returned no body');
      }
      return data;
    },
    onSuccess: async () => {
      // The Person count of the Family changed.
      await queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId), exact: true });
      void queryClient.invalidateQueries({ queryKey: familiesQueryKey, exact: true });
    },
  });
}
