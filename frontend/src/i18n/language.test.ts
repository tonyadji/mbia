import { detectLanguage } from './language';

describe('detectLanguage', () => {
  it.each([['fr'], ['fr-FR'], ['fr-CM'], ['FR-ca']])(
    'uses French for the browser language %s',
    (browser) => {
      expect(detectLanguage(null, [browser, 'en-US'])).toBe('fr');
    },
  );

  it.each([['en-US'], ['en'], ['de-DE'], ['es']])(
    'uses English for the browser language %s',
    (browser) => {
      expect(detectLanguage(null, [browser, 'fr-FR'])).toBe('en');
    },
  );

  it('uses English when the browser gives no language', () => {
    expect(detectLanguage(null, [])).toBe('en');
  });

  it('lets the stored preference win over the browser language', () => {
    expect(detectLanguage('en', ['fr-FR'])).toBe('en');
    expect(detectLanguage('fr', ['en-US'])).toBe('fr');
  });

  it('ignores an unsupported stored preference', () => {
    expect(detectLanguage('de', ['fr-FR'])).toBe('fr');
  });
});
