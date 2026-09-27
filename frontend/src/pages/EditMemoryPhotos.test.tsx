import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { FakeXhr } from '../test/fakeXhr';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
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
const MARIE_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const MEMORY_ID = '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f';
const MEMORY = memoryPath(ADJI_ID, MEMORY_ID);
const EDIT = editMemoryPath(ADJI_ID, MEMORY_ID);
const ME = 'u1';
const assetId = (n: number) => `0b1c2d3e-4f50-4a6b-8c7d-${String(n).padStart(12, '0')}`;
const url = (kind: string, id: string) =>
  `http://localhost:9000/mbia-media/${kind}/${id}?X-Amz-Signature=a`;

function family(myRole: string, maxPhotosPerMemory: number) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    myLinkedPersonId: MARIE_ID,
    stats: { personCount: 1, memoryCount: 1, activeMemberCount: 1 },
    limits: { maxPhotosPerMemory },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

interface PhotoInput {
  mediaAssetId: string;
  caption: string | null;
  takenAt: { precision: string; date?: string | null; year?: number | null } | null;
}

function photo(n: number, caption: string | null = null, takenAt: PhotoInput['takenAt'] = null) {
  const id = assetId(n);
  return {
    mediaAssetId: id,
    caption,
    takenAt,
    url: url('display', id),
    thumbnailUrl: url('thumbnail', id),
    widthPx: 480,
    heightPx: 320,
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
    photos: [
      photo(1, 'Grand-mère au marché'),
      photo(2, null, { precision: 'YEAR_ONLY', year: 1975 }),
    ],
    relatedPersons: [{ id: MARIE_ID, displayName: 'Marie Adji', status: 'ACTIVE' }],
    createdBy: { userId: ME, displayName: 'Marie', deleted: false },
    createdAt: '2026-09-26T10:00:00Z',
    updatedAt: '2026-09-26T10:00:00Z',
    version: 0,
    ...overrides,
  };
}

function readyAsset(id: string) {
  return {
    id,
    purpose: 'MEMORY_PHOTO',
    status: 'READY',
    mimeType: 'image/jpeg',
    sizeBytes: 5,
    widthPx: 480,
    heightPx: 320,
    url: url('display', id),
    thumbnailUrl: url('thumbnail', id),
  };
}

interface PatchBody {
  title?: string;
  content?: string | null;
  photos?: PhotoInput[];
  relatedPersonIds?: string[];
}

/**
 * A fake API: the Memory `getMemory` returns in turn, the Family limit of each read in turn, and the
 * answers of `updateMemory` in turn; by default, the Memory with the photos sent.
 */
function fakeApi({
  role = 'CONTRIBUTOR',
  limits = [3],
  memories = [memory()],
  update = [] as (() => Response)[],
} = {}) {
  const patched: PatchBody[] = [];
  let slots = 100;
  let familyReads = 0;
  let memoryReads = 0;
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method} ${path}`;
    if (key === 'GET /me') {
      return jsonResponse({ id: ME, email: 'marie@mbia.local', preferredLocale: 'fr' });
    }
    if (key === `GET /families/${ADJI_ID}`) {
      const limit = limits[Math.min(familyReads, limits.length - 1)] ?? 3;
      familyReads++;
      return jsonResponse(family(role, limit));
    }
    if (key === `GET /families/${ADJI_ID}/memories/${MEMORY_ID}`) {
      const current = memories[Math.min(memoryReads, memories.length - 1)];
      memoryReads++;
      return jsonResponse(current);
    }
    if (key === `POST /families/${ADJI_ID}/media/uploads`) {
      slots++;
      return jsonResponse(
        {
          mediaAssetId: assetId(slots),
          uploadUrl: `http://localhost:9000/mbia-media/upload/${String(slots)}`,
          method: 'PUT',
          expiresAt: '2026-09-26T10:15:00Z',
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
        },
        201,
      );
    }
    const complete = /^POST \/families\/[^/]+\/media\/uploads\/([^/]+)\/complete$/.exec(key);
    if (complete) return jsonResponse(readyAsset(complete[1] ?? ''));
    if (key === `PATCH /families/${ADJI_ID}/memories/${MEMORY_ID}`) {
      const body = (await request.clone().json()) as PatchBody;
      const handler = update[patched.length];
      patched.push(body);
      if (handler) return handler();
      return jsonResponse(
        memory({
          title: body.title,
          content: body.content?.trim() ? body.content : null,
          photos: (body.photos ?? []).map((sent) => ({
            ...photo(0),
            ...sent,
            url: url('display', sent.mediaAssetId),
            thumbnailUrl: url('thumbnail', sent.mediaAssetId),
          })),
          version: 1,
        }),
      );
    }
    return problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  return { patched, familyReads: () => familyReads };
}

