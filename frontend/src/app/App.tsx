import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { RouterProvider, type DataRouter } from 'react-router';

interface AppProps {
  router: DataRouter;
  queryClient: QueryClient;
}

export function App({ router, queryClient }: AppProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
