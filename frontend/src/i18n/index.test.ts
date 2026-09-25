import { i18n } from './index';

describe('i18n', () => {
  afterEach(async () => {
    await i18n.changeLanguage('fr');
  });

  it('keeps the <html lang> attribute in sync with the active language', async () => {
    await i18n.changeLanguage('en');
    expect(document.documentElement.lang).toBe('en');

    await i18n.changeLanguage('fr');
    expect(document.documentElement.lang).toBe('fr');
  });

  it('loads the common, errors and auth namespaces in both languages', () => {
    for (const language of ['fr', 'en']) {
      expect(i18n.hasResourceBundle(language, 'common')).toBe(true);
      expect(i18n.hasResourceBundle(language, 'errors')).toBe(true);
      expect(i18n.hasResourceBundle(language, 'auth')).toBe(true);
    }
  });
});
