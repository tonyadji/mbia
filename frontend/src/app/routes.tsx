import type { RouteObject } from 'react-router';
import { HomePlaceholderPage } from '../pages/HomePlaceholderPage';
import { RootLayout } from './RootLayout';

export const routes: RouteObject[] = [
  {
    element: <RootLayout />,
    children: [{ index: true, element: <HomePlaceholderPage /> }],
  },
];
