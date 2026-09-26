import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
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

function problemResponse(code: string, status: number) {
  return jsonResponse(
    { code, status, title: code, detail: 'raw server detail' },
    status,
    'application/problem+json',
  );
}

const ADJI_ID = '6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b';
const MARIE_ID = '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a';
const ASSET_ID = '0b1c2d3e-4f50-4a6b-8c7d-8e9f0a1b2c3d';
const MEMORY_ID = '4e5f6a7b-8c9d-4e0f-9a1b-2c3d4e5f6a7b';
const PROFILE = `/families/${ADJI_ID}/persons/${MARIE_ID}`;
const EDIT = `${PROFILE}/edit`;
const UPLOAD_URL = 'http://localhost:9000/mbia-media/upload?X-Amz-Signature=abc';
const THUMBNAIL = 'http://localhost:9000/mbia-media/thumbnail?X-Amz-Signature=new';

function family(myRole = 'ADMIN') {
  return {
    id: ADJI_ID,
    name: 'ADJI',
    myRole,
    stats: { personCount: 1, memoryCount: 0, activeMemberCount: 1 },
    version: 0,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
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
    birth: { precision: 'UNKNOWN' },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    relationshipToCurrentUser: null,
    profilePictureUrl: null,
    version: 2,
    biography: null,
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    ...overrides,
  };
}

function treeNode(overrides: Record<string, unknown> = {}) {
  return { ...person(overrides), hasMoreParents: false, hasMoreChildren: false };
}

const emptyPage = { items: [], page: { page: 0, size: 20, totalElements: 0, totalPages: 0 } };

function slot() {
  return {
    mediaAssetId: ASSET_ID,
    uploadUrl: UPLOAD_URL,
    method: 'PUT',
    expiresAt: '2026-09-26T10:15:00Z',
    requiredHeaders: { 'Content-Type': 'image/jpeg' },
  };
}

function readyAsset() {
  return {
    id: ASSET_ID,
    purpose: 'PROFILE_PICTURE',
    status: 'READY',
    mimeType: 'image/jpeg',
    sizeBytes: 5,
    widthPx: 480,
    heightPx: 320,
    url: 'http://localhost:9000/mbia-media/display?X-Amz-Signature=new',
    thumbnailUrl: THUMBNAIL,
  };
}

type Handler = (request: Request) => Response | Promise<Response>;

/** A fake API answering by method and path (after `/api/v1`), recording every request. */
function fakeApi({
  role = 'ADMIN',
  get = () => jsonResponse(person()),
  complete = () => jsonResponse(readyAsset()),
  other = {},
}: {
  role?: string;
  get?: Handler;
  complete?: Handler;
  other?: Record<string, Handler>;
} = {}) {
  const requests: Request[] = [];
  const handlers: Record<string, Handler> = {
    [`GET /families/${ADJI_ID}`]: () => jsonResponse(family(role)),
    [`GET /families/${ADJI_ID}/persons/${MARIE_ID}`]: get,
    [`GET /families/${ADJI_ID}/tree`]: () =>
      jsonResponse({ focusPersonId: MARIE_ID, nodes: [treeNode()], edges: [] }),
    [`GET /families/${ADJI_ID}/persons/${MARIE_ID}/archived-relationships`]: () => jsonResponse([]),
    [`GET /families/${ADJI_ID}/persons/${MARIE_ID}/memories`]: () => jsonResponse(emptyPage),
    [`POST /families/${ADJI_ID}/media/uploads`]: () => jsonResponse(slot(), 201),
    [`POST /families/${ADJI_ID}/media/uploads/${ASSET_ID}/complete`]: complete,
    [`PATCH /families/${ADJI_ID}/persons/${MARIE_ID}`]: () => jsonResponse(person({ version: 3 })),
    ...other,
  };
  fetchMock.mockImplementation(async (input) => {
    const request = input as Request;
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, '');
    if (request.method === 'GET' && path === '/me') {
      return jsonResponse({ id: 'u1', email: 'alice@mbia.local', preferredLocale: 'fr' });
    }
    const handler = handlers[`${request.method} ${path}`];
    return handler ? handler(request) : problemResponse('RESOURCE_NOT_FOUND', 404);
  });
  const matching = (method: string, suffix: string) =>
    requests.filter(
      (request) => request.method === method && new URL(request.url).pathname.endsWith(suffix),
    );
  return {
    slots: () => matching('POST', '/media/uploads'),
    completions: () => matching('POST', '/complete'),
    patches: () => requests.filter((request) => request.method === 'PATCH'),
    creations: () => matching('POST', '/persons'),
    gets: (suffix: string) => matching('GET', suffix),
  };
}

