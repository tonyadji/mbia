import { useInfiniteQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

const PAGE_SIZE = 20;

export function familyMemoriesQueryKey(familyId: string) {
  return [...familyQueryKey(familyId), 'memories'] as const;
}

/**
 * The ACTIVE Memories of a Family, most recently added first, 20 at a time
 * (`GET /families/{familyId}/memories`, OQ-034). No type filter: only stories exist
 * (phase-3-family-memories.md §3.1).
 */
export function useFamilyMemories(familyId: string, { enabled = true } = {}) {
  return useInfiniteQuery({
    queryKey: familyMemoriesQueryKey(familyId),
    queryFn: async ({ pageParam }) => {
      const { data } = await apiClient.GET('/families/{familyId}/memories', {
        params: { path: { familyId }, query: { page: pageParam, size: PAGE_SIZE } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/memories returned no body');
      }
      return data;
    },
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.page.page + 1 < last.page.totalPages ? last.page.page + 1 : undefined,
    retry: retryUnlessClientError,
    enabled,
  });
}
