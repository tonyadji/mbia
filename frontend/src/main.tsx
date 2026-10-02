import '@fontsource-variable/nunito-sans';
import './styles/index.css';
import './i18n';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { App } from './app/App';
import { createQueryClient } from './app/queryClient';
import { routes } from './app/routes';
import { createUserManager } from './auth/oidcConfig';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element #root not found');
}

createRoot(rootElement).render(
  <StrictMode>
    <App
      router={createBrowserRouter(routes)}
      queryClient={createQueryClient()}
      userManager={createUserManager()}
    />
  </StrictMode>,
);
