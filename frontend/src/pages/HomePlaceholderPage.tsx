import { useTranslation } from 'react-i18next';

export function HomePlaceholderPage() {
  const { t } = useTranslation();

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2 text-center">
      <h1 className="text-display text-primary">Mbia</h1>
      <p className="text-body text-text">{t('home.tagline')}</p>
      <p className="text-caption text-text-muted">{t('home.comingSoon')}</p>
    </div>
  );
}
