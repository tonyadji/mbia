import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import { currentUserQueryKey } from './useCurrentUser';

type UpdateCurrentUserRequest = components['schemas']['UpdateCurrentUserRequest'];

/** Changes the current User's display name or language (`PATCH /me`). */
export function useUpdateCurrentUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateCurrentUserRequest) => {
      const { data } = await apiClient.PATCH('/me', { body });
      if (data === undefined) {
        throw new Error('PATCH /me returned no body');
      }
      return data;
    },
    // The stored language is re-applied from this cache (ProtectedRoute): keep it current.
    onSuccess: (user) => {
      queryClient.setQueryData(currentUserQueryKey, user);
    },
  });
}