function renderApp() {
  const router = createMemoryRouter(routes, { initialEntries: [MEMORY, EDIT], initialIndex: 1 });
  render(
    <App
      router={router}
      queryClient={createQueryClient()}
      userManager={fakeUserManager(fakeOidcUser())}
    />,
  );
  return { router };
}

const jpeg = (name: string) => new File(['jpeg!'], name, { type: 'image/jpeg' });

function pick(files: File[]) {
  fireEvent.change(screen.getByTestId('memory-photo-input'), { target: { files } });
}

const save = () => screen.getByRole('button', { name: 'Enregistrer' });
const addPhoto = () => screen.getByRole('button', { name: 'Ajouter une photo' });
const removePhoto = (position: number) =>
  screen.getByRole('button', { name: `Retirer la photo ${String(position)}` });
const caption = (position: number) =>
  screen.getByRole('textbox', { name: `Légende de la photo ${String(position)}` });

/** Waits for the form, with its photos, to be shown. */
async function openForm() {
  return screen.findByRole('textbox', { name: 'Titre' });
}

describe('Photos in Edit Memory (SCREEN-014)', () => {
  beforeEach(async () => {
    fetchMock.mockReset();
    FakeXhr.sent = [];
    FakeXhr.waiting = [];
    FakeXhr.failNext = false;
    vi.stubGlobal('XMLHttpRequest', FakeXhr);
    // jsdom cannot decode images: the original file is sent as is (Phase 3 plan §3.6).
    vi.stubGlobal('createImageBitmap', vi.fn().mockRejectedValue(new Error('no decoder')));
    await act(() => i18n.changeLanguage('fr'));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the photos of the Memory with their caption and taken date, in order', async () => {
    fakeApi();
    renderApp();
    await openForm();

    expect(screen.getByRole('img', { name: 'Photo 1' })).toHaveAttribute(
      'src',
      url('thumbnail', assetId(1)),
    );
    expect(screen.getByRole('img', { name: 'Photo 2' })).toBeInTheDocument();
    expect(caption(1)).toHaveValue('Grand-mère au marché');
    expect(caption(2)).toHaveValue('');
    fireEvent.click(screen.getByRole('button', { name: "Plus d'informations sur la photo 2" }));
    const second = screen.getByRole('group', { name: 'Photo 2' });
    expect(within(second).getByRole('combobox')).toHaveValue('YEAR_ONLY');
    expect(within(second).getByRole('textbox', { name: 'Année de la photo' })).toHaveValue('1975');
    // Photos already on the Memory are not sent again.
    expect(FakeXhr.sent).toHaveLength(0);
  });

  it('sends the complete list after adding, removing and describing photos, then shows it', async () => {
    const api = fakeApi();
    const { router } = renderApp();
    await openForm();

    pick([jpeg('new.jpg')]);
    // `Save` waits for the new photo.
    await waitFor(() => {
      expect(save()).toBeDisabled();
    });
    await FakeXhr.finish();
    await waitFor(() => {
      expect(save()).toBeEnabled();
    });
    fireEvent.click(removePhoto(1));
    fireEvent.change(caption(1), { target: { value: ' La balance ' } });
    fireEvent.change(caption(2), { target: { value: 'Le nouveau marché' } });
    fireEvent.click(save());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(MEMORY);
    });
    expect(api.patched[0]?.photos).toEqual([
      {
        mediaAssetId: assetId(2),
        caption: 'La balance',
        takenAt: { precision: 'YEAR_ONLY', year: 1975 },
      },
      { mediaAssetId: assetId(101), caption: 'Le nouveau marché', takenAt: null },
    ]);
    // SCREEN-013 shows the result, in order.
    const shown = within(await screen.findByRole('list', { name: 'Photos' })).getAllByRole('img');
    expect(shown.map((image) => image.getAttribute('alt'))).toEqual([
      'La balance',
      'Le nouveau marché',
    ]);
  });

  it('adds photos only within the limit, and says so', async () => {
    fakeApi({ memories: [memory({ photos: [photo(1), photo(2), photo(3)] })] });
    renderApp();
    await openForm();

    expect(addPhoto()).toBeDisabled();
    expect(addPhoto()).toHaveAccessibleDescription("Un souvenir peut avoir jusqu'à 3 photos.");
    fireEvent.click(removePhoto(3));
    expect(addPhoto()).toBeEnabled();
  });

  it('keeps the photos above a lowered limit, and adds none until enough are removed', async () => {
    fakeApi({
      limits: [2],
      memories: [memory({ photos: [photo(1), photo(2), photo(3)] })],
    });
    renderApp();
    await openForm();

    expect(screen.getAllByRole('img')).toHaveLength(3);
    expect(addPhoto()).toBeDisabled();
    expect(addPhoto()).toHaveAccessibleDescription(/désormais \(jusqu'à 2\)\. Ses photos restent/);
    fireEvent.click(removePhoto(3));
    // At the limit: still no place.
    expect(addPhoto()).toBeDisabled();
    fireEvent.click(removePhoto(2));
    expect(addPhoto()).toBeEnabled();
  });

  it('saves a caption on a Memory above a lowered limit, with its photos unchanged', async () => {
    const api = fakeApi({
      limits: [2],
      memories: [memory({ photos: [photo(1), photo(2), photo(3)] })],
    });
    const { router } = renderApp();
    await openForm();

    fireEvent.change(caption(2), { target: { value: 'Au marché' } });
    fireEvent.click(save());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(MEMORY);
    });
    expect(api.patched[0]?.photos?.map((sent) => sent.mediaAssetId)).toEqual([
      assetId(1),
      assetId(2),
      assetId(3),
    ]);
  });

  it('empties the text while a photo remains', async () => {
    const api = fakeApi();
    const { router } = renderApp();
    await openForm();

    fireEvent.change(screen.getByRole('textbox', { name: /Votre histoire/ }), {
      target: { value: '   ' },
    });
    fireEvent.click(save());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(MEMORY);
    });
    expect(api.patched[0]?.content).toBe('   ');
    expect(api.patched[0]?.photos).toHaveLength(2);
  });

  it('refuses to remove the last photo of a Memory without text, and explains why', async () => {
    const api = fakeApi({ memories: [memory({ content: null, photos: [photo(1)] })] });
    renderApp();
    await openForm();

    fireEvent.click(removePhoto(1));
    fireEvent.click(save());

    const text = screen.getByRole('textbox', { name: /Votre histoire/ });
    await waitFor(() => {
      expect(text).toHaveFocus();
    });
    expect(text).toHaveAccessibleDescription(
      "Écrivez une histoire, ou gardez une photo : un souvenir a besoin de l'une ou de l'autre.",
    );
    expect(api.patched).toHaveLength(0);

    // With a text written, the Memory can go without photo.
    fireEvent.change(text, { target: { value: 'Grand-mère vendait du plantain.' } });
    fireEvent.click(save());
    await waitFor(() => {
      expect(api.patched).toHaveLength(1);
    });
    expect(api.patched[0]?.photos).toEqual([]);
  });

  it('refuses to empty the text of a Memory without photo, and explains why', async () => {
    const api = fakeApi({ memories: [memory({ photos: [] })] });
    renderApp();
    await openForm();

    const text = screen.getByRole('textbox', { name: /Votre histoire/ });
    fireEvent.change(text, { target: { value: '' } });
    fireEvent.click(save());

    await waitFor(() => {
      expect(text).toHaveAccessibleDescription(/gardez une photo/);
    });
    expect(api.patched).toHaveLength(0);
  });

  it('waits for a failed photo to be sent again or removed', async () => {
    fakeApi();
    renderApp();
    await openForm();

    FakeXhr.failNext = true;
    pick([jpeg('new.jpg')]);
    await FakeXhr.finish();
    expect(
      await screen.findByRole('button', { name: "Réessayer d'envoyer la photo 3" }),
    ).toBeInTheDocument();
    expect(save()).toBeDisabled();
    fireEvent.click(removePhoto(3));
    expect(save()).toBeEnabled();
  });

  it('explains a stale version and reloads the latest photos, dropping the photos added', async () => {
    const latest = memory({ photos: [photo(1, 'Changée ailleurs')], version: 1 });
    const api = fakeApi({
      memories: [memory(), latest],
      update: [() => problemResponse('CONCURRENT_MODIFICATION', 409)],
    });
    renderApp();
    await openForm();

    pick([jpeg('new.jpg')]);
    await FakeXhr.finish();
    await waitFor(() => {
      expect(save()).toBeEnabled();
    });
    fireEvent.change(caption(2), { target: { value: 'Ma légende' } });
    fireEvent.click(save());

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Ce souvenir a été modifié');
    expect(addPhoto()).toBeDisabled();
    fireEvent.click(within(alert).getByRole('button', { name: 'Recharger la dernière version' }));

    // The latest version, never merged with what was typed or added.
    await waitFor(() => {
      expect(caption(1)).toHaveValue('Changée ailleurs');
    });
    expect(screen.queryByRole('textbox', { name: 'Légende de la photo 2' })).toBeNull();
    fireEvent.click(save());
    await waitFor(() => {
      expect(api.patched).toHaveLength(2);
    });
    expect(api.patched[1]?.photos).toEqual([
      { mediaAssetId: assetId(1), caption: 'Changée ailleurs', takenAt: null },
    ]);
  });

  it('explains a photo limit lowered meanwhile, and reads the Family again', async () => {
    const api = fakeApi({
      limits: [3, 2],
      update: [() => problemResponse('MEMORY_PHOTO_LIMIT_REACHED', 409)],
    });
    renderApp();
    await openForm();

    pick([jpeg('new.jpg')]);
    await FakeXhr.finish();
    await waitFor(() => {
      expect(save()).toBeEnabled();
    });
    fireEvent.click(save());

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Un souvenir peut avoir jusqu'à (3|2) photos\. Retirez les photos en trop, puis enregistrez/,
    );
    await waitFor(() => {
      expect(api.familyReads()).toBeGreaterThan(1);
    });
    // Nothing typed is lost.
    expect(screen.getAllByRole('img', { name: /^Photo \d$/ })).toHaveLength(3);
  });

  it('sends a VIEWER back to the Memory, even on their own Memory', async () => {
    fakeApi({ role: 'VIEWER' });
    const { router } = renderApp();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(MEMORY);
    });
    expect(screen.queryByRole('button', { name: 'Enregistrer' })).toBeNull();
  });

  it('sends a CONTRIBUTOR back from the Memory of another member', async () => {
    fakeApi({
      memories: [
        memory({ createdBy: { userId: 'someone-else', displayName: 'X', deleted: false } }),
      ],
    });
    const { router } = renderApp();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(MEMORY);
    });
  });
});
