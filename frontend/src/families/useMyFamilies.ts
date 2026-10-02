import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';

export const familiesQueryKey = ['families'] as const;

/** The Families where the current User is an ACTIVE member (`GET /families`). */
export function useMyFamilies() {
  return useQuery({
    queryKey: familiesQueryKey,
    queryFn: async () => {
      const { data } = await apiClient.GET('/families');
      if (data === undefined) {
        throw new Error('GET /families returned no body');
      }
      return data;
    },
    retry: retryUnlessClientError,
  });
}
