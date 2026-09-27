import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { retryUnlessClientError } from '../api/retry';
import { familyQueryKey } from '../families/useFamily';

export type Member = components['schemas']['MemberResponse'];

export function membersQueryKey(familyId: string) {
  return [...familyQueryKey(familyId), 'members'] as const;
}

/**
 * The Family's ACTIVE members, in the order they joined (`GET /families/{familyId}/members`), for
 * any member; never their email (OQ-061).
 */
export function useFamilyMembers(familyId: string, { enabled = true } = {}) {
  return useQuery({
    queryKey: membersQueryKey(familyId),
    queryFn: async () => {
      const { data } = await apiClient.GET('/families/{familyId}/members', {
        params: { path: { familyId } },
      });
      if (data === undefined) {
        throw new Error('GET /families/{familyId}/members returned no body');
      }
      return data;
    },
    retry: retryUnlessClientError,
    enabled,
  });
}
