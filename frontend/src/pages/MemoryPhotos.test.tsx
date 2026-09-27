import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { familyMemoriesPath } from './FamilyMemoriesPage';
import { memoryPath } from './MemoryPage';
import { personPath } from './PersonProfilePage';

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
const MEMORY_ID = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f';
const PROFILE = personPath(ADJI_ID, AWA_ID);
const MEMORY = memoryPath(ADJI_ID, MEMORY_ID);
const MEMORIES = familyMemoriesPath(ADJI_ID);

function family(myRole: string) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    myLinkedPersonId: null,
    stats: { personCount: 1, memoryCount: 1, activeMemberCount: 1 },
    limits: { maxPhotosPerMemory: 3 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

function person() {
  return {
    id: AWA_ID,
    familyId: ADJI_ID,
    firstName: 'Awa',
    lastName: 'Ngo',
    displayName: 'Awa Ngo',
    gender: 'FEMALE',
    birth: { precision: 'YEAR_ONLY', year: 1930 },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    relationshipToCurrentUser: null,
    profilePictureUrl: null,
    version: 0,
    biography: null,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

/** A photo whose URLs carry `sig`, the signature of one response. */
function photo(n: number, overrides: Record<string, unknown> = {}, sig = 1) {
  return {
    mediaAssetId: `00000000-0000-4000-8000-00000000000${String(n)}`,
    caption: null,
    takenAt: { precision: 'UNKNOWN' },
    url: `http://localhost:9000/display-${String(n)}?sig=${String(sig)}`,
    thumbnailUrl: `http://localhost:9000/thumb-${String(n)}?sig=${String(sig)}`,
    widthPx: 2048,
    heightPx: 1365,
    ...overrides,
  };
}

function threePhotos(sig = 1) {
  return [
    photo(1, { caption: 'Maman au marché' }, sig),
    photo(
      2,
      { takenAt: { precision: 'YEAR_ONLY', year: 1975 }, widthPx: 1365, heightPx: 2048 },
      sig,
    ),
    photo(3, { caption: '   ', takenAt: { precision: 'EXACT', date: '1980-03-12' } }, sig),
  ];
}

function memory(overrides: Record<string, unknown> = {}) {
  return {
    id: MEMORY_ID,
    familyId: ADJI_ID,
    type: 'STORY',
    photos: threePhotos(),
    status: 'ACTIVE',
    title: 'Le marché de Yaoundé',
    content: 'Grand-mère vendait du plantain.',
    relatedPersons: [{ id: AWA_ID, displayName: 'Awa Ngo', status: 'ACTIVE' }],
    createdBy: { userId: 'u1', displayName: 'Tony', deleted: false },
    createdAt: '2026-09-20T10:00:00Z',
    updatedAt: '2026-09-20T10:00:00Z',
    version: 0,
    ...overrides,
  };
}

function page(items: unknown[]) {
  return {
    items,
    page: { page: 0, size: 20, totalElements: items.length, totalPages: 1 },
  };
}

type Handler = () => Response;

/** A fake API answering by method and path (after `/api/v1`), counting each call. */
function fakeApi({
  role = 'ADMIN',
  getMemory = () => jsonResponse(memory()),
  memories = () => jsonResponse(page([memory()])),
  locale = 'fr',
}: { role?: string; getMemory?: Handler; memories?: Handler; locale?: string } = {}) {
  const calls: string[] = [];
  const handlers: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role)),
    [`GET /families/${ADJI_ID}/persons/${AWA_ID}`]: () => jsonResponse(person()),
    [`GET /families/${ADJI_ID}/tree`]: () =>
      jsonResponse({ focusPersonId: AWA_ID, nodes: [], edges: [] }),
    [`GET /families/${ADJI_ID}/persons/${AWA_ID}/archived-relationships`]: () => jsonResponse([]),
    [`GET /families/${ADJI_ID}/persons/${AWA_ID}/memories`]: memories,
    [`GET /families/${ADJI_ID}/memories`]: memories,
    [`GET /families/${ADJI_ID}/memories/${MEMORY_ID}`]: getMemory,
  };
  fetchMock.mockImplementation((input) => {
    const request = input as Request;
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    calls.push(`${request.method} ${path}`);
    if (request.method === 'GET' && path === '/me') {
      return Promise.resolve(
        jsonResponse({ id: 'u1', email: 'tony@mbia.local', preferredLocale: locale }),
      );
    }
    const handler = handlers[`${request.method} ${path}`];
    return Promise.resolve(handler ? handler() : problemResponse('RESOURCE_NOT_FOUND', 404));
  });
  return { count: (call: string) => calls.filter((c) => c === call).length };
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
}

