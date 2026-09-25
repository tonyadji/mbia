import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { familiesQueryKey } from './useMyFamilies';

export function familyQueryKey(familyId: string) {
  return [...familiesQueryKey, familyId] as const;
}

/** One Family with its home-screen statistics (`GET /families/{familyId}`). */
export function useFamily(familyId: string, { enabled = true } = {}) {
  return useQuery({
    queryKey: familyQueryKey(familyId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}', {
        params: { path: { familyId } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId} returned no body');
      }
      return data;
    },
    // 404 FAMILY_NOT_FOUND will not change by retrying.
    retry: retryUnlessClientError,
    enabled,
  });
}
