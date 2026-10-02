import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { App } from '../app/App';
import { createQueryClient } from '../app/queryClient';
import { routes } from '../app/routes';
import { i18n } from '../i18n';
import { FakeXhr } from '../test/fakeXhr';
import { fakeOidcUser, fakeUserManager } from '../test/fakeUserManager';
import { addMemoryPath } from './AddMemoryPage';

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
const assetId = (n: number) => `0b1c2d3e-4f50-4a6b-8c7d-${String(n).padStart(12, '0')}`;

function family(maxPhotosPerMemory: number) {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole: 'CONTRIBUTOR',
    myLinkedPersonId: MARIE_ID,
    stats: { personCount: 1, memoryCount: 0, activeMemberCount: 1 },
    limits: { maxPhotosPerMemory },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
  };
}

const marie = {
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
};

function readyAsset(id: string) {
  return {
    id,
    purpose: 'MEMORY_PHOTO',
    status: 'READY',
    mimeType: 'image/jpeg',
    sizeBytes: 5,
    widthPx: 480,
    heightPx: 320,
    url: `http://localhost:9000/mbia-media/display/${id}?X-Amz-Signature=a`,
    thumbnailUrl: `http://localhost:9000/mbia-media/thumbnail/${id}?X-Amz-Signature=a`,
  };
}

interface CreateBody {
  title: string;
  content: string | null;
  relatedPersonIds: string[];
  photos?: { mediaAssetId: string; caption: string | null; takenAt: unknown }[];
}

