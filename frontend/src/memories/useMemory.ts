import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export function memoryQueryKey(familyId: string, memoryId: string) {
  return [...familyQueryKey(familyId), 'memories', memoryId] as const;
}

/** One Memory (`GET /families/{familyId}/memories/{memoryId}`). */
export function useMemory(familyId: string, memoryId: string, { enabled = true } = {}) {
  return useQuery({
    queryKey: memoryQueryKey(familyId, memoryId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/memories/{memoryId}', {
        params: { path: { familyId, memoryId } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/memories/{memoryId} returned no body');
      }
      return data;
    },
    // 404 MEMORY_NOT_FOUND / FAMILY_NOT_FOUND will not change by retrying.
    retry: retryUnlessClientError,
    enabled,
  });
}
