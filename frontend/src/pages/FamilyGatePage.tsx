import { useTranslation } from 'react-i18next';
import { Link, Navigate } from 'react-router';
import { CREATE_FAMILY_PATH } from '../auth/AuthProvider';
import { AccountLink } from '../components/AccountLink';
import { ErrorState } from '../components/ErrorState';
import { Logo } from '../components/Logo';
import { Skeleton } from '../components/Skeleton';
import { useMyFamilies } from '../families/useMyFamilies';
import { familyHomePath } from './FamilyHomePage';

/**
 * Where a signed-in User lands (`HOME_PATH`): no Family → Family creation; one → its home; several →
 * a simple chooser.
 */
export function FamilyGatePage() {
  const { t } = useTranslation('family');
  const families = useMyFamilies();

  if (families.isError) {
    return <ErrorState error={families.error} onRetry={() => void families.refetch()} />;
  }
  if (families.isPending) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
      </div>
    );
  }

  const [first, ...others] = families.data;
  if (first === undefined) return <Navigate to={CREATE_FAMILY_PATH} replace />;
  if (others.length === 0) return <Navigate to={familyHomePath(first.id)} replace />;

  return (
    <div className="flex flex-1 flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <Logo />
        <AccountLink />
      </header>
      <h1 className="text-display text-text">{t('chooser.title')}</h1>
      <ul className="flex flex-col gap-3">
        {families.data.map((family) => (
          <li key={family.id}>
            <Link
              to={familyHomePath(family.id)}
              className="flex min-h-16 flex-col justify-center rounded-xl border border-border bg-surface px-4 py-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <span className="text-body font-semibold break-words text-text">{family.name}</span>
              <span className="text-caption text-text-muted">
                {t('home.personCount', { count: family.stats.personCount })}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
