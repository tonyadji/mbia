import createClient, { type Middleware } from 'openapi-fetch';
import type { components, paths } from './generated/schema';

type ProblemDetails = components['schemas']['ProblemDetails'];
type FieldError = components['schemas']['FieldError'];

const DEFAULT_BASE_URL = 'http://localhost:8080/api/v1';

/**
 * An API error response. `code` and `traceId` come from the `ProblemDetails` body; they are `null`
 * when the response is not a problem (for example a proxy error page).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly traceId: string | null;
  readonly fieldErrors: FieldError[];
  readonly details: Record<string, unknown> | null;

  constructor(status: number, problem: Partial<ProblemDetails> | null) {
    super(problem?.code ?? `HTTP ${String(status)}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code ?? null;
    this.traceId = problem?.traceId ?? null;
    this.fieldErrors = problem?.fieldErrors ?? [];
    this.details = problem?.details ?? null;
  }
}

/** Reads a failed response into an {@link ApiError}. */
export async function toApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('json')) {
    return new ApiError(response.status, null);
  }
  try {
    const body: unknown = await response.json();
    return new ApiError(response.status, isProblem(body) ? body : null);
  } catch {
    return new ApiError(response.status, null);
  }
}

function isProblem(body: unknown): body is Partial<ProblemDetails> {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as { code?: unknown }).code === 'string'
  );
}

type AccessTokenProvider = () => string | null | Promise<string | null>;

let accessTokenProvider: AccessTokenProvider = () => null;

/** Sets where the access token comes from (the authentication layer, PR-11). */
export function setAccessTokenProvider(provider: AccessTokenProvider) {
  accessTokenProvider = provider;
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler = () => undefined;

/** Sets what happens when the API answers 401 (the authentication layer restarts sign-in). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  unauthorizedHandler = handler;
}

const authentication: Middleware = {
  async onRequest({ request }) {
    const token = await accessTokenProvider();
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
    return request;
  },
};

const problems: Middleware = {
  async onResponse({ response }) {
    if (!response.ok) {
      if (response.status === 401) {
        unauthorizedHandler();
      }
      throw await toApiError(response);
    }
    return response;
  },
};

/** Creates a client typed from `openapi.yaml`; failed responses reject with an {@link ApiError}. */
export function createApiClient(
  options: { baseUrl?: string; fetch?: typeof globalThis.fetch } = {},
) {
  const client = createClient<paths>({
    baseUrl: options.baseUrl ?? import.meta.env.VITE_API_BASE_URL ?? DEFAULT_BASE_URL,
    ...(options.fetch ? { fetch: options.fetch } : {}),
  });
  client.use(authentication, problems);
  return client;
}

export const apiClient = createApiClient();
