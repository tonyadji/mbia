import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

/** How `to` is related to `from`, with the path explaining it (`GET /families/{familyId}/kinship`). */
export function useKinship(familyId: string, from: string, to: string, { enabled = true } = {}) {
  return useQuery({
    queryKey: [...familyQueryKey(familyId), 'kinship', from, to] as const,
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/kinship', {
        params: { path: { familyId }, query: { from, to } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/kinship returned no body');
      }
      return data;
    },
    retry: retryUnlessClientError,
    enabled,
  });
}
