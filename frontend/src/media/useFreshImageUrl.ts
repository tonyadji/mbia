import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { familiesQueryKey } from '../families/useMyFamilies';

/**
 * Photo URLs are pre-signed for 60 minutes (data-model.md §13, §14bis). When an image fails to
 * load, most likely because its URL expired, the Family data on screen is fetched again to get
 * freshly signed URLs; a new URL that fails as well is given up (`show` false): no reload loop.
 */
export function useFreshImageUrl(url: string | null) {
  const queryClient = useQueryClient();
  // The last URL that failed to load, until an image loads again.
  const [failed, setFailed] = useState<string | null>(null);

  const onError = () => {
    if (url === null) return;
    setFailed(url);
    // A second failure in a row, with a fresh URL, stops there.
    if (failed === null) {
      void queryClient.invalidateQueries(
        { queryKey: familiesQueryKey, refetchType: 'active' },
        { cancelRefetch: false },
      );
    }
  };

  return {
    show: url !== null && failed !== url,
    onError,
    onLoad: () => {
      setFailed(null);
    },
  };
}
