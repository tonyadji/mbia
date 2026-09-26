import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { familyMemoriesPath } from './FamilyMemoriesPage';
import { editMemoryPath, memoryPath } from './MemoryPage';

// The API client captures `fetch` when it is created: replace it before any import.
const fetchMock = vi.hoisted(() => {
  const mock = vi.fn<typeof fetch>();
  globalThis.fetch = mock;
  return mock;
});

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number) {
  return jsonResponse(
    { code, status, title: code, detail: 'raw server detail' },
    status,
    'application/problem+json',
  );
}

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const AWA_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const MARIE_ID = '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const MEMORY_ID = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f';
const MEMORY = memoryPath(ADJI_ID, MEMORY_ID);
const EDIT = editMemoryPath(ADJI_ID, MEMORY_ID);
const ME = 'u1';

function family(myRole: string) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    myLinkedPersonId: null,
    stats: { personCount: 2, memoryCount: 1, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function memory(overrides: Record<string, unknown> = {}) {
  return {
    id: MEMORY_ID,
    familyId: ADJI_ID,
    type: 'STORY',
    status: 'ACTIVE',
    title: 'Le marché de Yaoundé',
    content: 'Grand-mère vendait du plantain.',
    relatedPersons: [{ id: AWA_ID, displayName: 'Awa Ngo', status: 'ACTIVE' }],
    createdBy: { userId: ME, displayName: 'Tony', deleted: false },
    createdAt: '2026-09-20T10:00:00Z',
    updatedAt: '2026-09-20T10:00:00Z',
    version: 0,
    ...overrides,
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`), recording every request. */
function fakeApi({
  role = 'CONTRIBUTOR',
  getMemory = () => jsonResponse(memory()),
  patchMemory = async (request) =>
    jsonResponse(memory({ ...((await request.json()) as Record<string, unknown>), version: 1 })),
  deleteMemory = () => new Response(null, { status: 204 }),
}: {
  role?: string;
  getMemory?: Handler;
  patchMemory?: Handler;
  deleteMemory?: Handler;
} = {}) {
  const requests: Request[] = [];
  const handlers: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role)),
    [`GET /families/${ADJI_ID}/memories/${MEMORY_ID}`]: getMemory,
    [`PATCH /families/${ADJI_ID}/memories/${MEMORY_ID}`]: patchMemory,
    [`DELETE /families/${ADJI_ID}/memories/${MEMORY_ID}`]: deleteMemory,
    [`GET /families/${ADJI_ID}/memories`]: () =>
      jsonResponse({ items: [], page: { page: 0, size: 20, totalElements: 0, totalPages: 0 } }),
  };
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({ id: ME, email: 'tony@mbia.local', preferredLocale: 'fr' });
    }
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    sent: (method: string) => requests.filter((request) => request.method === method),
  };
}

function renderApp(entries: string[]) {
  const router = createMemoryRouter(routes, { initialEntries: entries });
  render(
    <App
      router={router}
      queryClient={createQueryClient()}
      userManager={fakeUserManager(fakeOidcUser())}
    />,
  );
  return { router };
}

describe('Edit & archive a Memory', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('actions on the Memory (SCREEN-013)', () => {
    it.each([
      ['ADMIN', 'someone-else'],
      ['CONTRIBUTOR', ME],
    ])('are offered to %s when allowed', async (role, author) => {
      fakeApi({
        role,
        getMemory: () =>
          jsonResponse(memory({ createdBy: { userId: author, displayName: 'X', deleted: false } })),
      });
      renderApp([MEMORY]);

      expect(await screen.findByRole('link', { name: 'Modifier' })).toHaveAttribute('href', EDIT);
      expect(screen.getByRole('button', { name: 'Archiver' })).toBeVisible();
    });

    it.each([
      ['CONTRIBUTOR', 'someone-else'],
      ['VIEWER', ME],
    ])('are hidden from %s on a Memory of %s', async (role, author) => {
      fakeApi({
        role,
        getMemory: () =>
          jsonResponse(memory({ createdBy: { userId: author, displayName: 'X', deleted: false } })),
      });
      renderApp([MEMORY]);

      expect(await screen.findByRole('heading', { name: 'Le marché de Yaoundé' })).toBeVisible();
      expect(screen.queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Archiver' })).not.toBeInTheDocument();
    });

    it('starts reading at the title of the Memory', async () => {
      fakeApi();
      renderApp([MEMORY]);

      expect(await screen.findByRole('heading', { level: 1 })).toHaveFocus();
    });

    it('gives the focus back to Archive when the confirmation is cancelled', async () => {
      const api = fakeApi();
      renderApp([MEMORY]);

      const archive = await screen.findByRole('button', { name: 'Archiver' });
      archive.focus();
      fireEvent.click(archive);
      const dialog = screen.getByRole('dialog');
      await waitFor(() => {
        expect(dialog.contains(document.activeElement)).toBe(true);
      });
      fireEvent.click(within(dialog).getByRole('button', { name: 'Annuler' }));

      await waitFor(() => {
        expect(archive).toHaveFocus();
      });
      expect(api.sent('DELETE')).toHaveLength(0);
    });

    it('archives after a confirmation, then returns where the User came from', async () => {
      const api = fakeApi();
      const { router } = renderApp([familyMemoriesPath(ADJI_ID), MEMORY]);

      fireEvent.click(await screen.findByRole('button', { name: 'Archiver' }));
      const dialog = screen.getByRole('dialog');
      expect(dialog).toHaveTextContent('Il disparaîtra pour toute la famille.');
      expect(api.sent('DELETE')).toHaveLength(0);
      fireEvent.click(within(dialog).getByRole('button', { name: 'Archiver' }));

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(familyMemoriesPath(ADJI_ID));
      });
      expect(api.sent('DELETE')[0]?.headers.get('If-Match')).toBe('"0"');
    });

    it('explains a stale version and reloads it before archiving', async () => {
      let version = 0;
      const api = fakeApi({
        getMemory: () => jsonResponse(memory({ version })),
        deleteMemory: () => problemResponse('CONCURRENT_MODIFICATION', 409),
      });
      renderApp([MEMORY]);

      fireEvent.click(await screen.findByRole('button', { name: 'Archiver' }));
      const dialog = screen.getByRole('dialog');
      fireEvent.click(within(dialog).getByRole('button', { name: 'Archiver' }));
      expect(
        await within(dialog).findByText(
          'Ce souvenir a été modifié depuis que vous avez ouvert la page.',
        ),
      ).toBeVisible();

      version = 3;
      fireEvent.click(
        within(dialog).getByRole('button', { name: 'Recharger la dernière version' }),
      );
      await waitFor(() => {
        expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();
      });
      fireEvent.click(within(dialog).getByRole('button', { name: 'Archiver' }));
      await waitFor(() => {
        expect(api.sent('DELETE')[1]?.headers.get('If-Match')).toBe('"3"');
      });
    });
  });

  describe('Edit Memory (SCREEN-014)', () => {
    it('sends the loaded version and the texts, then shows the Memory', async () => {
      const api = fakeApi();
      const { router } = renderApp([EDIT]);

      const title = await screen.findByLabelText(/Titre/);
      expect(title).toHaveValue('Le marché de Yaoundé');
      fireEvent.change(title, { target: { value: '  Le marché central ' } });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(MEMORY);
      });
      const patch = api.sent('PATCH')[0];
      expect(patch?.headers.get('If-Match')).toBe('"0"');
      // The Persons were not changed: they are not sent (OQ-008, OQ-043).
      expect(await patch?.json()).toEqual({
        title: 'Le marché central',
        content: 'Grand-mère vendait du plantain.',
      });
      expect(await screen.findByRole('status')).toHaveTextContent('ont été enregistrées');
    });

    it('explains a stale version and reloads the latest one without merging', async () => {
      let latest = memory();
      fakeApi({
        getMemory: () => jsonResponse(latest),
        patchMemory: () => problemResponse('CONCURRENT_MODIFICATION', 409),
      });
      renderApp([EDIT]);

      const title = await screen.findByLabelText(/Titre/);
      fireEvent.change(title, { target: { value: 'Mon titre' } });
      latest = memory({ title: 'Titre de quelqu’un d’autre', version: 2 });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent(
        'Ce souvenir a été modifié depuis que vous avez ouvert la page.',
      );
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
      fireEvent.click(within(alert).getByRole('button', { name: 'Recharger la dernière version' }));

      await waitFor(() => {
        expect(screen.getByLabelText(/Titre/)).toHaveValue('Titre de quelqu’un d’autre');
      });
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeEnabled();
    });

    it('announces the screen by its title, without opening the keyboard', async () => {
      fakeApi();
      renderApp([EDIT]);

      expect(await screen.findByRole('heading', { level: 1 })).toHaveFocus();
    });

    it('moves the focus to a stale version, then to the title once reloaded', async () => {
      let latest = memory();
      fakeApi({
        getMemory: () => jsonResponse(latest),
        patchMemory: () => problemResponse('CONCURRENT_MODIFICATION', 409),
      });
      renderApp([EDIT]);

      await screen.findByLabelText(/Titre/);
      latest = memory({ version: 2 });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      const alert = await screen.findByRole('alert');
      await waitFor(() => {
        expect(alert).toHaveFocus();
      });
      fireEvent.click(within(alert).getByRole('button', { name: 'Recharger la dernière version' }));
      await waitFor(() => {
        expect(screen.getByLabelText(/Titre/)).toHaveFocus();
      });
    });

    it('moves the focus to the first field the server refused', async () => {
      fakeApi({
        patchMemory: () =>
          jsonResponse(
            {
              code: 'VALIDATION_FAILED',
              status: 400,
              title: 'VALIDATION_FAILED',
              fieldErrors: [
                { field: 'content', code: 'INVALID' },
                { field: 'title', code: 'INVALID' },
              ],
            },
            400,
            'application/problem+json',
          ),
      });
      renderApp([EDIT]);

      await screen.findByLabelText(/Titre/);
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      await waitFor(() => {
        expect(screen.getByLabelText(/Votre histoire/)).toHaveFocus();
      });
      expect(screen.getByLabelText(/Titre/)).toHaveAttribute('aria-invalid', 'true');
    });

    it('marks an archived Person, who may stay but not be the only one left after a change', async () => {
      const api = fakeApi({
        getMemory: () =>
          jsonResponse(
            memory({
              relatedPersons: [
                { id: AWA_ID, displayName: 'Awa Ngo', status: 'ARCHIVED' },
                { id: MARIE_ID, displayName: 'Marie Ngo', status: 'ACTIVE' },
              ],
            }),
          ),
      });
      renderApp([EDIT]);

      const persons = await screen.findByRole('list', { name: 'Qui concerne ce souvenir ?' });
      const awa = within(persons).getByText('Awa Ngo').closest('li');
      expect(awa).toHaveTextContent('Profil archivé');
      fireEvent.click(within(persons).getByRole('button', { name: 'Retirer Marie Ngo' }));

      expect(screen.getByText(/Gardez au moins une personne/)).toBeVisible();
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();
      expect(api.sent('PATCH')).toHaveLength(0);
    });

    it('lets a story whose Persons are all archived be corrected (OQ-043)', async () => {
      const api = fakeApi({
        getMemory: () =>
          jsonResponse(
            memory({
              relatedPersons: [{ id: AWA_ID, displayName: 'Awa Ngo', status: 'ARCHIVED' }],
            }),
          ),
      });
      renderApp([EDIT]);

      fireEvent.change(await screen.findByLabelText(/Votre histoire/), {
        target: { value: 'Texte corrigé' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      await waitFor(() => {
        expect(api.sent('PATCH')).toHaveLength(1);
      });
    });

    it('sends the new Persons when they change', async () => {
      const api = fakeApi({
        getMemory: () =>
          jsonResponse(
            memory({
              relatedPersons: [
                { id: AWA_ID, displayName: 'Awa Ngo', status: 'ACTIVE' },
                { id: MARIE_ID, displayName: 'Marie Ngo', status: 'ACTIVE' },
              ],
            }),
          ),
      });
      renderApp([EDIT]);

      fireEvent.click(await screen.findByRole('button', { name: 'Retirer Awa Ngo' }));
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

      await waitFor(() => {
        expect(api.sent('PATCH')).toHaveLength(1);
      });
      expect(await api.sent('PATCH')[0]?.json()).toMatchObject({ relatedPersonIds: [MARIE_ID] });
    });

    it('after an edit, archiving still returns where the Memory was opened from', async () => {
      fakeApi();
      const { router } = renderApp([familyMemoriesPath(ADJI_ID), MEMORY]);

      fireEvent.click(await screen.findByRole('link', { name: 'Modifier' }));
      fireEvent.change(await screen.findByLabelText(/Titre/), { target: { value: 'Corrigé' } });
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
      expect(await screen.findByRole('heading', { name: 'Corrigé' })).toBeVisible();

      fireEvent.click(screen.getByRole('button', { name: 'Archiver' }));
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archiver' }));
      await waitFor(() => {
        expect(router.state.location.pathname).toBe(familyMemoriesPath(ADJI_ID));
      });
    });

    it('sends a member who may not change the Memory back to it', async () => {
      fakeApi({
        role: 'CONTRIBUTOR',
        getMemory: () =>
          jsonResponse(
            memory({ createdBy: { userId: 'someone-else', displayName: 'X', deleted: false } }),
          ),
      });
      const { router } = renderApp([EDIT]);

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(MEMORY);
      });
    });
  });
});
