import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from '../families/useFamily';
import type { CreateRelationshipRequest } from './relatives';

/**
 * Creates an explicit relationship (`POST /families/{familyId}/relationships`). Without
 * `confirmWarnings`, probable date inconsistencies answer 422
 * `RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED` with `details.warnings`.
 */
export function useCreateRelationship(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateRelationshipRequest) => {
      const { data } = await apiClient.POST('/families/{familyId}/relationships', {
        params: { path: { familyId } },
        body,
      });
      if (data === undefined) {
        throw new Error('POST /families/{familyId}/relationships returned no body');
      }
      return data;
    },
    onSuccess: async () => {
      // The Persons of the Family now have new relatives.
      await queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
    },
  });
}
