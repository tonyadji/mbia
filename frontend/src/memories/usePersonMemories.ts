import { useInfiniteQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { personQueryKey } from '../persons/usePerson';

const PAGE_SIZE = 20;

export function personMemoriesQueryKey(familyId: string, personId: string) {
  return [...personQueryKey(familyId, personId), 'memories'] as const;
}

/**
 * The ACTIVE Memories of a Person, most recently added first, 20 at a time
 * (`GET …/persons/{personId}/memories`, OQ-034).
 */
export function usePersonMemories(familyId: string, personId: string) {
  return useInfiniteQuery({
    queryKey: personMemoriesQueryKey(familyId, personId),
    queryFn: async ({ pageParam }) => {
      const { data } = await apiClient.GET('/families/{familyId}/persons/{personId}/memories', {
        params: { path: { familyId, personId }, query: { page: pageParam, size: PAGE_SIZE } },
      });
      if (data === undefined) {
        throw new Error('GET …/persons/{personId}/memories returned no body');
      }
      return data;
    },
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.page.page + 1 < last.page.totalPages ? last.page.page + 1 : undefined,
    retry: retryUnlessClientError,
  });
}
