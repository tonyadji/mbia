import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export type FamilyActivity = components['schemas']['ActivityResponse'];

/** Family Home shows the 10 most recent lines, with no "show more" (mvp.md §20, OQ-054). */
export const RECENT_ACTIVITY_SIZE = 10;

export function activitiesQueryKey(familyId: string) {
  return [...familyQueryKey(familyId), 'activities'] as const;
}

/**
 * The most recent lines of the Family's activity, grouped by the server
 * (`GET /families/{familyId}/activities`), for any member, VIEWER included.
 */
export function useFamilyActivities(familyId: string) {
  return useQuery({
    queryKey: activitiesQueryKey(familyId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/activities', {
        params: { path: { familyId }, query: { size: RECENT_ACTIVITY_SIZE } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/activities returned no body');
      }
      return data.items;
    },
    retry: retryUnlessClientError,
  });
}
