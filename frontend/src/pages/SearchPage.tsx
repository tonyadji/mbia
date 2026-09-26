import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { ErrorState } from '../components/ErrorState';
import { NavigationBar } from '../components/NavigationBar';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { PersonSearch } from '../persons/PersonSearch';
import { familyTreePath } from '../tree/treePath';
import { archivedPeoplePath } from './ArchivedPeoplePage';
import { familyHomePath } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { personPath } from './PersonProfilePage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * SCREEN-007 from general navigation (a result opens the profile) or from the tree (`from=tree`,
 * a result recenters the tree; `focus` is the tree's focus, to go back to it).
 */
export function searchPath(familyId: string, tree?: { focus?: string | undefined }) {
  if (!tree) return `/families/${familyId}/search`;
  const query = new URLSearchParams({ from: 'tree' });
  if (tree.focus) query.set('focus', tree.focus);
  return `/families/${familyId}/search?${query.toString()}`;
}

/**
 * SCREEN-007 — Search Person, for any ACTIVE member of the Family. From general navigation, the
 * ADMIN also finds the "Archived people" view; never from the tree.
 */
export function SearchPage() {
  const { t } = useTranslation(['person', 'settings']);
  const { familyId = '' } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });

  if (!isValidId || (family.error instanceof ApiError && family.error.status === 404)) {
    return <FamilyNotFoundPage />;
  }
  const fromTree = params.get('from') === 'tree';
  const focus = params.get('focus');
  const backPath = fromTree
    ? familyTreePath(familyId, focus !== null && UUID.test(focus) ? focus : undefined)
    : familyHomePath(familyId);

  let content;
  if (family.isError) {
    content = <ErrorState error={family.error} onRetry={() => void family.refetch()} />;
  } else if (family.isPending) {
    content = <Skeleton className="h-12" />;
  } else {
    content = (
      <PersonSearch
        familyId={familyId}
        label={t('person:search.label')}
        autoFocus
        onSelect={(person) => {
          void navigate(
            fromTree ? familyTreePath(familyId, person.id) : personPath(familyId, person.id),
          );
        }}
      />
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 pb-24">
      <header className="flex flex-col gap-4">
        <Link
          to={backPath}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display text-text">{t('person:search.title')}</h1>
      </header>
      {content}
      {!fromTree && family.data?.myRole === 'ADMIN' && (
        <Link
          to={archivedPeoplePath(familyId)}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('person:archivedPeople.title')}
        </Link>
      )}
      <NavigationBar familyId={familyId} />
    </div>
  );
}