/**
 * The direct upload to object storage. Each PUT reports 40 % then waits for {@link FakeXhr.finish}.
 */
class FakeXhr {
  static sent: { method: string; url: string; headers: Record<string, string>; body: unknown }[] =
    [];
  static waiting: (() => void)[] = [];
  static failNext = false;

  upload: { onprogress: ((event: Partial<ProgressEvent>) => void) | null } = { onprogress: null };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onabort: (() => void) | null = null;
  status = 0;
  private method = '';
  private url = '';
  private headers: Record<string, string> = {};

  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }

  send(body: unknown) {
    FakeXhr.sent.push({ method: this.method, url: this.url, headers: this.headers, body });
    const fail = FakeXhr.failNext;
    FakeXhr.failNext = false;
    queueMicrotask(() => {
      this.upload.onprogress?.({ lengthComputable: true, loaded: 40, total: 100 });
    });
    FakeXhr.waiting.push(() => {
      if (fail) {
        this.onerror?.();
      } else {
        this.status = 200;
        this.onload?.();
      }
    });
  }

  static async finish() {
    await waitFor(() => {
      expect(FakeXhr.waiting.length).toBeGreaterThan(0);
    });
    await act(async () => {
      FakeXhr.waiting.shift()?.();
      await Promise.resolve();
    });
  }
}

function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const view = render(
    <App
      router={router}
      queryClient={createQueryClient()}
      userManager={fakeUserManager(fakeOidcUser())}
    />,
  );
  return { router, ...view };
}

function pick(file: File) {
  fireEvent.change(screen.getByTestId('photo-input'), { target: { files: [file] } });
}

const jpeg = () => new File(['jpeg!'], 'mamie.jpeg', { type: 'image/jpeg' });

async function jsonBody(request: Request | undefined) {
  return (await request?.json()) as Record<string, unknown>;
}

