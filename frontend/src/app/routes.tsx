import type { RouteObject } from 'react-router';
import { ProtectedRoute } from '../auth/ProtectedRoute';
import { LandingPage } from '../pages/LandingPage';
import { SignInCallbackPage } from '../pages/SignInCallbackPage';
import { WelcomePage } from '../pages/WelcomePage';
import { RootLayout } from './RootLayout';

export const routes: RouteObject[] = [
  {
    element: <RootLayout />,
    children: [
      { index: true, element: <WelcomePage /> },
      { path: 'auth/callback', element: <SignInCallbackPage /> },
      {
        element: <ProtectedRoute />,
        children: [{ path: 'home', element: <LandingPage /> }],
      },
    ],
  },
];