async function photoList() {
  return screen.findByRole('list', { name: 'Photos' });
}

async function thumbnail(name: string) {
  return within(await photoList()).getByRole('button', { name });
}

describe('Memory photos', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    await act(() => i18n.changeLanguage('fr'));
  });

  describe('on the Memory (SCREEN-013)', () => {
    it('shows the photos in order as a grid of thumbnails named by their caption', async () => {
      fakeApi();
      renderApp(MEMORY);

      const items = within(await photoList()).getAllByRole('listitem');
      expect(items).toHaveLength(3);
      const thumbnails = items.map((item) => within(item).getByRole('button'));
      // The caption, otherwise "Photo {n} of {count}"; a blank caption counts as none.
      expect(thumbnails.map((thumbnail) => thumbnail.getAttribute('aria-label'))).toEqual([
        'Maman au marché',
        'Photo 2 sur 3',
        'Photo 3 sur 3',
      ]);
      const images = items.map((item) => item.querySelector('img'));
      expect(images.map((image) => image?.getAttribute('src'))).toEqual([
        'http://localhost:9000/thumb-1?sig=1',
        'http://localhost:9000/thumb-2?sig=1',
        'http://localhost:9000/thumb-3?sig=1',
      ]);
      for (const image of images) {
        expect(image).toHaveAttribute('loading', 'lazy');
        expect(image).toHaveClass('object-cover');
      }
      // No text in the grid: caption and date are in the viewer.
      expect(screen.queryByText('Prise : 1975')).not.toBeInTheDocument();
      expect(document.querySelector('figcaption')).toBeNull();
    });

    it('opens the viewer on the tapped photo, with its caption, date and position', async () => {
      fakeApi();
      renderApp(MEMORY);

      fireEvent.click(await thumbnail('Photo 2 sur 3'));

      const viewer = screen.getByRole('dialog', { name: 'Photo 2 sur 3' });
      expect(within(viewer).getByRole('img', { name: 'Photo 2 sur 3' })).toHaveAttribute(
        'src',
        'http://localhost:9000/display-2?sig=1',
      );
      expect(within(viewer).getByText('Prise : 1975')).toBeInTheDocument();
      expect(viewer).toHaveFocus();
      for (const name of ['Fermer', 'Photo précédente', 'Photo suivante']) {
        expect(within(viewer).getByRole('button', { name })).toBeEnabled();
      }
    });

    it('moves between the photos with the buttons, never past the first or the last', async () => {
      fakeApi();
      renderApp(MEMORY);
      fireEvent.click(await thumbnail('Maman au marché'));

      const viewer = screen.getByRole('dialog');
      const previous = within(viewer).getByRole('button', { name: 'Photo précédente' });
      const next = within(viewer).getByRole('button', { name: 'Photo suivante' });
      expect(viewer).toHaveAccessibleName('Photo 1 sur 3');
      expect(within(viewer).getByText('Maman au marché')).toBeInTheDocument();
      expect(previous).toBeDisabled();

      fireEvent.click(next);
      fireEvent.click(next);
      expect(viewer).toHaveAccessibleName('Photo 3 sur 3');
      expect(within(viewer).getByText('Prise : 12 mars 1980')).toBeInTheDocument();
      expect(next).toBeDisabled();
      fireEvent.click(next);
      expect(viewer).toHaveAccessibleName('Photo 3 sur 3');

      fireEvent.click(previous);
      expect(viewer).toHaveAccessibleName('Photo 2 sur 3');
    });

    it('moves with a horizontal swipe and the arrow keys', async () => {
      fakeApi();
      renderApp(MEMORY);
      fireEvent.click(await thumbnail('Maman au marché'));
      const viewer = screen.getByRole('dialog');
      const image = within(viewer).getByRole('img');
      const stage = image.parentElement;
      if (stage === null) throw new Error('no stage');

      // A swipe to the left shows the next photo.
      fireEvent.pointerDown(stage, { clientX: 300, clientY: 400 });
      fireEvent.pointerUp(stage, { clientX: 150, clientY: 410 });
      expect(viewer).toHaveAccessibleName('Photo 2 sur 3');
      // A short or mostly vertical move is not a swipe.
      fireEvent.pointerDown(stage, { clientX: 100, clientY: 400 });
      fireEvent.pointerUp(stage, { clientX: 130, clientY: 400 });
      fireEvent.pointerDown(stage, { clientX: 100, clientY: 100 });
      fireEvent.pointerUp(stage, { clientX: 180, clientY: 400 });
      expect(viewer).toHaveAccessibleName('Photo 2 sur 3');
      // A swipe to the right shows the previous photo, and stops at the first.
      fireEvent.pointerDown(stage, { clientX: 100, clientY: 400 });
      fireEvent.pointerUp(stage, { clientX: 250, clientY: 400 });
      fireEvent.pointerDown(stage, { clientX: 100, clientY: 400 });
      fireEvent.pointerUp(stage, { clientX: 250, clientY: 400 });
      expect(viewer).toHaveAccessibleName('Photo 1 sur 3');

      fireEvent.keyDown(document, { key: 'ArrowRight' });
      fireEvent.keyDown(document, { key: 'ArrowRight' });
      fireEvent.keyDown(document, { key: 'ArrowRight' });
      expect(viewer).toHaveAccessibleName('Photo 3 sur 3');
      fireEvent.keyDown(document, { key: 'ArrowLeft' });
      expect(viewer).toHaveAccessibleName('Photo 2 sur 3');
    });

    it('closes with Close or Escape, focus back on the thumbnail it was opened from', async () => {
      fakeApi();
      renderApp(MEMORY);
      const second = await thumbnail('Photo 2 sur 3');
      second.focus();
      fireEvent.click(second);
      fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Fermer' }));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(second).toHaveFocus();

      const third = screen.getByRole('button', { name: 'Photo 3 sur 3' });
      third.focus();
      fireEvent.click(third);
      fireEvent.keyDown(document, { key: 'Escape' });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(third).toHaveFocus();
    });

    it('keeps the focus inside the viewer on Tab', async () => {
      fakeApi();
      renderApp(MEMORY);
      fireEvent.click(await thumbnail('Photo 2 sur 3'));
      const viewer = screen.getByRole('dialog');
      const close = within(viewer).getByRole('button', { name: 'Fermer' });
      const next = within(viewer).getByRole('button', { name: 'Photo suivante' });

      next.focus();
      fireEvent.keyDown(document, { key: 'Tab' });
      expect(close).toHaveFocus();
      fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
      expect(next).toHaveFocus();
    });

    it('writes the names, the position and the taken date in English', async () => {
      await act(() => i18n.changeLanguage('en'));
      fakeApi({ locale: 'en' });
      renderApp(MEMORY);

      const thumbnails = within(await photoList()).getAllByRole('button');
      expect(thumbnails.map((button) => button.getAttribute('aria-label'))).toEqual([
        'Maman au marché',
        'Photo 2 of 3',
        'Photo 3 of 3',
      ]);
      fireEvent.click(screen.getByRole('button', { name: 'Photo 3 of 3' }));
      const viewer = screen.getByRole('dialog', { name: 'Photo 3 of 3' });
      expect(within(viewer).getByText('Taken: March 12, 1980')).toBeInTheDocument();
      for (const name of ['Close', 'Previous photo', 'Next photo']) {
        expect(within(viewer).getByRole('button', { name })).toBeInTheDocument();
      }
    });

    it('shows neither caption nor date in the viewer for a photo without them', async () => {
      fakeApi({ getMemory: () => jsonResponse(memory({ photos: [photo(1)] })) });
      renderApp(MEMORY);

      fireEvent.click(await thumbnail('Photo 1 sur 1'));
      const viewer = screen.getByRole('dialog', { name: 'Photo 1 sur 1' });
      expect(within(viewer).queryByText(/Prise/)).not.toBeInTheDocument();
      expect(within(viewer).getByRole('button', { name: 'Photo précédente' })).toBeDisabled();
      expect(within(viewer).getByRole('button', { name: 'Photo suivante' })).toBeDisabled();
    });

    it('places the photos after the title and before the text', async () => {
      fakeApi();
      renderApp(MEMORY);

      const list = await photoList();
      const title = screen.getByRole('heading', { level: 1, name: 'Le marché de Yaoundé' });
      const text = screen.getByText('Grand-mère vendait du plantain.');
      expect(title.compareDocumentPosition(list) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(list.compareDocumentPosition(text) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('shows a Memory without text with its title and photos only', async () => {
      fakeApi({ getMemory: () => jsonResponse(memory({ content: null })) });
      renderApp(MEMORY);

      const list = await photoList();
      const article = list.closest('article');
      if (article === null) throw new Error('no article');
      // No empty paragraph between the photos and the Persons.
      expect(article.querySelectorAll('p.whitespace-pre-wrap')).toHaveLength(0);
    });

    it('shows no photo list for a Memory without photo', async () => {
      fakeApi({ getMemory: () => jsonResponse(memory({ photos: [] })) });
      renderApp(MEMORY);

      expect(await screen.findByText('Grand-mère vendait du plantain.')).toBeInTheDocument();
      expect(screen.queryByRole('list', { name: 'Photos' })).not.toBeInTheDocument();
    });

    it('lets a VIEWER browse the photos, without any other action', async () => {
      fakeApi({ role: 'VIEWER' });
      renderApp(MEMORY);

      expect(within(await photoList()).getAllByRole('button')).toHaveLength(3);
      expect(screen.queryByRole('link', { name: 'Modifier' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Archiver' })).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Maman au marché' }));
      const viewer = screen.getByRole('dialog');
      expect(
        within(viewer)
          .getAllByRole('button')
          .map((button) => button.getAttribute('aria-label')),
      ).toEqual(['Fermer', 'Photo précédente', 'Photo suivante']);
      expect(within(viewer).queryAllByRole('link')).toHaveLength(0);
    });

    it('shows a thumbnail again with a fresh URL after its URL expired', async () => {
      let sig = 1;
      const api = fakeApi({
        getMemory: () => jsonResponse(memory({ photos: threePhotos(sig) })),
      });
      renderApp(MEMORY);
      const getMemory = `GET /families/${ADJI_ID}/memories/${MEMORY_ID}`;

      const expired = (await thumbnail('Maman au marché')).querySelector('img');
      if (expired === null) throw new Error('no thumbnail');
      expect(api.count(getMemory)).toBe(1);
      sig = 2;
      // The pre-signed URL expired: S3 answers 403 and the image fails to load.
      fireEvent.error(expired);

      await waitFor(() => {
        expect(
          screen.getByRole('button', { name: 'Maman au marché' }).querySelector('img'),
        ).toHaveAttribute('src', 'http://localhost:9000/thumb-1?sig=2');
      });
      expect(api.count(getMemory)).toBe(2);

      // The fresh URL fails too: the place of the photo says so, without reloading again.
      const fresh = screen.getByRole('button', { name: 'Maman au marché' }).querySelector('img');
      if (fresh === null) throw new Error('no thumbnail');
      fireEvent.error(fresh);
      const button = screen.getByRole('button', { name: 'Maman au marché' });
      expect(
        await within(button).findByText('Cette photo ne peut pas être affichée pour le moment.'),
      ).toBeInTheDocument();
      expect(api.count(getMemory)).toBe(2);
    });

    it('shows the photo of the viewer again with a fresh URL after its URL expired', async () => {
      let sig = 1;
      const api = fakeApi({
        getMemory: () => jsonResponse(memory({ photos: threePhotos(sig) })),
      });
      renderApp(MEMORY);
      const getMemory = `GET /families/${ADJI_ID}/memories/${MEMORY_ID}`;
      fireEvent.click(await thumbnail('Photo 2 sur 3'));

      sig = 2;
      fireEvent.error(within(screen.getByRole('dialog')).getByRole('img'));

      await waitFor(() => {
        expect(within(screen.getByRole('dialog')).getByRole('img')).toHaveAttribute(
          'src',
          'http://localhost:9000/display-2?sig=2',
        );
      });
      expect(api.count(getMemory)).toBe(2);
      // Still on the same photo.
      expect(screen.getByRole('dialog')).toHaveAccessibleName('Photo 2 sur 3');
    });
  });

  describe('on the Memory cards (SCREEN-005, SCREEN-015)', () => {
    it('shows the thumbnail of the first photo, decorative, on the profile', async () => {
      fakeApi();
      renderApp(PROFILE);

      const section = await screen.findByRole('region', { name: 'Souvenirs' });
      const card = await within(section).findByRole('link', { name: /Le marché de Yaoundé/ });
      const thumbnail = card.querySelector('img');
      expect(thumbnail).toHaveAttribute('src', 'http://localhost:9000/thumb-1?sig=1');
      expect(thumbnail).toHaveAttribute('alt', '');
      expect(thumbnail).toHaveAttribute('loading', 'lazy');
      // Decorative: the title names the card.
      expect(within(card).queryByRole('img')).not.toBeInTheDocument();
      expect(card).toHaveAccessibleName(/^Le marché de Yaoundé/);
    });

    it('lists the Family Memories with thumbnails, a text-less one by its title only, no filter', async () => {
      const textless = memory({
        id: '00000000-0000-4000-8000-000000000099',
        title: 'Mamie en 1975',
        content: null,
        photos: [photo(9)],
      });
      const withoutPhoto = memory({
        id: '00000000-0000-4000-8000-000000000098',
        title: 'Une histoire',
        photos: [],
      });
      fakeApi({ memories: () => jsonResponse(page([textless, memory(), withoutPhoto])) });
      renderApp(MEMORIES);

      const list = await screen.findByRole('list', { name: 'Souvenirs' });
      const cards = within(list).getAllByRole('link');
      expect(cards.map((card) => card.querySelector('img')?.getAttribute('src') ?? null)).toEqual([
        'http://localhost:9000/thumb-9?sig=1',
        'http://localhost:9000/thumb-1?sig=1',
        null,
      ]);
      expect(cards[0]).toHaveTextContent(/^Mamie en 1975$/);
      expect(cards[0]?.querySelectorAll('.line-clamp-3')).toHaveLength(0);
      // No filter (OQ-042).
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
      expect(screen.queryByRole('radio')).not.toBeInTheDocument();
      expect(screen.queryByRole('tab')).not.toBeInTheDocument();
      expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    });

    it('shows a thumbnail again with a fresh URL after its URL expired', async () => {
      let sig = 1;
      const api = fakeApi({
        memories: () => jsonResponse(page([memory({ photos: threePhotos(sig) })])),
      });
      renderApp(MEMORIES);
      const list = `GET /families/${ADJI_ID}/memories`;

      const card = await screen.findByRole('link', { name: /Le marché de Yaoundé/ });
      const expired = card.querySelector('img');
      if (expired === null) throw new Error('no thumbnail');
      expect(api.count(list)).toBe(1);
      sig = 2;
      fireEvent.error(expired);

      await waitFor(() => {
        expect(
          screen.getByRole('link', { name: /Le marché de Yaoundé/ }).querySelector('img'),
        ).toHaveAttribute('src', 'http://localhost:9000/thumb-1?sig=2');
      });
      expect(api.count(list)).toBe(2);
    });
  });
});
