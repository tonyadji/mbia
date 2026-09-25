import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import enAuth from './en/auth.json';
import enCommon from './en/common.json';
import enErrors from './en/errors.json';
import enFamily from './en/family.json';
import enSettings from './en/settings.json';
import frAuth from './fr/auth.json';
import frCommon from './fr/common.json';
import frErrors from './fr/errors.json';
import frFamily from './fr/family.json';
import frSettings from './fr/settings.json';
import { DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES, detectLanguage } from './language';

export const defaultNS = 'common';

export const resources = {
  fr: { common: frCommon, errors: frErrors, auth: frAuth, settings: frSettings, family: frFamily },
  en: { common: enCommon, errors: enErrors, auth: enAuth, settings: enSettings, family: enFamily },
} as const;

function syncDocumentLanguage(language: string) {
  document.documentElement.lang = language;
}

i18n.on('languageChanged', syncDocumentLanguage);

// After sign-in, the stored preference (User.preferredLocale) replaces the browser language (ProtectedRoute).
void i18n.use(initReactI18next).init({
  resources,
  lng: detectLanguage(null, navigator.languages),
  fallbackLng: DEFAULT_LANGUAGE,
  supportedLngs: SUPPORTED_LANGUAGES,
  ns: ['common', 'errors', 'auth', 'settings', 'family'],
  defaultNS,
  interpolation: { escapeValue: false },
});

export { i18n };