describe('Person photo screens', () => {
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

  describe('Edit Person (SCREEN-012)', () => {
    it('adds a photo with upload progress, then saves it with the Person', async () => {
      const api = fakeApi();
      const { router } = renderApp(EDIT);

      fireEvent.click(await screen.findByRole('button', { name: 'Ajouter une photo' }));
      pick(jpeg());

      const progress = await screen.findByRole('progressbar', { name: 'Envoi de la photo' });
      await waitFor(() => {
        expect(progress).toHaveAttribute('aria-valuenow', '40');
      });
      expect(screen.getByText('Envoi de la photo… 40 %')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeDisabled();

      await FakeXhr.finish();
      expect(await screen.findByRole('button', { name: 'Changer la photo' })).toBeInTheDocument();
      expect(document.querySelector(`img[src="${THUMBNAIL}"]`)).not.toBeNull();

      const [request] = api.slots();
      expect(await jsonBody(request)).toEqual({
        purpose: 'PROFILE_PICTURE',
        fileName: 'mamie.jpeg',
        mimeType: 'image/jpeg',
        sizeBytes: 5,
      });
      expect(FakeXhr.sent).toHaveLength(1);
      expect(FakeXhr.sent[0]).toMatchObject({
        method: 'PUT',
        url: UPLOAD_URL,
        headers: { 'Content-Type': 'image/jpeg' },
      });
      expect(api.completions()).toHaveLength(1);

      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
      await waitFor(() => {
        expect(router.state.location.pathname).toBe(PROFILE);
      });
      const body = await jsonBody(api.patches()[0]);
      expect(body.profileMediaAssetId).toBe(ASSET_ID);
      expect(body).not.toHaveProperty('removeProfilePicture');
    });

    it('changes and removes an existing photo', async () => {
      const api = fakeApi({
        get: () => jsonResponse(person({ profilePictureUrl: 'http://localhost:9000/old' })),
      });
      renderApp(EDIT);

      expect(await screen.findByRole('button', { name: 'Changer la photo' })).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Retirer la photo' }));

      expect(screen.getByText("La photo sera retirée à l'enregistrement.")).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Ajouter une photo' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Retirer la photo' })).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
      await waitFor(() => {
        expect(api.patches()).toHaveLength(1);
      });
      const body = await jsonBody(api.patches()[0]);
      expect(body.removeProfilePicture).toBe(true);
      expect(body).not.toHaveProperty('profileMediaAssetId');
    });

    it('sends no photo field when the photo is unchanged', async () => {
      const api = fakeApi({
        get: () => jsonResponse(person({ profilePictureUrl: 'http://localhost:9000/old' })),
      });
      renderApp(EDIT);

      fireEvent.click(await screen.findByRole('button', { name: 'Enregistrer' }));
      await waitFor(() => {
        expect(api.patches()).toHaveLength(1);
      });
      const body = await jsonBody(api.patches()[0]);
      expect(body).not.toHaveProperty('profileMediaAssetId');
      expect(body).not.toHaveProperty('removeProfilePicture');
    });

    it('refuses an unsupported file before any upload, in human language', async () => {
      const api = fakeApi();
      renderApp(EDIT);

      await screen.findByRole('button', { name: 'Ajouter une photo' });
      pick(new File(['heic'], 'IMG_0001.HEIC', { type: 'image/heic' }));

      expect(await screen.findByRole('alert')).toHaveTextContent(
        "Ce fichier n'est pas une photo utilisable par Mbia. Choisissez une photo JPEG, PNG ou WEBP.",
      );
      expect(screen.getByRole('button', { name: 'Choisir une autre photo' })).toBeInTheDocument();
      expect(api.slots()).toHaveLength(0);
      expect(FakeXhr.sent).toHaveLength(0);
    });

    it('explains a failed upload and sends the same photo again on retry', async () => {
      const api = fakeApi();
      renderApp(EDIT);

      await screen.findByRole('button', { name: 'Ajouter une photo' });
      FakeXhr.failNext = true;
      pick(jpeg());
      await FakeXhr.finish();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        "La photo n'a pas pu être envoyée. Vérifiez votre connexion et réessayez.",
      );
      expect(api.completions()).toHaveLength(0);

      fireEvent.click(screen.getByRole('button', { name: 'Réessayer' }));
      await FakeXhr.finish();

      expect(await screen.findByRole('button', { name: 'Changer la photo' })).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      // A new upload slot for the second attempt.
      expect(api.slots()).toHaveLength(2);
      expect(FakeXhr.sent).toHaveLength(2);
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeEnabled();
    });

    it('explains a photo refused by the server and offers to choose another one', async () => {
      const api = fakeApi({ complete: () => problemResponse('MEDIA_INVALID', 400) });
      renderApp(EDIT);

      await screen.findByRole('button', { name: 'Ajouter une photo' });
      pick(jpeg());
      await FakeXhr.finish();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Ce fichier ne peut pas être utilisé comme photo. Choisissez-en un autre.',
      );
      expect(screen.getByRole('button', { name: 'Choisir une autre photo' })).toBeInTheDocument();
      expect(screen.queryByText('raw server detail')).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
      await waitFor(() => {
        expect(api.patches()).toHaveLength(1);
      });
      expect(await jsonBody(api.patches()[0])).not.toHaveProperty('profileMediaAssetId');
    });

    it('goes back to the saved photo when the latest version is reloaded', async () => {
      let version = 2;
      fakeApi({
        get: () => jsonResponse(person({ version })),
        other: {
          [`PATCH /families/${ADJI_ID}/persons/${MARIE_ID}`]: () => {
            version = 3;
            return problemResponse('CONCURRENT_MODIFICATION', 409);
          },
        },
      });
      renderApp(EDIT);

      await screen.findByRole('button', { name: 'Ajouter une photo' });
      pick(jpeg());
      await FakeXhr.finish();
      await screen.findByRole('button', { name: 'Changer la photo' });

      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
      fireEvent.click(await screen.findByRole('button', { name: 'Recharger la dernière version' }));

      expect(await screen.findByRole('button', { name: 'Ajouter une photo' })).toBeInTheDocument();
      expect(document.querySelector(`img[src="${THUMBNAIL}"]`)).toBeNull();
    });

    it('shows no photo action to a VIEWER', async () => {
      fakeApi({
        role: 'VIEWER',
        get: () => jsonResponse(person({ profilePictureUrl: 'http://localhost:9000/old' })),
      });
      const { router } = renderApp(EDIT);

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(PROFILE);
      });
      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      expect(screen.queryByRole('button', { name: /photo/i })).not.toBeInTheDocument();
      expect(screen.queryByTestId('photo-input')).not.toBeInTheDocument();
    });

    it("shows no photo action to a CONTRIBUTOR on another member's linked Person", async () => {
      fakeApi({
        role: 'CONTRIBUTOR',
        get: () =>
          jsonResponse(person({ linkedUserId: 'u2', relationshipToCurrentUser: 'NONE_KNOWN' })),
      });
      const { router } = renderApp(EDIT);

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(PROFILE);
      });
      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      expect(screen.queryByRole('button', { name: /photo/i })).not.toBeInTheDocument();
      expect(screen.queryByTestId('photo-input')).not.toBeInTheDocument();
    });
  });

  describe('Quick create (SCREEN-004)', () => {
    it('offers Photo among the first fields and creates the Person with it', async () => {
      const api = fakeApi({
        other: {
          [`POST /families/${ADJI_ID}/persons`]: () => jsonResponse(person(), 201),
        },
      });
      renderApp(`/families/${ADJI_ID}/persons/new`);

      const add = await screen.findByRole('button', { name: 'Ajouter une photo' });
      // Before `More information` (family-tree-ux.md §5).
      const more = screen.getByRole('button', { name: "Plus d'informations" });
      expect(add.compareDocumentPosition(more) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

      fireEvent.change(screen.getByRole('textbox', { name: /Prénom/ }), {
        target: { value: 'Marie' },
      });
      pick(jpeg());
      expect(await screen.findByRole('progressbar')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Ajouter à la famille' })).toBeDisabled();
      await FakeXhr.finish();
      await screen.findByRole('button', { name: 'Changer la photo' });

      fireEvent.click(screen.getByRole('button', { name: 'Ajouter à la famille' }));
      await waitFor(() => {
        expect(api.creations()).toHaveLength(1);
      });
      expect((await jsonBody(api.creations()[0])).profileMediaAssetId).toBe(ASSET_ID);
    });

    it('creates a Person without photo as before', async () => {
      const api = fakeApi({
        other: {
          [`POST /families/${ADJI_ID}/persons`]: () => jsonResponse(person(), 201),
        },
      });
      renderApp(`/families/${ADJI_ID}/persons/new`);

      fireEvent.change(await screen.findByRole('textbox', { name: /Prénom/ }), {
        target: { value: 'Marie' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Ajouter à la famille' }));
      await waitFor(() => {
        expect(api.creations()).toHaveLength(1);
      });
      expect(await jsonBody(api.creations()[0])).not.toHaveProperty('profileMediaAssetId');
    });
  });

  describe('Avatar', () => {
    it('shows the thumbnail on the tree card, and the initial without photo', async () => {
      // jsdom has no scrolling; the tree centres its focus on load.
      Element.prototype.scrollTo = () => undefined;
      fakeApi({
        other: {
          [`GET /families/${ADJI_ID}/tree`]: () =>
            jsonResponse({
              focusPersonId: MARIE_ID,
              nodes: [
                treeNode({ profilePictureUrl: 'http://localhost:9000/marie' }),
                treeNode({
                  id: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
                  firstName: 'Awa',
                  displayName: 'Awa',
                }),
              ],
              edges: [
                {
                  relationshipId: 'r1',
                  type: 'PARENT_OF',
                  sourcePersonId: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
                  targetPersonId: MARIE_ID,
                  version: 0,
                },
              ],
            }),
        },
      });
      renderApp(`/families/${ADJI_ID}/tree?focus=${MARIE_ID}`);

      const card = await screen.findByRole('button', { name: /Marie Adji/ });
      expect(card.querySelector('img')).toHaveAttribute('src', 'http://localhost:9000/marie');
      const parent = screen.getByRole('button', { name: /Awa/ });
      expect(parent.querySelector('img')).toBeNull();
      expect(parent).toHaveTextContent('A');
    });

    it('shows the related Persons of a Memory with their photo', async () => {
      fakeApi({
        other: {
          [`GET /families/${ADJI_ID}/memories/${MEMORY_ID}`]: () =>
            jsonResponse({
              id: MEMORY_ID,
              familyId: ADJI_ID,
              type: 'STORY',
              status: 'ACTIVE',
              title: 'Le marché',
              content: 'Une histoire.',
              relatedPersons: [
                {
                  id: MARIE_ID,
                  displayName: 'Marie Adji',
                  status: 'ACTIVE',
                  profilePictureUrl: 'http://localhost:9000/marie',
                },
              ],
              createdBy: { userId: 'u1', displayName: 'Tony', deleted: false },
              createdAt: '2026-09-20T10:00:00Z',
              updatedAt: '2026-09-20T10:00:00Z',
              version: 0,
            }),
        },
      });
      renderApp(`/families/${ADJI_ID}/memories/${MEMORY_ID}`);

      await screen.findByRole('link', { name: 'Marie Adji' });
      expect(document.querySelector('img[src="http://localhost:9000/marie"]')).not.toBeNull();
    });

    it('shows a photo again with a fresh URL after its URL expired', async () => {
      let signature = 1;
      const api = fakeApi({
        get: () =>
          jsonResponse(
            person({ profilePictureUrl: `http://localhost:9000/marie?sig=${String(signature)}` }),
          ),
      });
      renderApp(PROFILE);

      await screen.findByRole('heading', { level: 1, name: 'Marie Adji' });
      const expired = document.querySelector('img[src="http://localhost:9000/marie?sig=1"]');
      if (expired === null) throw new Error('the photo is not shown');
      const loads = api.gets(`/persons/${MARIE_ID}`).length;

      // The pre-signed URL expired: S3 answers 403 and the image fails to load.
      signature = 2;
      fireEvent.error(expired);

      await waitFor(() => {
        expect(
          document.querySelector('img[src="http://localhost:9000/marie?sig=2"]'),
        ).not.toBeNull();
      });
      expect(api.gets(`/persons/${MARIE_ID}`).length).toBe(loads + 1);

      // A fresh URL failing as well keeps the initial, without reloading again.
      signature = 3;
      const fresh = document.querySelector('img[src="http://localhost:9000/marie?sig=2"]');
      if (fresh === null) throw new Error('the fresh photo is not shown');
      fireEvent.error(fresh);
      await waitFor(() => {
        expect(document.querySelector('header img')).toBeNull();
      });
      expect(api.gets(`/persons/${MARIE_ID}`).length).toBe(loads + 1);
    });
  });
});
