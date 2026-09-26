import { useInfiniteQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { personQueryKey } from './usePerson';

export type PersonHistoryEntry = components['schemas']['PersonHistoryEntry'];

const PAGE_SIZE = 20;

export function personHistoryQueryKey(familyId: string, personId: string) {
  return [...personQueryKey(familyId, personId), 'history'] as const;
}

/**
 * The presentation-safe change history of a Person, most recent first, page by page
 * (`GET …/persons/{personId}/history`, data-model.md §18).
 */
export function usePersonHistory(familyId: string, personId: string, { enabled = true } = {}) {
  return useInfiniteQuery({
    queryKey: personHistoryQueryKey(familyId, personId),
    queryFn: async ({ pageParam }) => {
      const { data } = await apiClient.GET('/families/{familyId}/persons/{personId}/history', {
        params: { path: { familyId, personId }, query: { page: pageParam, size: PAGE_SIZE } },
      });
      if (data === undefined) {
        throw new Error('GET …/persons/{personId}/history returned no body');
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
