import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { ErrorState } from '../components/ErrorState';
import { NavigationBar } from '../components/NavigationBar';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { PersonSearch } from '../persons/PersonSearch';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { personPath } from './PersonProfilePage';
import { searchPath } from './SearchPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function archivedPeoplePath(familyId: string) {
  return `/families/${familyId}/archived`;
}

/**
 * SCREEN-007, the ADMIN "Archived people" view (mvp.md §13): the same search over ARCHIVED Persons
 * only; a result opens the archived profile, where the ADMIN restores it. Reached from general
 * navigation only, never from the tree or Add Relative.
 */
export function ArchivedPeoplePage() {
  const { t } = useTranslation(['person', 'settings']);
  const { familyId = '' } = useParams();
  const navigate = useNavigate();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });

  if (!isValidId || (family.error instanceof ApiError && family.error.status === 404)) {
    return <FamilyNotFoundPage />;
  }

  let content;
  if (family.isError) {
    content = <ErrorState error={family.error} onRetry={() => void family.refetch()} />;
  } else if (family.isPending) {
    content = <Skeleton className="h-12" />;
  } else if (family.data.myRole !== 'ADMIN') {
    content = <p className="text-body text-text-muted">{t('person:archivedPeople.adminOnly')}</p>;
  } else {
    content = (
      <>
        <p className="text-body text-text-muted">{t('person:archivedPeople.intro')}</p>
        <PersonSearch
          familyId={familyId}
          label={t('person:search.label')}
          status="ARCHIVED"
          onSelect={(person) => {
            void navigate(personPath(familyId, person.id));
          }}
        />
      </>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 pb-24">
      <header className="flex flex-col gap-4">
        <Link
          to={searchPath(familyId)}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display text-text">{t('person:archivedPeople.title')}</h1>
      </header>
      {content}
      <NavigationBar familyId={familyId} />
    </div>
  );
}
