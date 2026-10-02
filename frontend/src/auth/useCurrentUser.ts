import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';

export const currentUserQueryKey = ['me'] as const;

/** The Mbia User behind the session (`GET /me`, created on first call by the backend). */
export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: async () => {
      const { data } = await apiClient.GET('/me');
      if (data === undefined) {
        throw new Error('GET /me returned no body');
      }
      return data;
    },
    // A client error (401, 403 EMAIL_NOT_VERIFIED) will not change by retrying.
    retry: retryUnlessClientError,
  });
}
