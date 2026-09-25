import { useTranslation } from 'react-i18next';
import { Link, useLocation, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { AccountLink } from '../components/AccountLink';
import { buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { NavigationBar } from '../components/NavigationBar';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { addPersonPath } from './AddPersonPage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { personPath } from './PersonProfilePage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function familyHomePath(familyId: string) {
  return `/families/${familyId}`;
}

/** Navigation state set by Family or Person creation, for the success message. */
export interface FamilyHomeState {
  created?: boolean;
  /** The Person just added; `self` after "Start with me". */
  personAdded?: { id: string; name: string; self: boolean };
}

/**
 * SCREEN-002 — Family Home. `View family tree`, `Add a relative`, search and the tree card arrive
 * with their features; no Memory or activity in Phase 2.
 */
export function FamilyHomePage() {
  const { familyId = '' } = useParams();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });

  if (!isValidId || (family.error instanceof ApiError && family.error.status === 404)) {
    return <FamilyNotFoundPage />;
  }
  if (family.isError) {
    return <ErrorState error={family.error} onRetry={() => void family.refetch()} />;
  }

  return (
    <div className="flex flex-1 flex-col gap-8 pb-24">
      {family.isPending ? <HeaderSkeleton /> : <FamilyContent family={family.data} />}
      <NavigationBar familyId={familyId} />
    </div>
  );
}

type Family = NonNullable<ReturnType<typeof useFamily>['data']>;

function FamilyContent({ family }: { family: Family }) {
  const { t } = useTranslation('family');
  const state = useLocation().state as FamilyHomeState | null;
  const created = state?.created === true;
  const personAdded = state?.personAdded;
  const canAddPersons = family.myRole === 'ADMIN' || family.myRole === 'CONTRIBUTOR';
  const isEmpty = family.stats.personCount === 0;

  return (
    <>
      <header className="flex items-center gap-3">
        <FamilyIcon />
        <div className="flex min-w-0 flex-1 flex-col">
          <h1 className="text-section break-words text-text">{family.name}</h1>
          <p className="text-caption text-text-muted">
            {t('home.personCount', { count: family.stats.personCount })}
          </p>
        </div>
        <AccountLink />
      </header>
      {created && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {t('home.created', { name: family.name })}
        </p>
      )}
      {personAdded && (
        <div
          role="status"
          className="flex flex-col gap-1 rounded-xl border border-border bg-surface px-4 py-3 text-body"
        >
          <p>
            {personAdded.self
              ? t('home.selfAdded', { family: family.name })
              : t('home.personAdded', { name: personAdded.name })}
          </p>
          <Link
            to={personPath(family.id, personAdded.id)}
            className="self-start font-semibold text-primary underline-offset-4 hover:underline"
          >
            {t('home.viewProfile')}
          </Link>
        </div>
      )}
      {isEmpty ? (
        <section className="flex flex-1 flex-col items-center justify-center gap-2 text-center">
          <h2 className="text-display text-text">{t('home.emptyTitle', { name: family.name })}</h2>
          <p className="text-body text-text-muted">{t('home.emptyBody')}</p>
          {canAddPersons && (
            <div className="mt-6 flex w-full max-w-sm flex-col gap-3">
              <Link
                to={addPersonPath(family.id, { startWithMe: true })}
                className={buttonClassName('primary')}
              >
                {t('home.startWithMe')}
              </Link>
              <Link to={addPersonPath(family.id)} className={buttonClassName('secondary')}>
                {t('home.addSomeoneElse')}
              </Link>
            </div>
          )}
        </section>
      ) : (
        canAddPersons && (
          <Link
            to={addPersonPath(family.id)}
            className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
          >
            {t('home.addPerson')}
          </Link>
        )
      )}
    </>
  );
}

function HeaderSkeleton() {
  return (
    <div className="flex items-center gap-3">
      <Skeleton className="size-10 rounded-full" />
      <div className="flex flex-1 flex-col gap-2">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-4 w-32" />
      </div>
    </div>
  );
}

function FamilyIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-10 shrink-0 text-primary"
      fill="currentColor"
    >
      <circle cx="12" cy="6.5" r="3" />
      <circle cx="5" cy="9" r="2.3" />
      <circle cx="19" cy="9" r="2.3" />
      <path d="M12 10.5c-3 0-5 2-5 4.5V19h10v-4c0-2.5-2-4.5-5-4.5ZM5 12.5c-2 0-3.5 1.4-3.5 3.3V19H5.5v-4c0-1 .2-1.8.6-2.5H5ZM19 12.5h-1.1c.4.7.6 1.5.6 2.5v4h4v-3.2c0-1.9-1.5-3.3-3.5-3.3Z" />
    </svg>
  );
}
