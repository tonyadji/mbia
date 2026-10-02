import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export type Person = components['schemas']['PersonResponse'];

export function personQueryKey(familyId: string, personId: string) {
  return [...familyQueryKey(familyId), 'persons', personId] as const;
}

/** One Person profile (`GET /families/{familyId}/persons/{personId}`). */
export function usePerson(familyId: string, personId: string, { enabled = true } = {}) {
  return useQuery({
    queryKey: personQueryKey(familyId, personId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/persons/{personId}', {
        params: { path: { familyId, personId } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/persons/{personId} returned no body');
      }
      return data;
    },
    // 404 PERSON_NOT_FOUND / FAMILY_NOT_FOUND will not change by retrying.
    retry: retryUnlessClientError,
    enabled,
  });
}
