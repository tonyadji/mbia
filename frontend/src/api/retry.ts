import { ApiError } from './client';

/** Query retry rule: a client error (4xx) will not change by retrying; other failures get 3 tries. */
export function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  return !(error instanceof ApiError && error.status < 500) && failureCount < 3;
}
