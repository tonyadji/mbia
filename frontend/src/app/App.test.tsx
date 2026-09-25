import { render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from './App';
import { createQueryClient } from './queryClient';
import { routes } from './routes';

describe('App', () => {
  it('renders the placeholder page with only the brand name', async () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] });

    const { container } = render(<App router={router} queryClient={createQueryClient()} />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Mbia' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(container).toHaveTextContent(/^Mbia$/);
  });
});
