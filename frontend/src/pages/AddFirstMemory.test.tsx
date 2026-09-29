import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
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
const NEW_ID = '7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e';
const MEMORY_ID = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f';

function person(overrides: Record<string, unknown> = {}) {
  return {
    id: MARIE_ID,
    familyId: ADJI_ID,
    firstName: 'Marie',
    lastName: 'Adji',
    displayName: 'Marie Adji',
    gender: 'FEMALE',
    birth: { precision: 'UNKNOWN' },
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
  relationshipToCurrentUser: 'MOTHER',
});

type Handler = (request: Request) => Response | Promise<Response>;

/**
 * A fake API for SCREEN-006: `empty` is a Family without Person, whose counts and linked Person
 * change once a Person is created (as the server's would). Records the Person and Memory bodies,
 * and the order of the creations.
 */
function fakeApi({
  empty = false,
  createPerson = [],
  createStory = [],
}: { empty?: boolean; createPerson?: Handler[]; createStory?: Handler[] } = {}) {
  const persons: Record<string, unknown>[] = [];
  const stories: Record<string, unknown>[] = [];
  const order: string[] = [];
  let created: ReturnType<typeof person> | null = null;
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method} ${path}`;
    if (key === 'GET /me') {
      return jsonResponse({ id: 'u1', email: 'marie@mbia.local', preferredLocale: 'fr' });
    }
    if (key === `GET /families/${ADJI_ID}`) {
      const personCount = (empty ? 0 : 2) + (created ? 1 : 0);
      return jsonResponse({
        id: ADJI_ID,
        name: 'ADJI',
        myRole: 'ADMIN',
        myLinkedPersonId: empty
          ? created?.relationshipToCurrentUser === 'SELF'
            ? NEW_ID
            : null
          : MARIE_ID,
        stats: { personCount, memoryCount: 0, activeMemberCount: 1 },
        limits: { maxPhotosPerMemory: 3 },
        version: 0,
        createdAt: '2026-09-25T10:00:00Z',
        updatedAt: '2026-09-25T10:00:00Z',
      });
    }
    if (key === `GET /families/${ADJI_ID}/persons`) {
      return jsonResponse({
        items: [eloise, marie],
        page: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
      });
    }
    if (key === `GET /families/${ADJI_ID}/persons/${MARIE_ID}`) return jsonResponse(marie);
    if (key === `POST /families/${ADJI_ID}/persons`) {
      const body = (await request.clone().json()) as Record<string, unknown>;
      const handler = createPerson[persons.length];
      persons.push(body);
      order.push('person');
      if (handler) return handler(request);
      const firstName = String(body.firstName);
      const lastName = (body.lastName as string | undefined) ?? null;
      created = person({
        id: NEW_ID,
        firstName,
        lastName,
        displayName: [firstName, lastName].filter(Boolean).join(' '),
        relationshipToCurrentUser: body.linkToCurrentUser ? 'SELF' : null,
      });
      return jsonResponse(created, 201);
    }
    if (key === `POST /families/${ADJI_ID}/memories/stories`) {
      const body = (await request.clone().json()) as Record<string, unknown>;
      const handler = createStory[stories.length];
      stories.push(body);
      order.push('memory');
      if (handler) return handler(request);
      return jsonResponse(
        {
          id: MEMORY_ID,
          familyId: ADJI_ID,
          type: 'STORY',
          photos: [],
          status: 'ACTIVE',
          title: body.title,
          content: body.content,
          happenedAt: { precision: 'UNKNOWN' },
          relatedPersons: [],
          createdBy: { userId: 'u1', displayName: 'Marie', deleted: false },
          createdAt: '2026-09-26T10:00:00Z',
          updatedAt: '2026-09-26T10:00:00Z',
          version: 0,
        },
        201,
      );
    }
    return problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return { persons, stories, order };
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

function type(name: string, value: string, container: HTMLElement = document.body) {
  fireEvent.change(within(container).getByRole('textbox', { name }), { target: { value } });
}

async function fillStory() {
  await screen.findByRole('textbox', { name: 'Titre' });
  type('Titre', 'Le marché');
  type('Votre histoire', 'Chaque samedi.');
}

describe('A first memory (SCREEN-006, OQ-065)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('Who is this memory about?', () => {
    it('is asked first while the Family has no Person, in place of the related Persons', async () => {
      fakeApi({ empty: true });
      renderApp(addMemoryPath(ADJI_ID));

      const question = await screen.findByRole('group', { name: 'De qui parle ce souvenir ?' });
      const fields = screen.getAllByRole('textbox');
      // Before the title.
      expect(question.compareDocumentPosition(screen.getByRole('textbox', { name: 'Titre' }))).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      );
      expect(fields).toHaveLength(2);
      expect(within(question).getByRole('button', { name: 'Moi' })).toHaveAttribute(
        'aria-pressed',
        'false',
      );
      expect(
        screen.queryByRole('button', { name: 'Ajouter une personne' }),
      ).not.toBeInTheDocument();
      expect(publish()).toBeDisabled();
    });

    it('is not asked once the Family has a Person', async () => {
      fakeApi();
      renderApp(addMemoryPath(ADJI_ID));
      await screen.findByRole('textbox', { name: 'Titre' });
      expect(
        screen.queryByRole('group', { name: 'De qui parle ce souvenir ?' }),
      ).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Ajouter une personne' })).toBeInTheDocument();
    });

    it("Me creates the User's own linked Person, then the Memory about them", async () => {
      const api = fakeApi({ empty: true });
      const { router } = renderApp(addMemoryPath(ADJI_ID));

      fireEvent.click(await screen.findByRole('button', { name: 'Moi' }));
      expect(screen.getByRole('button', { name: 'Moi' })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.getByRole('textbox', { name: 'Prénom' })).toHaveAttribute(
        'autocomplete',
        'given-name',
      );
      type('Prénom', ' Alice ');
      type('Nom', 'Adji');
      await fillStory();
      fireEvent.click(publish());

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/memories/${MEMORY_ID}`);
      });
      expect(api.order).toEqual(['person', 'memory']);
      expect(api.persons).toEqual([
        {
          firstName: 'Alice',
          lastName: 'Adji',
          isDeceased: false,
          linkToCurrentUser: true,
          confirmPossibleDuplicate: false,
        },
      ]);
      expect(api.stories[0]).toMatchObject({ title: 'Le marché', relatedPersonIds: [NEW_ID] });
    });

    it('Someone else creates a Person not linked to the User', async () => {
      const api = fakeApi({ empty: true });
      renderApp(addMemoryPath(ADJI_ID));

      fireEvent.click(await screen.findByRole('button', { name: "Quelqu'un d'autre" }));
      type('Prénom', 'Rose');
      await fillStory();
      fireEvent.click(publish());

      await waitFor(() => {
        expect(api.stories).toHaveLength(1);
      });
      expect(api.persons[0]).toEqual({
        firstName: 'Rose',
        isDeceased: false,
        linkToCurrentUser: false,
        confirmPossibleDuplicate: false,
      });
      expect(api.stories[0]).toMatchObject({ relatedPersonIds: [NEW_ID] });
    });

    it('needs a first name before anything is created', async () => {
      const api = fakeApi({ empty: true });
      renderApp(addMemoryPath(ADJI_ID));

      fireEvent.click(await screen.findByRole('button', { name: 'Moi' }));
      await fillStory();
      fireEvent.click(publish());

      await waitFor(() => {
        expect(screen.getByRole('textbox', { name: 'Prénom' })).toHaveAttribute(
          'aria-invalid',
          'true',
        );
      });
      expect(screen.getByText('Ce champ est obligatoire.')).toBeVisible();
      expect(api.order).toEqual([]);
    });

    it('keeps the Person and everything written when the Memory is then refused, and says so', async () => {
      const api = fakeApi({
        empty: true,
        createStory: [() => problemResponse('INTERNAL_ERROR', 500)],
      });
      const { router } = renderApp(addMemoryPath(ADJI_ID));

      fireEvent.click(await screen.findByRole('button', { name: 'Moi' }));
      type('Prénom', 'Alice');
      await fillStory();
      fireEvent.click(publish());

      expect(
        await screen.findByText(
          "Alice fait maintenant partie de la famille, mais le souvenir n'a pas été publié. Tout ce que vous avez écrit est conservé : vous pouvez publier à nouveau.",
        ),
      ).toBeVisible();
      expect(screen.getByRole('alert')).toBeVisible();
      // The Family, reloaded with its new Person, resets nothing.
      await waitFor(() => {
        expect(screen.getByText('Alice fait maintenant partie de la famille.')).toBeVisible();
      });
      expect(screen.queryByRole('button', { name: 'Moi' })).not.toBeInTheDocument();
      expect(screen.getByRole('textbox', { name: 'Titre' })).toHaveValue('Le marché');
      expect(screen.getByRole('textbox', { name: 'Votre histoire' })).toHaveValue('Chaque samedi.');

      // Publishing again never creates the Person twice.
      fireEvent.click(publish());
      await waitFor(() => {
        expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/memories/${MEMORY_ID}`);
      });
      expect(api.order).toEqual(['person', 'memory', 'memory']);
      expect(api.stories[1]).toMatchObject({ relatedPersonIds: [NEW_ID] });
    });
  });

  describe('Add {typed name} in the related Persons', () => {
    async function addOnTheWay(text: string) {
      fireEvent.click(await screen.findByRole('button', { name: 'Ajouter une personne' }));
      const sheet = await screen.findByRole('dialog', { name: 'Choisir une personne' });
      fireEvent.change(
        within(sheet).getByRole('searchbox', { name: 'Rechercher dans la famille' }),
        {
          target: { value: text },
        },
      );
      fireEvent.click(await within(sheet).findByRole('button', { name: `Ajouter ${text}` }));
      return screen.findByRole('dialog', { name: 'Ajouter une personne' });
    }

    it('adds a Person not in the tree, created with the Memory, just before it', async () => {
      const api = fakeApi();
      renderApp(addMemoryPath(ADJI_ID));
      await fillStory();

      const sheet = await addOnTheWay('Rose Mbida');
      expect(within(sheet).getByRole('textbox', { name: 'Prénom' })).toHaveValue('Rose');
      expect(within(sheet).getByRole('textbox', { name: 'Nom' })).toHaveValue('Mbida');
      fireEvent.click(within(sheet).getByRole('button', { name: 'Ajouter cette personne' }));

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      const chosen = screen.getByRole('list', { name: 'Qui concerne ce souvenir ?' });
      expect(within(chosen).getByText('Rose Mbida')).toBeVisible();
      expect(within(chosen).getByText('À ajouter à la famille')).toBeVisible();
      // Nothing is created before publishing.
      expect(api.order).toEqual([]);

      fireEvent.click(publish());
      await waitFor(() => {
        expect(api.stories).toHaveLength(1);
      });
      expect(api.order).toEqual(['person', 'memory']);
      expect(api.persons[0]).toEqual({
        firstName: 'Rose',
        lastName: 'Mbida',
        isDeceased: false,
        linkToCurrentUser: false,
        confirmPossibleDuplicate: false,
      });
      expect(api.stories[0]).toMatchObject({ relatedPersonIds: [MARIE_ID, NEW_ID] });
    });

    it('removes a Person added on the way before publishing, without creating them', async () => {
      const api = fakeApi();
      renderApp(addMemoryPath(ADJI_ID));
      await fillStory();
      const sheet = await addOnTheWay('Rose');
      fireEvent.click(within(sheet).getByRole('button', { name: 'Ajouter cette personne' }));
      fireEvent.click(screen.getByRole('button', { name: 'Retirer Rose' }));
      fireEvent.click(publish());

      await waitFor(() => {
        expect(api.stories).toHaveLength(1);
      });
      expect(api.order).toEqual(['memory']);
    });

    it('offers a possible duplicate as a choice: the existing Person instead', async () => {
      const api = fakeApi({
        createPerson: [
          () => problemResponse('POSSIBLE_DUPLICATE', 409, { details: { candidates: [eloise] } }),
        ],
      });
      renderApp(addMemoryPath(ADJI_ID));
      await fillStory();
      const sheet = await addOnTheWay('Éloïse Ngo');
      fireEvent.click(within(sheet).getByRole('button', { name: 'Ajouter cette personne' }));
      fireEvent.click(publish());

      const duplicates = await screen.findByRole('alert', {
        name: 'Une personne semblable est déjà dans la famille',
      });
      expect(api.stories).toHaveLength(0);
      fireEvent.click(
        within(duplicates).getByRole('button', {
          name: 'Voir la personne existante : Éloïse Ngo',
        }),
      );

      await waitFor(() => {
        expect(api.stories).toHaveLength(1);
      });
      expect(api.persons).toHaveLength(1);
      expect(api.stories[0]).toMatchObject({ relatedPersonIds: [MARIE_ID, ELOISE_ID] });
    });

    it('offers a possible duplicate as a choice: Create anyway', async () => {
      const api = fakeApi({
        createPerson: [
          () => problemResponse('POSSIBLE_DUPLICATE', 409, { details: { candidates: [eloise] } }),
        ],
      });
      renderApp(addMemoryPath(ADJI_ID));
      await fillStory();
      const sheet = await addOnTheWay('Éloïse Ngo');
      fireEvent.click(within(sheet).getByRole('button', { name: 'Ajouter cette personne' }));
      fireEvent.click(publish());

      fireEvent.click(await screen.findByRole('button', { name: 'Créer quand même' }));
      await waitFor(() => {
        expect(api.stories).toHaveLength(1);
      });
      expect(api.persons[1]).toMatchObject({ firstName: 'Éloïse', confirmPossibleDuplicate: true });
      expect(api.stories[0]).toMatchObject({ relatedPersonIds: [MARIE_ID, NEW_ID] });
    });
  });

  describe('Family Home (SCREEN-002, OQ-066)', () => {
    it('shows no mutation action to a VIEWER', async () => {
      fetchMock.mockImplementation((input) => {
        const request = input as Request;
        const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
        if (path === '/me') {
          return Promise.resolve(
            jsonResponse({ id: 'u1', email: 'v@mbia.local', preferredLocale: 'fr' }),
          );
        }
        if (path === `/families/${ADJI_ID}`) {
          return Promise.resolve(
            jsonResponse({
              id: ADJI_ID,
              name: 'ADJI',
              myRole: 'VIEWER',
              myLinkedPersonId: MARIE_ID,
              stats: { personCount: 2, memoryCount: 1, activeMemberCount: 2 },
              limits: { maxPhotosPerMemory: 3 },
              version: 0,
              createdAt: '2026-09-25T10:00:00Z',
              updatedAt: '2026-09-25T10:00:00Z',
            }),
          );
        }
        if (path === `/families/${ADJI_ID}/story/years`) {
          return Promise.resolve(
            jsonResponse({ years: [{ year: 1975, memoryCount: 1 }], undatedMemoryCount: 0 }),
          );
        }
        return Promise.resolve(problemResponse('RESOURCE_NOT_FOUND', 404));
      });
      renderApp(familyHomePath(ADJI_ID));

      expect(await screen.findByRole('link', { name: '1975 · 1 souvenir' })).toBeVisible();
      expect(screen.getByRole('link', { name: "Voir l'arbre familial" })).toBeVisible();
      for (const name of [
        'Raconter un souvenir',
        'Ajouter un souvenir',
        'Ajouter une personne',
        'Ajouter un proche',
      ]) {
        expect(screen.queryByRole('link', { name })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
      }
    });
  });
});
