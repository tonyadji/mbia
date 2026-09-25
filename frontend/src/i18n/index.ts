import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import enCommon from './en/common.json';
import enErrors from './en/errors.json';
import frCommon from './fr/common.json';
import frErrors from './fr/errors.json';
import { DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES, detectLanguage } from './language';

export const defaultNS = 'common';

export const resources = {
  fr: { common: frCommon, errors: frErrors },
  en: { common: enCommon, errors: enErrors },
} as const;

function syncDocumentLanguage(language: string) {
  document.documentElement.lang = language;
}

i18n.on('languageChanged', syncDocumentLanguage);

// The stored preference (User.preferredLocale) is passed here once account settings exist (PR-12).
void i18n.use(initReactI18next).init({
  resources,
  lng: detectLanguage(null, navigator.languages),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: SUPPORTED_LANGUAGES,
  ns: ['common', 'errors'],
  defaultNS,
  interpolation: { escapeValue: false },
});

export { i18n };
