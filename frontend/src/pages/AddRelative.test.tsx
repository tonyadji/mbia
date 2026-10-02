import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { addRelativePath } from '../persons/relatives';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';

// The API client captures `fetch` when it is created: replace it before any import.
const fetchMock = vi.hoisted(() => {
  const mock = vi.fn<typeof fetch>();
  globalThis.fetch = mock;
  return mock;
});

function jsonResponse(body: unknown, status = 200, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

function problemResponse(code: string, status: number, details?: Record<string, unknown>) {
  return jsonResponse(
    { code, status, title: code, detail: 'raw server detail', details },
    status,
    'application/problem+json',
  );
}

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const MARIE_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const PAUL_ID = '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d';
const PROFILE = `/families/${ADJI_ID}/persons/${MARIE_ID}`;

function family(overrides: Record<string, unknown> = {}) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole: 'ADMIN',
    myLinkedPersonId: null,
    stats: { personCount: 1, memoryCount: 0, activeMemberCount: 1 },
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
    relationshipToCurrentUser: null,
    profilePictureUrl: null,
    version: 0,
    biography: null,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

const paul = person({ id: PAUL_ID, firstName: 'Paul', displayName: 'Paul Adji', gender: 'MALE' });

function relationship(body: Record<string, unknown>) {
  return jsonResponse(
    {
      id: 'r1',
      familyId: ADJI_ID,
      status: 'ACTIVE',
      warnings: [],
      version: 0,
      createdAt: '',
      ...body,
    },
    201,
  );
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`); relationship answers in order. */
function fakeApi({
  familyBody = family(),
  anchor = person(),
  createdPerson = paul,
  relationships = [],
  personAnswers = [],
  patch,
  locale = 'fr',
}: {
  locale?: string;
  familyBody?: Record<string, unknown>;
  anchor?: Record<string, unknown>;
  createdPerson?: Record<string, unknown>;
  relationships?: ((body: Record<string, unknown>) => Response)[];
  /** Answers to the Person creations, in order; then `createdPerson`. */
  personAnswers?: (() => Response)[];
  patch?: Handler;
} = {}) {
  const requests: { method: string; path: string; headers: Headers; body: unknown }[] = [];
  let relationshipCalls = 0;
  let personCalls = 0;
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    const text = await request.clone().text();
    const body: unknown = text ? JSON.parse(text) : null;
    requests.push({ method: request.method, path, headers: request.headers, body });
    const key = `${request.method} ${path}`;
    if (key === 'GET /me') {
      return jsonResponse({ id: 'u1', email: 'alice@mbia.local', preferredLocale: locale });
    }
    if (key === `GET /families/${ADJI_ID}`) return jsonResponse(familyBody);
    if (key === `GET /families/${ADJI_ID}/persons/${MARIE_ID}`) return jsonResponse(anchor);
    if (key === `POST /families/${ADJI_ID}/persons`) {
      const answer = personAnswers[personCalls];
      personCalls += 1;
      return answer ? answer() : jsonResponse(createdPerson, 201);
    }
    if (key === `PATCH /families/${ADJI_ID}/persons/${PAUL_ID}` && patch) return patch(request);
    if (key === `POST /families/${ADJI_ID}/relationships`) {
      const answer = relationships[relationshipCalls] ?? relationship;
      relationshipCalls += 1;
      return answer(body as Record<string, unknown>);
    }
    return problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return {
    sent: (method: string, suffix: string) =>
      requests.filter((request) => request.method === method && request.path.endsWith(suffix)),
  };
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

async function fillAndSubmit(firstName: string) {
  fireEvent.change(await screen.findByRole('textbox', { name: 'Prénom' }), {
    target: { value: firstName },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Ajouter à la famille' }));
}

const warning = (code: string, parentBirthYear: number, childBirthYear: number) =>
  problemResponse('RELATIONSHIP_WARNING_CONFIRMATION_REQUIRED', 422, {
    warnings: [{ code, context: { parentBirthYear, childBirthYear } }],
  });

describe('Add Relative (SCREEN-004, family-tree-ux.md §9)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('from the profile', () => {
    it('offers the gendered choices to an ADMIN or CONTRIBUTOR', async () => {
      fakeApi();
      renderApp(PROFILE);

      fireEvent.click(await screen.findByRole('button', { name: 'Ajouter un proche' }));

      expect(screen.getByText('Ajouter un parent')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Père' })).toHaveAttribute(
        'href',
        addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'),
      );
      for (const name of ['Mère', 'Autre parent', 'Fils', 'Fille', 'Enfant']) {
        expect(screen.getByRole('link', { name })).toBeInTheDocument();
      }
      expect(screen.getByRole('link', { name: 'Ajouter un ou une partenaire' })).toHaveAttribute(
        'href',
        addRelativePath(ADJI_ID, MARIE_ID, 'PARTNER', 'profile'),
      );
    });

    it('does not offer it to a VIEWER', async () => {
      fakeApi({ familyBody: family({ myRole: 'VIEWER' }) });
      renderApp(PROFILE);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Marie Adji' }),
      ).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Ajouter un proche' })).not.toBeInTheDocument();
    });
  });

  it('adds a father: new Person with MALE preset, then new PARENT_OF Marie', async () => {
    const api = fakeApi();
    const { router } = renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Ajouter le père de Marie Adji' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Genre', hidden: true })).toHaveValue('MALE');
    await fillAndSubmit('Paul');

    expect(
      await screen.findByText('Paul Adji a été ajouté à la famille et relié à Marie Adji.'),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PROFILE);
    expect(api.sent('POST', '/persons')[0]?.body).toMatchObject({
      firstName: 'Paul',
      gender: 'MALE',
    });
    expect(api.sent('POST', '/relationships').map((request) => request.body)).toEqual([
      {
        type: 'PARENT_OF',
        sourcePersonId: PAUL_ID,
        targetPersonId: MARIE_ID,
        confirmWarnings: false,
      },
    ]);
  });

  it.each([
    ['DAUGHTER', 'Ajouter une fille de Marie Adji', 'FEMALE', 'PARENT_OF', MARIE_ID, PAUL_ID],
    ['CHILD', 'Ajouter un enfant de Marie Adji', 'UNKNOWN', 'PARENT_OF', MARIE_ID, PAUL_ID],
    ['PARENT', 'Ajouter un parent de Marie Adji', 'UNKNOWN', 'PARENT_OF', PAUL_ID, MARIE_ID],
    [
      'PARTNER',
      'Ajouter un ou une partenaire de Marie Adji',
      'UNKNOWN',
      'PARTNER_OF',
      MARIE_ID,
      PAUL_ID,
    ],
  ] as const)(
    'adds a %s with the derived relationship',
    async (relation, title, gender, type, source, target) => {
      const api = fakeApi();
      renderApp(addRelativePath(ADJI_ID, MARIE_ID, relation, 'profile'));

      expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: 'Genre', hidden: true })).toHaveValue(gender);
      await fillAndSubmit('Paul');

      await screen.findByText('Paul Adji a été ajouté à la famille et relié à Marie Adji.');
      expect(api.sent('POST', '/relationships')[0]?.body).toEqual({
        type,
        sourcePersonId: source,
        targetPersonId: target,
        confirmWarnings: false,
      });
    },
  );

  it('speaks of "my" relative when the anchor is the current User', async () => {
    fakeApi({ anchor: person({ relationshipToCurrentUser: 'SELF' }) });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'MOTHER', 'home'));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Ajouter ma mère' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Genre', hidden: true })).toHaveValue('FEMALE');
  });

  it('explains a date warning and adds the link anyway on confirmation', async () => {
    const api = fakeApi({
      relationships: [() => warning('PARENT_BORN_AFTER_CHILD', 1995, 1990), relationship],
    });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'));
    await fillAndSubmit('Paul');

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('Ces dates semblent inhabituelles')).toBeInTheDocument();
    expect(
      within(alert).getByText(
        "Paul Adji deviendrait le parent de Marie Adji, alors que son année de naissance (1995) n'est pas antérieure à celle de Marie Adji (1990).",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajouter à la famille' })).not.toBeInTheDocument();

    fireEvent.click(within(alert).getByRole('button', { name: 'Ajouter le lien quand même' }));

    await screen.findByText('Paul Adji a été ajouté à la famille et relié à Marie Adji.');
    expect(api.sent('POST', '/persons')).toHaveLength(1);
    expect(api.sent('POST', '/relationships').map((request) => request.body)).toEqual([
      expect.objectContaining({ confirmWarnings: false }),
      expect.objectContaining({ confirmWarnings: true }),
    ]);
  });

  it('shows the parent age of an implausible age warning', async () => {
    fakeApi({ relationships: [() => warning('IMPLAUSIBLE_PARENT_AGE', 1990, 1995)] });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'SON', 'profile'));
    await fillAndSubmit('Paul');

    expect(
      await screen.findByText(
        'Marie Adji aurait eu 5 ans à la naissance de Paul Adji (années de naissance : 1990 et 1995).',
      ),
    ).toBeInTheDocument();
  });

  it('corrects the new Person, then retries the link without creating them twice', async () => {
    const api = fakeApi({
      relationships: [() => warning('PARENT_BORN_AFTER_CHILD', 1995, 1990), relationship],
      patch: () => jsonResponse({ ...paul, version: 1 }),
    });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'));
    await fillAndSubmit('Paul');

    fireEvent.click(await screen.findByRole('button', { name: 'Corriger les informations' }));
    expect(screen.getByRole('button', { name: "Plus d'informations" })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Ajouter à la famille' }));

    await screen.findByText('Paul Adji a été ajouté à la famille et relié à Marie Adji.');
    expect(api.sent('POST', '/persons')).toHaveLength(1);
    const [update] = api.sent('PATCH', `/persons/${PAUL_ID}`);
    expect(update?.headers.get('If-Match')).toBe('"0"');
    expect(api.sent('POST', '/relationships')).toHaveLength(2);
  });

  it('says the Person was added but not linked when the link is refused', async () => {
    fakeApi({ relationships: [() => problemResponse('PERSON_NOT_ACTIVE', 409)] });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'));
    await fillAndSubmit('Paul');

    const alert = await screen.findByRole('alert');
    expect(
      within(alert).getByText(
        "Paul Adji a été ajouté à la famille, mais n'est pas relié à Marie Adji. Vous pourrez créer ce lien plus tard depuis son profil.",
      ),
    ).toBeInTheDocument();
    expect(
      within(alert).getByText(
        'Cette personne a été archivée ou fusionnée : elle ne peut plus recevoir de nouveau lien.',
      ),
    ).toBeInTheDocument();
    expect(within(alert).getByRole('link', { name: 'Voir son profil' })).toHaveAttribute(
      'href',
      `/families/${ADJI_ID}/persons/${PAUL_ID}`,
    );
    expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
  });

  it('translates every hard block in family language, in English too', async () => {
    await act(() => i18n.changeLanguage('en'));
    fakeApi({
      locale: 'en',
      relationships: [() => problemResponse('RELATIONSHIP_CREATES_CYCLE', 409)],
    });
    renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'DAUGHTER', 'profile'));

    expect(
      await screen.findByRole('heading', { level: 1, name: "Add Marie Adji's daughter" }),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: 'First name' }), {
      target: { value: 'Paul' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Add to the family' }));

    expect(
      await screen.findByText(
        'This link cannot be added because it would make someone one of their own ancestors.',
      ),
    ).toBeInTheDocument();
  });

  describe('from the Family home', () => {
    it('offers my father, my mother, my partner, my child and someone else', async () => {
      fakeApi({ familyBody: family({ myLinkedPersonId: MARIE_ID }) });
      renderApp(`/families/${ADJI_ID}`);

      fireEvent.click(await screen.findByRole('button', { name: 'Ajouter un proche' }));

      expect(screen.getByRole('link', { name: 'Mon père' })).toHaveAttribute(
        'href',
        addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'home'),
      );
      expect(screen.getByRole('link', { name: 'Ma mère' })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Mon ou ma partenaire' })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Mon enfant' })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: "Quelqu'un d'autre" })).toHaveAttribute(
        'href',
        `/families/${ADJI_ID}/persons/new`,
      );
      expect(screen.queryByRole('link', { name: 'Ajouter une personne' })).not.toBeInTheDocument();
    });

    it('keeps Add a person when the User is not in the tree', async () => {
      fakeApi();
      renderApp(`/families/${ADJI_ID}`);

      expect(await screen.findByRole('link', { name: 'Ajouter une personne' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Ajouter un proche' })).not.toBeInTheDocument();
    });

    it('returns home with the new relative linked', async () => {
      fakeApi({
        familyBody: family({ myLinkedPersonId: MARIE_ID }),
        anchor: person({ relationshipToCurrentUser: 'SELF' }),
      });
      const { router } = renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'home'));
      await fillAndSubmit('Paul');

      expect(
        await screen.findByText('Paul Adji a été ajouté à la famille et relié à Marie Adji.'),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}`);
    });
  });

  describe('possible duplicate (person-relationships-collaboration.md §4.1)', () => {
    const PAULO_ID = 'b0000000-0000-4000-8000-000000000001';
    const existingPaul = person({
      id: PAULO_ID,
      firstName: 'Paul',
      displayName: 'Paul Adji',
      gender: 'MALE',
      birth: { precision: 'YEAR_ONLY', year: 1960 },
    });
    const duplicate = () =>
      problemResponse('POSSIBLE_DUPLICATE', 409, { candidates: [existingPaul] });

    it('shows the similar Person, then creates anyway on request', async () => {
      const api = fakeApi({ personAnswers: [duplicate] });
      renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'));
      await fillAndSubmit('Paul');

      const alert = await screen.findByRole('alert', {
        name: 'Une personne semblable est déjà dans la famille',
      });
      expect(within(alert).getByText('Paul Adji')).toBeInTheDocument();
      expect(within(alert).getByText('1960 –')).toBeInTheDocument();
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
      expect(api.sent('POST', '/relationships')).toHaveLength(0);

      fireEvent.click(within(alert).getByRole('button', { name: 'Créer quand même' }));

      expect(
        await screen.findByText('Paul Adji a été ajouté à la famille et relié à Marie Adji.'),
      ).toBeInTheDocument();
      expect(api.sent('POST', '/persons').map((request) => request.body)).toEqual([
        expect.objectContaining({ firstName: 'Paul', confirmPossibleDuplicate: false }),
        expect.objectContaining({ firstName: 'Paul', confirmPossibleDuplicate: true }),
      ]);
      expect(api.sent('POST', '/relationships')[0]?.body).toMatchObject({
        sourcePersonId: PAUL_ID,
        targetPersonId: MARIE_ID,
      });
    });

    it('links the existing Person instead when the User views them', async () => {
      const api = fakeApi({ personAnswers: [duplicate] });
      renderApp(addRelativePath(ADJI_ID, MARIE_ID, 'FATHER', 'profile'));
      await fillAndSubmit('Paul');

      fireEvent.click(
        await screen.findByRole('button', { name: 'Voir la personne existante : Paul Adji' }),
      );
      expect(screen.getByRole('heading', { name: 'Personne choisie' })).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Relier Paul Adji à Marie Adji' }));

      expect(
        await screen.findByText('Paul Adji est maintenant relié à Marie Adji.'),
      ).toBeInTheDocument();
      expect(api.sent('POST', '/persons')).toHaveLength(1);
      expect(api.sent('POST', '/relationships')[0]?.body).toMatchObject({
        type: 'PARENT_OF',
        sourcePersonId: PAULO_ID,
        targetPersonId: MARIE_ID,
      });
    });

    it('opens the existing profile outside relative mode', async () => {
      const api = fakeApi({ personAnswers: [duplicate] });
      const { router } = renderApp(`/families/${ADJI_ID}/persons/new`);
      await fillAndSubmit('Paul');

      fireEvent.click(
        await screen.findByRole('button', { name: 'Voir la personne existante : Paul Adji' }),
      );

      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/persons/${PAULO_ID}`);
      expect(api.sent('POST', '/persons')).toHaveLength(1);
    });

    it('speaks English too', async () => {
      fakeApi({ locale: 'en', personAnswers: [duplicate] });
      await act(() => i18n.changeLanguage('en'));
      renderApp(`/families/${ADJI_ID}/persons/new`);
      fireEvent.change(await screen.findByRole('textbox', { name: 'First name' }), {
        target: { value: 'Paul' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Add to the family' }));

      expect(
        await screen.findByRole('alert', { name: 'A similar person is already in the family' }),
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Create anyway' })).toBeInTheDocument();
    });
  });
});
