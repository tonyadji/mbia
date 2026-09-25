import { act, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { i18n } from '../i18n';
import { App } from './App';
import { createQueryClient } from './queryClient';
import { routes } from './routes';

function renderApp() {
  const router = createMemoryRouter(routes, { initialEntries: ['/'] });
  return render(<App router={router} queryClient={createQueryClient()} />);
}

describe('App', () => {
  afterEach(async () => {
    await act(() => i18n.changeLanguage('fr'));
  });

  it('renders the placeholder page in French', async () => {
    await act(() => i18n.changeLanguage('fr'));
    renderApp();

    expect(await screen.findByRole('heading', { level: 1, name: 'Mbia' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByText('Le patrimoine de votre famille, ensemble.')).toBeInTheDocument();
    expect(screen.getByText('Bientôt disponible.')).toBeInTheDocument();
  });

  it('switches the placeholder page text when the language changes to English', async () => {
    await act(() => i18n.changeLanguage('fr'));
    renderApp();
    await screen.findByText('Le patrimoine de votre famille, ensemble.');

    await act(() => i18n.changeLanguage('en'));

    expect(screen.getByRole('heading', { level: 1, name: 'Mbia' })).toBeInTheDocument();
    expect(screen.getByText("Your family's heritage, together.")).toBeInTheDocument();
    expect(screen.getByText('Coming soon.')).toBeInTheDocument();
    expect(screen.queryByText('Bientôt disponible.')).not.toBeInTheDocument();
  });
});
