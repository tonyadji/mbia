import type { RouteObject } from 'react-router';
import { CREATE_FAMILY_PATH } from '../auth/AuthProvider';
import { ProtectedRoute } from '../auth/ProtectedRoute';
import { AccountSettingsPage, SETTINGS_PATH } from '../pages/AccountSettingsPage';
import { AddPersonPage } from '../pages/AddPersonPage';
import { CreateFamilyPage } from '../pages/CreateFamilyPage';
import { EditPersonPage } from '../pages/EditPersonPage';
import { FamilyGatePage } from '../pages/FamilyGatePage';
import { FamilyHomePage } from '../pages/FamilyHomePage';
import { FamilyTreePage } from '../pages/FamilyTreePage';
import { PersonProfilePage } from '../pages/PersonProfilePage';
import { SearchPage } from '../pages/SearchPage';
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
          { path: 'families/:familyId/tree', element: <FamilyTreePage /> },
          { path: 'families/:familyId/search', element: <SearchPage /> },
          { path: 'families/:familyId/persons/new', element: <AddPersonPage /> },
          { path: 'families/:familyId/persons/:personId', element: <PersonProfilePage /> },
          { path: 'families/:familyId/persons/:personId/edit', element: <EditPersonPage /> },
          { path: SETTINGS_PATH.slice(1), element: <AccountSettingsPage /> },
        ],
      },
    ],
  },
];
