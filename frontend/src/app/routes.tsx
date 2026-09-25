import type { RouteObject } from 'react-router';
import { CREATE_FAMILY_PATH } from '../auth/AuthProvider';
import { ProtectedRoute } from '../auth/ProtectedRoute';
import { AccountSettingsPage, SETTINGS_PATH } from '../pages/AccountSettingsPage';
import { AddPersonPage } from '../pages/AddPersonPage';
import { CreateFamilyPage } from '../pages/CreateFamilyPage';
import { FamilyGatePage } from '../pages/FamilyGatePage';
import { FamilyHomePage } from '../pages/FamilyHomePage';
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
        children: [
          { path: 'home', element: <FamilyGatePage /> },
          { path: CREATE_FAMILY_PATH.slice(1), element: <CreateFamilyPage /> },
          { path: 'families/:familyId', element: <FamilyHomePage /> },
          { path: 'families/:familyId/persons/new', element: <AddPersonPage /> },
          { path: SETTINGS_PATH.slice(1), element: <AccountSettingsPage /> },
        ],
      },
    ],
  },
];
