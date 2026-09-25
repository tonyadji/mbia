import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { HOME_PATH } from '../auth/AuthProvider';
import { buttonClassName } from '../components/Button';

/** A Family that does not exist or that the User is not a member of (404 `FAMILY_NOT_FOUND`). */
export function FamilyNotFoundPage() {
  const { t } = useTranslation('family');

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <h1 className="text-display text-text">{t('notFound.title')}</h1>
      <p className="text-body text-text-muted">{t('notFound.body')}</p>
      <Link to={HOME_PATH} className={buttonClassName('secondary', 'sm:w-auto')}>
        {t('notFound.back')}
      </Link>
    </div>
  );
}
