export const SUPPORTED_LANGUAGES = ['fr', 'en'] as const;

export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'fr';

export function isSupportedLanguage(value: unknown): value is Language {
  return SUPPORTED_LANGUAGES.includes(value as Language);
}

/**
 * Initial language (localization-and-kinship-labels.md §1): the User's stored preference wins,
 * otherwise the browser language (`fr*` → `fr`, anything else → `en`).
 */
export function detectLanguage(
  preferredLocale: string | null | undefined,
  browserLanguages: readonly string[],
): Language {
  if (isSupportedLanguage(preferredLocale)) return preferredLocale;
  return browserLanguages[0]?.toLowerCase().startsWith('fr') ? 'fr' : 'en';
}
