import { useTranslation } from 'react-i18next';
import { Link, useLocation, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { AccountLink } from '../components/AccountLink';
import { buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { NavigationBar } from '../components/NavigationBar';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { AddRelativeMenu } from '../persons/AddRelativeMenu';
import { addRelativePath } from '../persons/relatives';
import { familyTreePath } from '../tree/treePath';
import { addMemoryPath } from './AddMemoryPage';
import { addPersonPath } from './AddPersonPage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { personPath } from './PersonProfilePage';
import { searchPath } from './SearchPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function familyHomePath(familyId: string) {
  return `/families/${familyId}`;
}

/** Navigation state set by Family or Person creation, for the success message. */
export interface FamilyHomeState {
  created?: boolean;
  /**
   * The Person just added; `self` after "Start with me", `linkedTo` after "Add a relative",
   * `existing` when a Person already in the Family was linked rather than added.
   */
  personAdded?: { id: string; name: string; self: boolean; linkedTo?: string; existing?: boolean };
}

/**
 * SCREEN-002 — Family Home: the search entry (SCREEN-007), `View family tree` as primary action,
 * then `Add a relative` and `Add a memory`. No activity in Phase 3 (phase-3-family-memories.md §3.2).
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

export type Family = NonNullable<ReturnType<typeof useFamily>['data']>;

function FamilyContent({ family }: { family: Family }) {
  const { t } = useTranslation(['family', 'person']);
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
            {personAdded.self && t('home.selfAdded', { family: family.name })}
            {!personAdded.self &&
              (personAdded.linkedTo === undefined
                ? t('home.personAdded', { name: personAdded.name })
                : t(personAdded.existing ? 'person:relative.linked' : 'person:relative.added', {
                    name: personAdded.name,
                    anchor: personAdded.linkedTo,
                  }))}
          </p>
          <Link
            to={personPath(family.id, personAdded.id)}
            className="inline-flex min-h-12 items-center self-start font-semibold text-primary underline-offset-4 hover:underline"
          >
            {t('home.viewProfile')}
          </Link>
        </div>
      )}
      {isEmpty ? (
        <FamilyEmptyState family={family} />
      ) : (
        <>
          <Link
            to={searchPath(family.id)}
            className="flex min-h-12 items-center gap-3 rounded-xl border border-border bg-surface px-4 text-body text-text-muted transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <SearchIcon />
            {t('home.search')}
          </Link>
          <Link
            to={familyTreePath(family.id)}
            className={buttonClassName('primary', 'sm:w-auto sm:self-start')}
          >
            {t('home.viewTree')}
          </Link>
          {canAddPersons && <AddAction family={family} />}
          {canAddPersons && (
            <Link
              to={addMemoryPath(family.id)}
              className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
            >
              {t('home.addMemory')}
            </Link>
          )}
        </>
      )}
    </>
  );
}

/**
 * The Family has no Person yet (SCREEN-002): ADMIN and CONTRIBUTOR may start with themselves or
 * someone else, a VIEWER only reads the explanation. Also the empty tree (SCREEN-003).
 */
export function FamilyEmptyState({ family }: { family: Family }) {
  const { t } = useTranslation('family');
  const canAddPersons = family.myRole === 'ADMIN' || family.myRole === 'CONTRIBUTOR';
  return (
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
  );
}

/**
 * `Add a relative` from the caller's own Person (family-tree-ux.md §9.1), or `Add a person` when
 * the caller is not in the tree (SCREEN-002).
 */
function AddAction({ family }: { family: Family }) {
  const { t } = useTranslation('family');
  const me = family.myLinkedPersonId;
  if (me == null) {
    return (
      <Link
        to={addPersonPath(family.id)}
        className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
      >
        {t('home.addPerson')}
      </Link>
    );
  }
  const relatives = (['FATHER', 'MOTHER', 'PARTNER', 'CHILD'] as const).map((relation) => ({
    label: t(`home.relatives.${relation}`),
    to: addRelativePath(family.id, me, relation, 'home'),
  }));
  return (
    <AddRelativeMenu
      label={t('home.addRelative')}
      groups={[
        {
          choices: [
            ...relatives,
            { label: t('home.relatives.someoneElse'), to: addPersonPath(family.id) },
          ],
        },
      ]}
    />
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

export function SearchIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-5 shrink-0"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}
