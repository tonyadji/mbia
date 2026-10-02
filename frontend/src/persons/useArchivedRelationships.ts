import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export type ArchivedRelationship = components['schemas']['ArchivedRelationshipResponse'];

/**
 * The removed relationships of a Person, most recent first, each with the other Person
 * (`GET …/persons/{personId}/archived-relationships`, ADMIN only).
 */
export function useArchivedRelationships(
  familyId: string,
  personId: string,
  { enabled = true } = {},
) {
  return useQuery({
    queryKey: [...familyQueryKey(familyId), 'persons', personId, 'archived-relationships'] as const,
    queryFn: async () => {
      const { data } = await apiClient.GET(
        '/families/{familyId}/persons/{personId}/archived-relationships',
        { params: { path: { familyId, personId } } },
      );
      if (data === undefined) {
        throw new Error('GET …/persons/{personId}/archived-relationships returned no body');
      }
      return data;
    },
    retry: retryUnlessClientError,
    enabled,
  });
}
