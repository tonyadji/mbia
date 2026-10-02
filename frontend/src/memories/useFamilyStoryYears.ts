import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { familyMemoriesQueryKey } from './useFamilyMemories';

/**
 * Under the Memories of the Family: publishing, editing or archiving a Memory refreshes the strip
 * with them.
 */
export function familyStoryYearsQueryKey(familyId: string) {
  return [...familyMemoriesQueryKey(familyId), 'story', 'years'] as const;
}

/**
 * The strip of years of the family story (`GET /families/{familyId}/story/years`, mvp.md §20,
 * OQ-064): the years with ACTIVE Memories, oldest first, with their counts, and the number of
 * Memories without a year.
 */
export function useFamilyStoryYears(familyId: string, { enabled = true } = {}) {
  return useQuery({
    queryKey: familyStoryYearsQueryKey(familyId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/story/years', {
        params: { path: { familyId } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/story/years returned no body');
      }
      return data;
    },
    retry: retryUnlessClientError,
    enabled,
  });
}
