import { act, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
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

const FAMILY_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const TONY = '0b5c1f3e-1c1a-4a57-9a55-8f2f6c1d2e02';
const MARIE = '1b5c1f3e-1c1a-4a57-9a55-8f2f6c1d2e03';
const MEMORY = '2b5c1f3e-1c1a-4a57-9a55-8f2f6c1d2e04';

const me = { id: TONY, email: 'tony@mbia.local', displayName: 'Tony', preferredLocale: 'fr' };

function family(myRole = 'VIEWER') {
  return {
    id: FAMILY_ID,
    name: 'ADJI',
    myRole,
    stats: { personCount: 8, memoryCount: 1, activeMemberCount: 2 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function hoursAgo(hours: number) {
  return new Date(Date.now() - hours * 3600 * 1000).toISOString();
}

let sequence = 0;

/** One item of `listFamilyActivities`, alone and ACTIVE unless told otherwise. */
function activity(type: string, overrides: Record<string, unknown> = {}) {
  sequence += 1;
  return {
    id: `00000000-0000-4000-8000-${String(sequence).padStart(12, '0')}`,
    type,
    count: 1,
    resourceActive: true,
    actor: { userId: TONY, displayName: 'Tony', deleted: false },
    resourceType: 'PERSON',
    resourceId: MARIE,
    resourceIds: [MARIE],
    data: {},
    occurredAt: hoursAgo(2),
    ...overrides,
  };
}

function givenActivities(items: unknown[] | 'error') {
  fetchMock.mockImplementation((input) => Promise.resolve(answer(input as Request, items)));
}

function answer(request: Request, items: unknown[] | 'error') {
  const url = new URL(request.url);
  const path = url.pathname.replace(/^\/api\/v1/, '');
  if (path === '/me') return jsonResponse(me);
  if (path === `/families/${FAMILY_ID}`) return jsonResponse(family());
  if (path === `/families/${FAMILY_ID}/activities`) {
    expect(url.searchParams.get('size')).toBe('10');
    return items === 'error'
      ? jsonResponse({ code: 'INTERNAL_ERROR', status: 500 }, 500, 'application/problem+json')
      : jsonResponse({
          items,
          page: { page: 0, size: 10, totalElements: items.length, totalPages: 1 },
        });
  }
  return jsonResponse({ code: 'RESOURCE_NOT_FOUND', status: 404 }, 404, 'application/problem+json');
}

function renderHome() {
  const router = createMemoryRouter(routes, { initialEntries: [`/families/${FAMILY_ID}`] });
  render(
    <App
      router={router}
      queryClient={createQueryClient()}
      userManager={fakeUserManager(fakeOidcUser())}
    />,
  );
}

/** Waits until the activity was requested and its answer rendered. */
async function activityAnswered() {
  await waitFor(() => {
    expect(
      fetchMock.mock.calls.some(([input]) => (input as Request).url.includes('/activities')),
    ).toBe(true);
  });
  await act(() => new Promise((resolve) => setTimeout(resolve, 10)));
}

async function activitySection() {
  return screen.findByRole('region', { name: 'Activité récente' });
}

describe('Family Home recent activity (SCREEN-002, OQ-054)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  it('is not shown without any activity', async () => {
    givenActivities([]);
    renderHome();

    expect(await screen.findByText('8 personnes')).toBeInTheDocument();
    await activityAnswered();
    expect(screen.queryByRole('heading', { name: 'Activité récente' })).not.toBeInTheDocument();
  });

  it('is not shown when it cannot be read, and the rest of the home stays', async () => {
    givenActivities('error');
    renderHome();

    expect(await screen.findByText('8 personnes')).toBeInTheDocument();
    await activityAnswered();
    expect(screen.queryByRole('heading', { name: 'Activité récente' })).not.toBeInTheDocument();
  });

  it('shows a group as one line with who, what and when, without link, in French then English', async () => {
    givenActivities([
      activity('PERSON_CREATED', {
        count: 6,
        resourceIds: [MARIE, MARIE, MARIE, MARIE, MARIE, MARIE],
      }),
    ]);
    renderHome();

    const section = await activitySection();
    expect(await within(section).findByText('Tony a ajouté 6 personnes')).toBeInTheDocument();
    expect(within(section).getByText('il y a 2 heures')).toBeInTheDocument();
    expect(within(section).queryByRole('link')).not.toBeInTheDocument();

    await act(() => i18n.changeLanguage('en'));

    const english = screen.getByRole('region', { name: 'Recent activity' });
    expect(within(english).getByText('Tony added 6 people')).toBeInTheDocument();
    expect(within(english).getByText('2 hours ago')).toBeInTheDocument();
  });

  it('links a single ACTIVE Person or Memory, with the names recorded', async () => {
    givenActivities([
      activity('PERSON_CREATED', { data: { personDisplayName: 'Marie Ngo' } }),
      activity('MEMORY_CREATED', {
        resourceType: 'MEMORY',
        resourceId: MEMORY,
        resourceIds: [MEMORY],
        data: { memoryTitle: 'Le mariage' },
      }),
    ]);
    renderHome();

    const section = await activitySection();
    expect(
      await within(section).findByRole('link', { name: 'Tony a ajouté Marie Ngo' }),
    ).toHaveAttribute('href', `/families/${FAMILY_ID}/persons/${MARIE}`);
    expect(
      within(section).getByRole('link', { name: 'Tony a ajouté le souvenir « Le mariage »' }),
    ).toHaveAttribute('href', `/families/${FAMILY_ID}/memories/${MEMORY}`);
  });

  it('names an archived or merged item without link', async () => {
    givenActivities([
      activity('PERSON_ARCHIVED', { resourceActive: false, data: { personDisplayName: 'Marie' } }),
      activity('PERSON_CREATED', { resourceActive: false, data: { personDisplayName: 'Maria' } }),
      activity('MEMORY_CREATED', {
        resourceType: 'MEMORY',
        resourceId: MEMORY,
        resourceActive: false,
        data: { memoryTitle: 'Le mariage' },
      }),
    ]);
    renderHome();

    const section = await activitySection();
    expect(await within(section).findByText('Tony a archivé Marie')).toBeInTheDocument();
    expect(within(section).getByText('Tony a ajouté Maria')).toBeInTheDocument();
    expect(
      within(section).getByText('Tony a ajouté le souvenir « Le mariage »'),
    ).toBeInTheDocument();
    expect(within(section).queryByRole('link')).not.toBeInTheDocument();
  });

  it('never links a relationship or a member, and names a deleted actor "Former member"', async () => {
    givenActivities([
      activity('RELATIONSHIP_CREATED', {
        resourceType: 'RELATIONSHIP',
        data: {
          relationshipType: 'PARENT_OF',
          sourcePersonDisplayName: 'Paul',
          targetPersonDisplayName: 'Marie',
        },
      }),
      activity('INVITATION_ACCEPTED', {
        resourceType: 'MEMBERSHIP',
        resourceActive: false,
        actor: { userId: TONY, displayName: 'Awa', deleted: false },
        data: { memberDisplayName: 'Awa Mbida' },
      }),
      activity('MEMBER_REMOVED', {
        resourceType: 'MEMBERSHIP',
        resourceActive: false,
        actor: { userId: TONY, displayName: null, deleted: true },
        data: { memberDisplayName: 'Rose' },
      }),
    ]);
    renderHome();

    const section = await activitySection();
    expect(await within(section).findByText('Tony a relié Paul et Marie')).toBeInTheDocument();
    expect(within(section).getByText('Awa Mbida a rejoint la famille')).toBeInTheDocument();
    expect(
      within(section).getByText('Ancien membre a retiré Rose de la famille'),
    ).toBeInTheDocument();
    expect(within(section).queryByRole('link')).not.toBeInTheDocument();
    expect(section).not.toHaveTextContent('PARENT_OF');
  });

  it('never shows a type it does not know', async () => {
    givenActivities([
      activity('PERSON_UPDATED'),
      activity('PERSON_CREATED', { data: { personDisplayName: 'Marie' } }),
    ]);
    renderHome();

    const section = await activitySection();
    expect(await within(section).findAllByRole('listitem')).toHaveLength(1);
    expect(section).not.toHaveTextContent('PERSON_UPDATED');
  });
});
