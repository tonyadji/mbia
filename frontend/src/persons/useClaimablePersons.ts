import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { personSearchQueryKey } from './usePersonSearch';

export type ClaimablePerson = components['schemas']['ClaimablePerson'];

const PAGE_SIZE = 20;

/**
 * The Persons the current User can claim as "me", each with one known parent
 * (`GET /families/{familyId}/claimable-persons`, mvp.md §18, OQ-050), with the matching and order
 * of the people search.
 */
export function useClaimablePersons(familyId: string, term: string, { enabled = true } = {}) {
  return useInfiniteQuery({
    queryKey: [...personSearchQueryKey(familyId), 'claimable', term] as const,
    queryFn: async ({ pageParam }) => {
      const { data } = await apiClient.GET('/families/{familyId}/claimable-persons', {
        params: {
          path: { familyId },
          query: { search: term === '' ? undefined : term, page: pageParam, size: PAGE_SIZE },
        },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/claimable-persons returned no body');
      }
      return data;
    },
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.page.page + 1 < last.page.totalPages ? last.page.page + 1 : undefined,
    retry: retryUnlessClientError,
    enabled,
    placeholderData: keepPreviousData,
  });
}
