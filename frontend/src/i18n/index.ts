import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import enAuth from './en/auth.json';
import enCommon from './en/common.json';
import enErrors from './en/errors.json';
import enFamily from './en/family.json';
import enMemory from './en/memory.json';
import enPerson from './en/person.json';
import enSettings from './en/settings.json';
import enTree from './en/tree.json';
import frAuth from './fr/auth.json';
import frCommon from './fr/common.json';
import frErrors from './fr/errors.json';
import frFamily from './fr/family.json';
import frMemory from './fr/memory.json';
import frPerson from './fr/person.json';
import frSettings from './fr/settings.json';
import frTree from './fr/tree.json';
import { DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES, detectLanguage } from './language';

export const defaultNS = 'common';

export const resources = {
  fr: {
    common: frCommon,
    errors: frErrors,
    auth: frAuth,
    settings: frSettings,
    family: frFamily,
    memory: frMemory,
    person: frPerson,
    tree: frTree,
  },
  en: {
    common: enCommon,
    errors: enErrors,
    auth: enAuth,
    settings: enSettings,
    family: enFamily,
    memory: enMemory,
    person: enPerson,
    tree: enTree,
  },
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
  ns: ['common', 'errors', 'auth', 'settings', 'family', 'memory', 'person', 'tree'],
  defaultNS,
  interpolation: { escapeValue: false },
});

export { i18n };
