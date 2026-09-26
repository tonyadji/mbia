import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { personMemoriesQueryKey } from './usePersonMemories';

/**
 * The number of ACTIVE Memories of a Person, read from a one-item page of
 * `GET …/persons/{personId}/memories` (OQ-038): one call, only where the count is shown.
 */
export function usePersonMemoryCount(familyId: string, personId: string) {
  return useQuery({
    queryKey: [...personMemoriesQueryKey(familyId, personId), 'count'],
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/persons/{personId}/memories', {
        params: { path: { familyId, personId }, query: { page: 0, size: 1 } },
      });
      if (data === undefined) {
        throw new Error('GET …/persons/{personId}/memories returned no body');
      }
      return data.page.totalElements;
    },
    retry: retryUnlessClientError,
  });
}
