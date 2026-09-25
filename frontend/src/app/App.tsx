import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { RouterProvider, type DataRouter } from 'react-router';
import { AuthProvider, type AuthUserManager } from '../auth/AuthProvider';

interface AppProps {
  router: DataRouter;
  queryClient: QueryClient;
  userManager: AuthUserManager;
}

export function App({ router, queryClient, userManager }: AppProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider userManager={userManager}>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  );
}
