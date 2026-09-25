import type { i18n as I18n } from 'i18next';
import type { resources } from '../i18n';
import { ApiError } from './client';

type ErrorsKey = keyof (typeof resources)['fr']['errors'];

/**
 * The translated message for an error: `errors:<code>` for an {@link ApiError} whose code has a
 * translation, the generic message otherwise. Raw server messages are never shown (AGENTS.md §8).
 */
export function errorMessage(i18n: I18n, error: unknown): string {
  const t = i18n.getFixedT(null, 'errors');
  if (
    error instanceof ApiError &&
    error.code !== null &&
    i18n.exists(error.code, { ns: 'errors' })
  ) {
    // Error codes get their translation with the feature that returns them; `exists` guards the lookup.
    return t(error.code as ErrorsKey);
  }
  return t('unexpected');
}
