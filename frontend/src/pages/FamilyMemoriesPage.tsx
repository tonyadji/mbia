import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { AccountLink } from '../components/AccountLink';
import { Button, buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { MemoryCard } from '../components/MemoryCard';
import { NavigationBar } from '../components/NavigationBar';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { useFamilyMemories } from '../memories/useFamilyMemories';
import { addMemoryPath } from './AddMemoryPage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { memoryPath } from './MemoryPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function familyMemoriesPath(familyId: string) {
  return `/families/${familyId}/memories`;
}

/**
 * SCREEN-015 — Family Memories, the `Memories` tab (family-tree-ux.md §4): the Family's ACTIVE
 * Memories, most recently added first, 20 at a time with `Show more` (OQ-034), each opening
 * SCREEN-013. No Photos / Stories filter: only stories exist (phase-3-family-memories.md §3.1).
 */
export function FamilyMemoriesPage() {
  const { t } = useTranslation('memory');
  const { familyId = '' } = useParams();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });
  const memories = useFamilyMemories(familyId, { enabled: isValidId });

  const notFound = [family.error, memories.error].some(
    (error) => error instanceof ApiError && error.status === 404,
  );
  if (!isValidId || notFound) {
    return <FamilyNotFoundPage />;
  }

  const canAdd = family.data?.myRole === 'ADMIN' || family.data?.myRole === 'CONTRIBUTOR';
  const items = memories.data?.pages.flatMap((page) => page.items) ?? [];
  const addMemory = canAdd && (
    <Link
      to={addMemoryPath(familyId)}
      className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
    >
      {t('family.add')}
    </Link>
  );

  let content;
  if (family.isError || memories.isError) {
    const retry = () => {
      if (family.isError) void family.refetch();
      if (memories.isError) void memories.refetch();
    };
    content = <ErrorState error={family.error ?? memories.error} onRetry={retry} />;
  } else if (family.isPending || memories.isPending) {
    content = (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    );
  } else if (items.length === 0) {
    content = (
      <>
        <p className="text-body text-text-muted">{t('family.empty')}</p>
        {addMemory}
      </>
    );
  } else {
    content = (
      <>
        {addMemory}
        <ul aria-labelledby="family-memories" className="flex flex-col gap-3">
          {items.map((memory) => (
            <li key={memory.id}>
              <MemoryCard
                title={memory.title ?? ''}
                content={memory.content ?? ''}
                to={memoryPath(familyId, memory.id)}
              />
            </li>
          ))}
        </ul>
        {memories.hasNextPage && (
          <Button
            variant="secondary"
            className="sm:w-auto sm:self-start"
            disabled={memories.isFetchingNextPage}
            onClick={() => void memories.fetchNextPage()}
          >
            {t('family.more')}
          </Button>
        )}
      </>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 pb-24">
      <header className="flex items-center gap-3">
        <h1 id="family-memories" className="text-display min-w-0 flex-1 text-text">
          {t('family.title')}
        </h1>
        <AccountLink />
      </header>
      {content}
      <NavigationBar familyId={familyId} />
    </div>
  );
}
