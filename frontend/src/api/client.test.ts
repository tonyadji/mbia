import { ApiError, createApiClient, setAccessTokenProvider } from './client';

const BASE_URL = 'http://api.test/api/v1';

function fakeFetch(response: Response) {
  const requests: Request[] = [];
  const fetch = vi.fn((input: Request) => {
    requests.push(input);
    return Promise.resolve(response.clone());
  });
  return { fetch: fetch as unknown as typeof globalThis.fetch, requests };
}

function problemResponse(body: object, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

describe('apiClient', () => {
  afterEach(() => {
    setAccessTokenProvider(() => null);
  });

  it('calls the contract paths under the configured base URL', async () => {
    const { fetch, requests } = fakeFetch(
      new Response(JSON.stringify({ items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const { data } = await createApiClient({ baseUrl: BASE_URL, fetch }).GET('/families');

    expect(requests[0]?.url).toBe(`${BASE_URL}/families`);
    expect(data).toEqual({ items: [] });
  });

  it('sends the access token when the provider has one', async () => {
    setAccessTokenProvider(() => Promise.resolve('token-123'));
    const { fetch, requests } = fakeFetch(new Response('{}', { status: 200 }));

    await createApiClient({ baseUrl: BASE_URL, fetch }).GET('/me');

    expect(requests[0]?.headers.get('Authorization')).toBe('Bearer token-123');
  });

  it('sends no Authorization header without a token', async () => {
    const { fetch, requests } = fakeFetch(new Response('{}', { status: 200 }));

    await createApiClient({ baseUrl: BASE_URL, fetch }).GET('/me');

    expect(requests[0]?.headers.has('Authorization')).toBe(false);
  });

  it('rejects a problem response with a typed ApiError', async () => {
    const { fetch } = fakeFetch(
      problemResponse(
        {
          type: 'https://mbia.example.com/problems/validation-failed',
          title: 'Validation failed',
          status: 400,
          code: 'VALIDATION_FAILED',
          detail: 'The request is invalid.',
          traceId: 'trace-1',
          fieldErrors: [{ field: 'name', code: 'NOT_BLANK', message: 'must not be blank' }],
          details: { max: 3 },
        },
        400,
      ),
    );

    const error: unknown = await createApiClient({ baseUrl: BASE_URL, fetch })
      .GET('/me')
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      traceId: 'trace-1',
      fieldErrors: [{ field: 'name', code: 'NOT_BLANK', message: 'must not be blank' }],
      details: { max: 3 },
    });
  });

  it('rejects a non-problem error response with an ApiError without code', async () => {
    const { fetch } = fakeFetch(
      new Response('<html>Bad gateway</html>', {
        status: 502,
        headers: { 'Content-Type': 'text/html' },
      }),
    );

    const error: unknown = await createApiClient({ baseUrl: BASE_URL, fetch })
      .GET('/me')
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 502,
      code: null,
      traceId: null,
      fieldErrors: [],
      details: null,
    });
  });
});