/** A fake API: each upload slot gets the next asset id; `create` answers the publications in turn. */
function fakeApi({ limit = 3, limits = [] as number[], create = [] as (() => Response)[] } = {}) {
  const slots: Record<string, unknown>[] = [];
  const posted: CreateBody[] = [];
  let familyReads = 0;
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method} ${path}`;
    if (key === 'GET /me') {
      return jsonResponse({ id: 'u1', email: 'marie@mbia.local', preferredLocale: 'fr' });
    }
    if (key === `GET /families/${ADJI_ID}`) {
      // `limits` gives the limit of each later read, for example after a refusal.
      const value = familyReads === 0 ? limit : (limits[familyReads - 1] ?? limit);
      familyReads++;
      return jsonResponse(family(value));
    }
    if (key === `GET /families/${ADJI_ID}/persons/${MARIE_ID}`) return jsonResponse(marie);
    if (key === `POST /families/${ADJI_ID}/media/uploads`) {
      slots.push((await request.clone().json()) as Record<string, unknown>);
      return jsonResponse(
        {
          mediaAssetId: assetId(slots.length),
          uploadUrl: `http://localhost:9000/mbia-media/upload/${String(slots.length)}`,
          method: 'PUT',
          expiresAt: '2026-09-26T10:15:00Z',
          requiredHeaders: { 'Content-Type': 'image/jpeg' },
        },
        201,
      );
    }
    const complete = /^POST \/families\/[^/]+\/media\/uploads\/([^/]+)\/complete$/.exec(key);
    if (complete) return jsonResponse(readyAsset(complete[1] ?? ''));
    if (key === `POST /families/${ADJI_ID}/memories/stories`) {
      const body = (await request.clone().json()) as CreateBody;
      const handler = create[posted.length];
      posted.push(body);
      return handler
        ? handler()
        : jsonResponse(
            {
              id: MEMORY_ID,
              familyId: ADJI_ID,
              type: 'STORY',
              status: 'ACTIVE',
              title: body.title,
              content: body.content,
              happenedAt: { precision: 'UNKNOWN' },
              photos: [],
              relatedPersons: [{ id: MARIE_ID, displayName: 'Marie Adji', status: 'ACTIVE' }],
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
  return { slots, posted, familyReads: () => familyReads };
}

function renderApp() {
  const router = createMemoryRouter(routes, { initialEntries: [addMemoryPath(ADJI_ID)] });
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
const jpegs = (count: number) =>
  Array.from({ length: count }, (_, index) => jpeg(`photo-${String(index + 1)}.jpg`));

function pick(files: File[]) {
  fireEvent.change(screen.getByTestId('memory-photo-input'), { target: { files } });
}

async function finishUploads(count: number) {
  for (let index = 0; index < count; index++) await FakeXhr.finish();
}

const publish = () => screen.getByRole('button', { name: 'Publier' });
const addPhoto = () => screen.getByRole('button', { name: 'Ajouter une photo' });

async function fillTitle(title: string) {
  fireEvent.change(await screen.findByRole('textbox', { name: 'Titre' }), {
    target: { value: title },
  });
}

describe('Photos in Add Memory (SCREEN-006)', () => {
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

  it('keeps the first 3 of 5 photos chosen, says so, then disables Add a photo with its reason', async () => {
    const api = fakeApi({ limit: 3 });
    renderApp();
    await fillTitle('Le marché');

    pick(jpegs(5));

    expect(await screen.findByRole('status')).toHaveTextContent(
      "Un souvenir peut avoir jusqu'à 3 photos. 2 photos n'ont pas été ajoutées.",
    );
    expect(screen.getAllByRole('group', { name: /^Photo \d$/ })).toHaveLength(3);
    await waitFor(() => {
      expect(api.slots).toHaveLength(3);
    });
    // Sent as Memory photos, in the order chosen.
    expect(api.slots.map((slot) => [slot.purpose, slot.fileName])).toEqual([
      ['MEMORY_PHOTO', 'photo-1.jpg'],
      ['MEMORY_PHOTO', 'photo-2.jpg'],
      ['MEMORY_PHOTO', 'photo-3.jpg'],
    ]);
    expect(addPhoto()).toBeDisabled();
    expect(addPhoto()).toHaveAccessibleDescription(/Un souvenir peut avoir jusqu'à 3 photos\./);

    // Removing one frees a place again.
    fireEvent.click(screen.getByRole('button', { name: 'Retirer la photo 2' }));
    expect(addPhoto()).toBeEnabled();
    expect(screen.getAllByRole('group', { name: /^Photo \d$/ })).toHaveLength(2);
  });

  it('reads the limit from the Family: with 2, one photo then two more keeps only one of them', async () => {
    const api = fakeApi({ limit: 2 });
    renderApp();
    await fillTitle('Le marché');

    pick([jpeg('a.jpg')]);
    expect(addPhoto()).toBeEnabled();
    pick([jpeg('b.jpg'), jpeg('c.jpg')]);

    expect(await screen.findByRole('status')).toHaveTextContent(
      "Un souvenir peut avoir jusqu'à 2 photos. 1 photo n'a pas été ajoutée.",
    );
    await waitFor(() => {
      expect(api.slots.map((slot) => slot.fileName)).toEqual(['a.jpg', 'b.jpg']);
    });
    expect(addPhoto()).toBeDisabled();
  });

  it('says a Memory can have only one photo when the limit is 1', async () => {
    fakeApi({ limit: 1 });
    renderApp();
    await fillTitle('Le marché');
    pick([jpeg('a.jpg')]);
    expect(addPhoto()).toBeDisabled();
    expect(addPhoto()).toHaveAccessibleDescription("Un souvenir ne peut avoir qu'une seule photo.");
  });

  it('shows the progress, waits for every photo, then publishes a title and a photo without text', async () => {
    const api = fakeApi();
    const { router } = renderApp();
    await fillTitle('Mamie au marché');
    expect(screen.getByRole('textbox', { name: 'Votre histoire' })).toBeRequired();

    pick(jpegs(2));

    const progress = await screen.findByRole('progressbar', { name: 'Envoi de la photo 1' });
    await waitFor(() => {
      expect(progress).toHaveAttribute('aria-valuenow', '40');
    });
    expect(publish()).toBeDisabled();

    await FakeXhr.finish();
    expect(await screen.findByRole('img', { name: 'Photo 1' })).toHaveAttribute(
      'src',
      readyAsset(assetId(1)).thumbnailUrl,
    );
    // The second photo is still being sent.
    expect(publish()).toBeDisabled();
    await FakeXhr.finish();
    await screen.findByRole('img', { name: 'Photo 2' });
    await waitFor(() => {
      expect(publish()).toBeEnabled();
    });

    // The text is optional once a photo is ready.
    expect(screen.getByRole('textbox', { name: 'Votre histoire' })).not.toBeRequired();
    expect(screen.getByText('Facultatif quand le souvenir a une photo.')).toBeInTheDocument();

    fireEvent.change(screen.getByRole('textbox', { name: 'Légende de la photo 1' }), {
      target: { value: '  Au marché de Yaoundé  ' },
    });
    const more = screen.getByRole('button', { name: "Plus d'informations sur la photo 2" });
    expect(more).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(more);
    expect(more).toHaveAttribute('aria-expanded', 'true');
    const second = screen.getByRole('group', { name: 'Photo 2' });
    fireEvent.change(
      within(second).getByRole('combobox', { name: 'Quand cette photo a-t-elle été prise ?' }),
      { target: { value: 'YEAR_ONLY' } },
    );
    fireEvent.change(within(second).getByRole('textbox', { name: 'Année de la photo' }), {
      target: { value: '1975' },
    });

    fireEvent.click(publish());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/memories/${MEMORY_ID}`);
    });
    expect(api.posted).toEqual([
      {
        title: 'Mamie au marché',
        content: null,
        relatedPersonIds: [MARIE_ID],
        photos: [
          { mediaAssetId: assetId(1), caption: 'Au marché de Yaoundé', takenAt: null },
          {
            mediaAssetId: assetId(2),
            caption: null,
            takenAt: { precision: 'YEAR_ONLY', year: 1975 },
          },
        ],
      },
    ]);
  });

  it('still requires the text when the Memory has no photo', async () => {
    const api = fakeApi();
    renderApp();
    await fillTitle('Le marché');
    fireEvent.click(publish());
    expect(await screen.findByText('Ce champ est obligatoire.')).toBeInTheDocument();
    expect(api.posted).toHaveLength(0);
  });

  it('refuses an unsupported file before any upload; it can only be removed', async () => {
    const api = fakeApi();
    renderApp();
    await fillTitle('Le marché');

    pick([new File(['notes'], 'notes.txt', { type: 'text/plain' })]);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Ce fichier n'est pas une photo utilisable par Mbia.",
    );
    expect(api.slots).toHaveLength(0);
    expect(FakeXhr.sent).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /Réessayer/ })).not.toBeInTheDocument();
    expect(publish()).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Retirer la photo 1' }));
    expect(screen.queryByRole('group', { name: 'Photo 1' })).not.toBeInTheDocument();
    expect(publish()).toBeEnabled();
  });

  it('offers Try again after a failed upload, and Publish waits for it', async () => {
    const api = fakeApi();
    renderApp();
    await fillTitle('Le marché');

    FakeXhr.failNext = true;
    pick([jpeg('a.jpg')]);
    await FakeXhr.finish();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "La photo n'a pas pu être envoyée. Vérifiez votre connexion et réessayez.",
    );
    expect(publish()).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: "Réessayer d'envoyer la photo 1" }));
    await FakeXhr.finish();
    await screen.findByRole('img', { name: 'Photo 1' });
    // A new upload slot for the same file.
    expect(api.slots.map((slot) => slot.fileName)).toEqual(['a.jpg', 'a.jpg']);
    await waitFor(() => {
      expect(publish()).toBeEnabled();
    });
  });

  it('removes a failed photo, which frees Publish', async () => {
    fakeApi();
    renderApp();
    await fillTitle('Le marché');
    fireEvent.change(screen.getByRole('textbox', { name: 'Votre histoire' }), {
      target: { value: 'Chaque samedi.' },
    });
    FakeXhr.failNext = true;
    pick([jpeg('a.jpg')]);
    await FakeXhr.finish();
    await screen.findByRole('alert');
    expect(publish()).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Retirer la photo 1' }));
    expect(publish()).toBeEnabled();
  });

  it('refuses an invalid taken year before sending anything', async () => {
    const api = fakeApi();
    renderApp();
    await fillTitle('Le marché');
    pick([jpeg('a.jpg')]);
    await finishUploads(1);
    await screen.findByRole('img', { name: 'Photo 1' });

    fireEvent.click(screen.getByRole('button', { name: "Plus d'informations sur la photo 1" }));
    fireEvent.change(
      screen.getByRole('combobox', { name: 'Quand cette photo a-t-elle été prise ?' }),
      { target: { value: 'YEAR_ONLY' } },
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Année de la photo' }), {
      target: { value: '19755' },
    });
    await waitFor(() => {
      expect(publish()).toBeEnabled();
    });
    fireEvent.click(publish());

    expect(await screen.findByText('Indiquez une année entre 1 et 9999.')).toBeInTheDocument();
    expect(api.posted).toHaveLength(0);
  });

  it('keeps the taken date of a photo as before: only the date of the Memory refuses the future', async () => {
    const api = fakeApi();
    renderApp();
    await fillTitle('Le marché');
    pick([jpeg('a.jpg')]);
    await finishUploads(1);
    await screen.findByRole('img', { name: 'Photo 1' });

    fireEvent.click(screen.getByRole('button', { name: "Plus d'informations sur la photo 1" }));
    const takenAt = screen.getByRole('combobox', {
      name: 'Quand cette photo a-t-elle été prise ?',
    });
    fireEvent.change(takenAt, { target: { value: 'EXACT' } });
    expect(screen.getByLabelText('Date de la photo')).not.toHaveAttribute('max');
    fireEvent.change(takenAt, { target: { value: 'YEAR_ONLY' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Année de la photo' }), {
      target: { value: '2099' },
    });
    await waitFor(() => {
      expect(publish()).toBeEnabled();
    });
    fireEvent.click(publish());

    await waitFor(() => {
      expect(api.posted).toHaveLength(1);
    });
    expect(api.posted[0]).toMatchObject({
      photos: [
        {
          mediaAssetId: assetId(1),
          caption: null,
          takenAt: { precision: 'YEAR_ONLY', year: 2099 },
        },
      ],
    });
    expect(api.posted[0]).not.toHaveProperty('happenedAt');
  });

  it.each([
    [
      'MEMORY_PHOTO_LIMIT_REACHED',
      409,
      "Un souvenir peut avoir jusqu'à 2 photos. Retirez les photos en trop, puis publiez à nouveau.",
    ],
    [
      'MEDIA_ALREADY_USED',
      409,
      'Une des photos est déjà utilisée ailleurs. Retirez-la et ajoutez-la de nouveau, puis publiez.',
    ],
    [
      'MEDIA_NOT_READY',
      409,
      "Une des photos n'est pas encore prête. Retirez-la et ajoutez-la de nouveau, puis publiez.",
    ],
    [
      'MEDIA_NOT_FOUND',
      404,
      'Une des photos est introuvable. Retirez-la et ajoutez-la de nouveau, puis publiez.',
    ],
  ])('explains %s and keeps everything typed', async (code, status, message) => {
    // The limit was lowered to 2 in the meantime: the Family read after the refusal says so.
    const api = fakeApi({
      limit: 3,
      limits: [2],
      create: [() => problemResponse(code, status)],
    });
    const { router } = renderApp();
    await fillTitle('Le marché');
    fireEvent.change(screen.getByRole('textbox', { name: 'Votre histoire' }), {
      target: { value: 'Chaque samedi.' },
    });
    pick(jpegs(3));
    await finishUploads(3);
    await screen.findByRole('img', { name: 'Photo 3' });
    fireEvent.change(screen.getByRole('textbox', { name: 'Légende de la photo 1' }), {
      target: { value: 'Mamie' },
    });
    await waitFor(() => {
      expect(publish()).toBeEnabled();
    });

    fireEvent.click(publish());

    const alert = await screen.findByText(
      code === 'MEMORY_PHOTO_LIMIT_REACHED' ? /Retirez les photos en trop/ : message,
    );
    expect(alert).toHaveAttribute('role', 'alert');
    expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/families/${ADJI_ID}/memories/new`);
    expect(screen.getByRole('textbox', { name: 'Titre' })).toHaveValue('Le marché');
    expect(screen.getByRole('textbox', { name: 'Votre histoire' })).toHaveValue('Chaque samedi.');
    expect(screen.getByRole('textbox', { name: 'Légende de la photo 1' })).toHaveValue('Mamie');
    expect(screen.getAllByRole('group', { name: /^Photo \d$/ })).toHaveLength(3);
    if (code === 'MEMORY_PHOTO_LIMIT_REACHED') {
      // The Family is read again: the new limit is explained and applied.
      await waitFor(() => {
        expect(alert).toHaveTextContent(message);
      });
      expect(api.familyReads()).toBeGreaterThan(1);
      expect(addPhoto()).toBeDisabled();
    }
  });

  it('names every photo control with its position, in English too', async () => {
    fakeApi();
    renderApp();
    // The User's saved language applies first; then the User switches to English.
    await screen.findByRole('textbox', { name: 'Titre' });
    await act(() => i18n.changeLanguage('en'));
    fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), {
      target: { value: 'The market' },
    });
    pick(jpegs(4));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'A memory can have up to 3 photos. 1 photo was not added.',
    );
    expect(screen.getByRole('textbox', { name: 'Caption of photo 3' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Remove photo 2' })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'More information about photo 1' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add a photo' })).toBeDisabled();
    expect(screen.getAllByRole('progressbar', { name: /^Sending photo \d$/ })).toHaveLength(3);
  });

  it('moves the focus to the first photo added, then to the photo that takes a removed one’s place', async () => {
    fakeApi({ limit: 3 });
    renderApp();
    await fillTitle('Le marché');
    const group = (position: number) =>
      screen.getByRole('group', { name: `Photo ${String(position)}` });

    // Choosing the photos that reach the limit disables `Add a photo`: the focus is not lost.
    addPhoto().focus();
    pick(jpegs(1));
    await waitFor(() => {
      expect(group(1)).toHaveFocus();
    });
    addPhoto().focus();
    pick(jpegs(2));
    await waitFor(() => {
      expect(group(2)).toHaveFocus();
    });
    expect(addPhoto()).toBeDisabled();

    // The first removed: the next one takes its place, and the focus.
    fireEvent.click(screen.getByRole('button', { name: 'Retirer la photo 1' }));
    await waitFor(() => {
      expect(screen.getAllByRole('group', { name: /^Photo \d$/ })).toHaveLength(2);
    });
    expect(group(1)).toHaveFocus();
    // The last removed: the previous one gets the focus.
    fireEvent.click(screen.getByRole('button', { name: 'Retirer la photo 2' }));
    await waitFor(() => {
      expect(group(1)).toHaveFocus();
    });
    // No photo left: `Add a photo` gets it.
    fireEvent.click(screen.getByRole('button', { name: 'Retirer la photo 1' }));
    await waitFor(() => {
      expect(addPhoto()).toHaveFocus();
    });
  });

  it('keeps a long caption without break inside the photo at phone width', async () => {
    fakeApi();
    renderApp();
    await fillTitle('Le marché');
    pick(jpegs(1));
    const field = await screen.findByRole('textbox', { name: 'Légende de la photo 1' });
    fireEvent.change(field, { target: { value: 'Nkolbisson'.repeat(20) } });

    // A single-line field scrolls its text; its photo may shrink below its content's width.
    expect(field.tagName).toBe('INPUT');
    expect(screen.getByRole('group', { name: 'Photo 1' })).toHaveClass('min-w-0');
  });
});
