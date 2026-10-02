import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { familyQueryKey } from './useFamily';
import { familiesQueryKey } from './useMyFamilies';

/** Creates a Family; the current User becomes its ADMIN (`POST /families`). */
export function useCreateFamily() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (name: string) => {
      const { data } = await apiClient.POST('/families', { body: { name } });
      if (data === undefined) {
        throw new Error('POST /families returned no body');
      }
      return data;
    },
    onSuccess: (family) => {
      queryClient.setQueryData(familyQueryKey(family.id), family);
      void queryClient.invalidateQueries({ queryKey: familiesQueryKey, exact: true });
    },
  });
}
