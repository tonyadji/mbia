import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { retryUnlessClientError } from '../api/retry';
import { familiesQueryKey } from '../families/useMyFamilies';

/** The public preview of an invitation (`GET /invitations/{token}`): never its Person (OQ-050). */
export function useInvitationPreview(token: string) {
  return useQuery({
    queryKey: ['invitations', 'preview', token] as const,
    queryFn: async () => {
      const { data } = await apiClient.GET('/invitations/{token}', {
        params: { path: { token } },
      });
      if (data === undefined) {
        throw new Error('GET /invitations/{token} returned no body');
      }
      return data;
    },
    // 404 and 410 will not change by retrying.
    retry: retryUnlessClientError,
  });
}

/** Joins the Family as the signed-in User (`POST /invitations/{token}/accept`, mvp.md §18). */
export function useAcceptInvitation(token: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const { data } = await apiClient.POST('/invitations/{token}/accept', {
        params: { path: { token } },
      });
      if (data === undefined) {
        throw new Error('POST /invitations/{token}/accept returned no body');
      }
      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: familiesQueryKey });
    },
  });
}
