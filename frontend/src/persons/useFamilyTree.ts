import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export type FamilyTree = components['schemas']['TreeResponse'];
export type TreeNode = components['schemas']['TreeNode'];

export function familyTreeQueryKey(familyId: string, focusPersonId: string | undefined) {
  return [...familyQueryKey(familyId), 'tree', focusPersonId ?? null] as const;
}

/**
 * The depth-1 local graph around a Person (`GET /families/{familyId}/tree`). Without
 * `focusPersonId`, or when that Person is no longer ACTIVE, the server chooses the focus (OQ-014).
 * `keepPrevious` keeps the current tree while the next focus loads (SCREEN-003 recentering).
 */
export function useFamilyTree(
  familyId: string,
  focusPersonId: string | undefined,
  { enabled = true, keepPrevious = false } = {},
) {
  return useQuery({
    queryKey: familyTreeQueryKey(familyId, focusPersonId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/tree', {
        params: { path: { familyId }, query: { focusPersonId, depth: 1 } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/tree returned no body');
      }
      return data;
    },
    retry: retryUnlessClientError,
    enabled,
    placeholderData: keepPrevious ? keepPreviousData : undefined,
  });
}
