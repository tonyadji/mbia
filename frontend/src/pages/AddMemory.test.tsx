import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, MemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { MemoryCard } from '../components/MemoryCard';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { addMemoryPath } from './AddMemoryPage';
import { familyHomePath } from './FamilyHomePage';

// The API client captures `fetch` when it is created: replace it before any import.
const fetchMock = vi.hoisted(() => {
  const mock = vi.fn<typeof fetch>();
  globalThis.fetch = mock;
  return mock;
});

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number, extra: Record<string, unknown> = {}) {
  return jsonResponse(
    { code, status, title: code, detail: 'raw server detail', ...extra },
    status,
    'application/problem+json',
  );
}

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const MARIE_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const ELOISE_ID = '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const MEMORY_ID = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f';

function family(overrides: Record<string, unknown> = {}) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole: 'ADMIN',
    myLinkedPersonId: MARIE_ID,
    stats: { personCount: 2, memoryCount: 0, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

function person(overrides: Record<string, unknown> = {}) {
  return {
    id: MARIE_ID,
    familyId: ADJI_ID,
    firstName: 'Marie',
    lastName: 'Adji',
    displayName: 'Marie Adji',
    gender: 'FEMALE',
    birth: { precision: 'YEAR_ONLY', year: 1990 },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    relationshipToCurrentUser: 'SELF',
    profilePictureUrl: null,
    version: 0,
    biography: null,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

const marie = person();
const eloise = person({
  id: ELOISE_ID,
  firstName: 'Éloïse',
  lastName: 'Ngo',
  displayName: 'Éloïse Ngo',
  birth: { precision: 'YEAR_ONLY', year: 1948 },
  relationshipToCurrentUser: 'MOTHER',
});

function memoryFrom(body: { title: string; content: string; relatedPersonIds: string[] }) {
  const byId: Record<string, ReturnType<typeof person>> = {
    [MARIE_ID]: marie,
    [ELOISE_ID]: eloise,
  };
  return {
    id: MEMORY_ID,
    familyId: ADJI_ID,
    type: 'STORY',
    status: 'ACTIVE',
    title: body.title,
    content: body.content,
    relatedPersons: body.relatedPersonIds.map((id) => ({
      id,
      displayName: byId[id]?.displayName ?? '?',
    })),
    createdBy: { userId: 'u1', displayName: 'Marie', deleted: false },
    createdAt: '2026-09-26T10:00:00Z',
    updatedAt: '2026-09-26T10:00:00Z',
    version: 0,
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`), recording every request body. */
function fakeApi({
  familyOverrides = {},
  persons = { [MARIE_ID]: marie, [ELOISE_ID]: eloise },
  create = [],
  locale = 'fr',
}: {
  familyOverrides?: Record<string, unknown>;
  persons?: Record<string, unknown>;
  create?: Handler[];
  locale?: string;
} = {}) {
  const posted: unknown[] = [];
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method} ${path}`;
    if (key === 'GET /me') {
      return jsonResponse({ id: 'u1', email: 'marie@mbia.local', preferredLocale: locale });
    }
    if (key === `GET /families/${ADJI_ID}`) return jsonResponse(family(familyOverrides));
    if (key === `GET /families/${ADJI_ID}/persons`) {
      return jsonResponse({
        items: [eloise, marie],
        page: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      });
    }
    const personMatch = /^GET \/families\/[^/]+\/persons\/([^/]+)$/.exec(key);
    if (personMatch) {
      const found = persons[personMatch[1] ?? ''];
      return found ? jsonResponse(found) : problemResponse('PERSON_NOT_FOUND', 404);
    }
    if (key === `POST /families/${ADJI_ID}/memories/stories`) {
      const body = (await request.clone().json()) as Parameters<typeof memoryFrom>[0];
      const handler = create[posted.length];
      posted.push(body);
      return handler ? handler(request) : jsonResponse(memoryFrom(body), 201);
    }
    return problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return { posted };
}

function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <App
      router={router}
      queryClient={createQueryClient()}
      userManager={fakeUserManager(fakeOidcUser())}
    />,
  );
  return { router };
}

const publish = () => screen.getByRole('button', { name: 'Publier' });

async function fillStory(title: string, content: string) {
  fireEvent.change(await screen.findByRole('textbox', { name: 'Titre' }), {
    target: { value: title },
  });
  fireEvent.change(screen.getByRole('textbox', { name: 'Votre histoire' }), {
    target: { value: content },
  });
}

function chosenPersons() {
  const list = screen.queryByRole('list', { name: 'Qui concerne ce souvenir ?' });
  // Each chosen Person has its own `Remove` button.
  return list
    ? within(list)
        .getAllByRole('button')
        .map((button) => button.getAttribute('aria-label')?.replace(/^Retirer /, ''))
    : [];
}

describe('Add a Memory (SCREEN-006)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('Family Home entry point', () => {
    it.each(['ADMIN', 'CONTRIBUTOR'])('offers Add a memory to a %s', async (myRole) => {
      fakeApi({ familyOverrides: { myRole } });
      renderApp(familyHomePath(ADJI_ID));
      expect(await screen.findByRole('link', { name: 'Ajouter un souvenir' })).toHaveAttribute(
        'href',
        addMemoryPath(ADJI_ID),
      );
    });

    it('offers nothing to a VIEWER', async () => {
      fakeApi({ familyOverrides: { myRole: 'VIEWER' } });
      renderApp(familyHomePath(ADJI_ID));
      await screen.findByRole('link', { name: "Voir l'arbre familial" });
      expect(screen.queryByRole('link', { name: 'Ajouter un souvenir' })).not.toBeInTheDocument();
    });

    it('offers nothing while the Family has no Person, since a Memory needs one', async () => {
      fakeApi({
        familyOverrides: {
          myLinkedPersonId: null,
          stats: { personCount: 0, memoryCount: 0, activeMemberCount: 1 },
        },
      });
      renderApp(familyHomePath(ADJI_ID));
      await screen.findByRole('link', { name: 'Commencer par moi' });
      expect(screen.queryByRole('link', { name: 'Ajouter un souvenir' })).not.toBeInTheDocument();
    });

    it('opens the story form directly, without a photo or story choice', async () => {
      fakeApi();
      const { router } = renderApp(familyHomePath(ADJI_ID));
      fireEvent.click(await screen.findByRole('link', { name: 'Ajouter un souvenir' }));
      expect(
        await screen.findByRole('heading', { level: 1, name: 'Raconter une histoire' }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/memories/new`);
      expect(screen.queryByText(/photo/i)).not.toBeInTheDocument();
    });
  });

  describe('preselection (family-tree-ux.md §13)', () => {
    it("preselects the User's own Person from Family Home", async () => {
      fakeApi();
      renderApp(addMemoryPath(ADJI_ID));
      await screen.findByRole('textbox', { name: 'Titre' });
      expect(chosenPersons()).toEqual(['Marie Adji']);
      expect(publish()).toBeEnabled();
    });

    it('preselects nobody when the User has no Person, and Publish stays disabled', async () => {
      fakeApi({ familyOverrides: { myLinkedPersonId: null } });
      renderApp(addMemoryPath(ADJI_ID));
      await fillStory('Le marché', 'Chaque samedi.');
      expect(chosenPersons()).toEqual([]);
      expect(screen.getByText('Choisissez au moins une personne pour pouvoir publier.'));
      expect(publish()).toBeDisabled();
    });

    it('preselects the Person the flow starts from', async () => {
      fakeApi();
      renderApp(addMemoryPath(ADJI_ID, { personId: ELOISE_ID }));
      await screen.findByRole('textbox', { name: 'Titre' });
      expect(chosenPersons()).toEqual(['Éloïse Ngo']);
      expect(screen.getByRole('link', { name: 'Retour' })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/persons/${ELOISE_ID}`,
      );
    });

    it('does not preselect an archived Person', async () => {
      fakeApi({ persons: { [MARIE_ID]: person({ status: 'ARCHIVED' }) } });
      renderApp(addMemoryPath(ADJI_ID));
      await screen.findByRole('textbox', { name: 'Titre' });
      expect(chosenPersons()).toEqual([]);
      expect(publish()).toBeDisabled();
    });
  });

  it('adds Persons from the Family search and disables Publish when the last one is removed', async () => {
    fakeApi();
    renderApp(addMemoryPath(ADJI_ID));
    await screen.findByRole('textbox', { name: 'Titre' });

    fireEvent.click(screen.getByRole('button', { name: 'Ajouter une personne' }));
    const sheet = await screen.findByRole('dialog', { name: 'Choisir une personne' });
    // Marie is already chosen: only Éloïse is offered.
    const results = await within(sheet).findByRole('list', { name: 'Personnes trouvées' });
    expect(within(results).getAllByRole('button')).toHaveLength(1);
    fireEvent.click(within(results).getByRole('button', { name: /Éloïse Ngo/ }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(chosenPersons()).toEqual(['Marie Adji', 'Éloïse Ngo']);

    fireEvent.click(screen.getByRole('button', { name: 'Retirer Marie Adji' }));
    fireEvent.click(screen.getByRole('button', { name: 'Retirer Éloïse Ngo' }));
    expect(chosenPersons()).toEqual([]);
    expect(publish()).toBeDisabled();
  });

  it('says when everyone found is already chosen', async () => {
    fakeApi();
    renderApp(addMemoryPath(ADJI_ID, { personId: ELOISE_ID }));
    await screen.findByRole('textbox', { name: 'Titre' });
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter une personne' }));
    const sheet = await screen.findByRole('dialog', { name: 'Choisir une personne' });
    fireEvent.click(await within(sheet).findByRole('button', { name: /Marie Adji/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter une personne' }));
    expect(
      await within(await screen.findByRole('dialog', { name: 'Choisir une personne' })).findByText(
        'Les personnes trouvées sont déjà choisies.',
      ),
    ).toBeInTheDocument();
  });

  it('refuses a blank title or text before sending anything', async () => {
    const api = fakeApi();
    renderApp(addMemoryPath(ADJI_ID));
    await fillStory('   ', '\n  ');
    fireEvent.click(publish());
    expect(await screen.findAllByText('Ce champ est obligatoire.')).toHaveLength(2);
    expect(api.posted).toHaveLength(0);
  });

  it('refuses a text above 50,000 characters without cutting it', async () => {
    const api = fakeApi();
    renderApp(addMemoryPath(ADJI_ID));
    const long = 'a'.repeat(50_001);
    await fillStory('Le marché', long);
    fireEvent.click(publish());
    expect(
      await screen.findByText('Ce texte ne peut pas dépasser 50 000 caractères.', {
        normalizer: (text) => text.replace(/\s/g, ' '),
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Votre histoire' })).toHaveValue(long);
    expect(api.posted).toHaveLength(0);
  });

  it('publishes the story as typed and lands on the first related Person', async () => {
    const api = fakeApi();
    const { router } = renderApp(addMemoryPath(ADJI_ID));
    const content = 'Chaque samedi,\n\ngrand-mère allait au marché.\n<b>pas du HTML</b>';
    await fillStory('  Le marché  ', content);
    fireEvent.click(publish());

    expect(
      await screen.findByText('Votre souvenir « Le marché » a été publié.'),
    ).toBeInTheDocument();
    expect(api.posted).toEqual([{ title: 'Le marché', content, relatedPersonIds: [MARIE_ID] }]);
    expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/persons/${MARIE_ID}`);
  });

  describe('refusals keep everything typed', () => {
    async function publishRefused(handler: Handler) {
      const api = fakeApi({ create: [handler] });
      renderApp(addMemoryPath(ADJI_ID));
      await fillStory('Le marché', 'Chaque samedi,\ngrand-mère allait au marché.');
      fireEvent.click(publish());
      const alert = await screen.findByRole('alert');
      expect(alert).not.toHaveTextContent('raw server detail');
      expect(screen.getByRole('textbox', { name: 'Titre' })).toHaveValue('Le marché');
      expect(screen.getByRole('textbox', { name: 'Votre histoire' })).toHaveValue(
        'Chaque samedi,\ngrand-mère allait au marché.',
      );
      expect(chosenPersons()).toEqual(['Marie Adji']);
      await waitFor(() => {
        expect(publish()).toBeEnabled();
      });
      return { alert, api };
    }

    it('explains an archived Person (PERSON_NOT_ACTIVE)', async () => {
      const { alert } = await publishRefused(() => problemResponse('PERSON_NOT_ACTIVE', 409));
      expect(alert).toHaveTextContent(
        'Une des personnes choisies a été archivée ou fusionnée entre-temps. Retirez-la, puis publiez à nouveau.',
      );
    });

    it('explains a Person no longer in the Family (PERSON_NOT_FOUND)', async () => {
      const { alert } = await publishRefused(() => problemResponse('PERSON_NOT_FOUND', 404));
      expect(alert).toHaveTextContent('ne fait plus partie de la famille');
    });

    it('marks the field of a validation refusal', async () => {
      const { alert } = await publishRefused(() =>
        problemResponse('VALIDATION_FAILED', 400, {
          fieldErrors: [
            { field: 'title', code: 'SIZE', message: 'size must be between 1 and 250' },
          ],
        }),
      );
      expect(alert).toHaveTextContent(i18n.t('errors:VALIDATION_FAILED'));
      expect(screen.getByRole('textbox', { name: 'Titre' })).toHaveAccessibleDescription(
        'Vérifiez ce champ.',
      );
    });

    it('explains an unexpected failure, and a new attempt publishes', async () => {
      const { api } = await publishRefused(() => problemResponse('INTERNAL_ERROR', 500));
      expect(screen.getByRole('alert')).toHaveTextContent(i18n.t('errors:unexpected'));
      fireEvent.click(publish());
      expect(
        await screen.findByText('Votre souvenir « Le marché » a été publié.'),
      ).toBeInTheDocument();
      expect(api.posted).toHaveLength(2);
    });
  });

  it('tells a VIEWER who opens the form that their role does not allow it', async () => {
    fakeApi({ familyOverrides: { myRole: 'VIEWER' } });
    renderApp(addMemoryPath(ADJI_ID));
    expect(
      await screen.findByText("Votre rôle dans cette famille ne permet pas d'ajouter un souvenir."),
    ).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Titre' })).not.toBeInTheDocument();
  });

  it('shows the form in English', async () => {
    fakeApi({ locale: 'en' });
    renderApp(addMemoryPath(ADJI_ID));
    expect(await screen.findByRole('heading', { level: 1, name: 'Tell a story' })).toBeVisible();
    expect(await screen.findByRole('textbox', { name: 'Your story' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Who is this memory about?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publish' })).toBeEnabled();
  });
});

describe('MemoryCard', () => {
  it('shows the title and the text as plain text, and opens the Memory', () => {
    render(
      <MemoryCard
        title="Le marché"
        content={'Chaque samedi,\n<b>grand-mère</b> allait au marché.'}
        to="/families/f/memories/m"
      />,
      { wrapper: MemoryRouter },
    );
    const card = screen.getByRole('link', { name: /Le marché/ });
    expect(card).toHaveAttribute('href', '/families/f/memories/m');
    expect(card).toHaveTextContent('<b>grand-mère</b> allait au marché.');
    expect(card.querySelector('b')).toBeNull();
  });
});
