import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export type PersonSummary = components['schemas']['PersonSummary'];

/** The search starts after 2 characters (SCREEN-007); a shorter text lists the first page. */
export const MIN_SEARCH_LENGTH = 2;

const PAGE_SIZE = 20;

/** Every search of a Family, to refresh after a Person is created or changed. */
export function personSearchQueryKey(familyId: string) {
  return [...familyQueryKey(familyId), 'persons', 'search'] as const;
}

/** The text actually searched: trimmed, and empty below `MIN_SEARCH_LENGTH` characters. */
export function searchTerm(text: string): string {
  const term = text.trim();
  return Array.from(term).length >= MIN_SEARCH_LENGTH ? term : '';
}

/**
 * The ACTIVE Persons of a Family matching `term`, page after page (`GET /families/{familyId}/persons`,
 * mvp.md §19): case- and accent-insensitive, ordered by display name. An empty `term` lists
 * every ACTIVE Person.
 */
export function usePersonSearch(familyId: string, term: string, { enabled = true } = {}) {
  return useInfiniteQuery({
    queryKey: [...personSearchQueryKey(familyId), term] as const,
    queryFn: async ({ pageParam }) => {
      const { data } = await apiClient.GET('/families/{familyId}/persons', {
        params: {
          path: { familyId },
          query: { search: term === '' ? undefined : term, page: pageParam, size: PAGE_SIZE },
        },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/persons returned no body');
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
