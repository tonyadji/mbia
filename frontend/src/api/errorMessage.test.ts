import { createInstance, type i18n as I18n } from 'i18next';
import { ApiError } from './client';
import { errorMessage } from './errorMessage';

describe('errorMessage', () => {
  let i18n: I18n;

  beforeAll(async () => {
    i18n = createInstance();
    await i18n.init({
      lng: 'en',
      resources: {
        en: { errors: { unexpected: 'Generic message.', TEST_CODE: 'Translated message.' } },
      },
    });
  });

  it('translates a known error code from the errors namespace', () => {
    expect(errorMessage(i18n, new ApiError(409, { code: 'TEST_CODE' }))).toBe(
      'Translated message.',
    );
  });

  it('falls back to the generic message for an untranslated code', () => {
    expect(errorMessage(i18n, new ApiError(500, { code: 'NOT_TRANSLATED' }))).toBe(
      'Generic message.',
    );
  });

  it('falls back to the generic message for an error without code or a non-API error', () => {
    expect(errorMessage(i18n, new ApiError(502, null))).toBe('Generic message.');
    expect(errorMessage(i18n, new TypeError('Failed to fetch'))).toBe('Generic message.');
  });
});
