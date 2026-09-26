import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Button, buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { MemoryCard } from '../components/MemoryCard';
import { Skeleton } from '../components/Skeleton';
import { addMemoryPath } from '../pages/AddMemoryPage';
import { memoryPath } from '../pages/MemoryPage';
import { usePersonMemories } from './usePersonMemories';

/**
 * SCREEN-005 Memories section: the Person's ACTIVE Memories, most recently added first, 20 at a
 * time with `Show more` (OQ-034), each opening SCREEN-013; `Add a memory` with this Person
 * preselected when `canAdd`; an empty state otherwise explained.
 */
export function PersonMemories({
  familyId,
  personId,
  name,
  canAdd,
}: {
  familyId: string;
  personId: string;
  name: string;
  canAdd: boolean;
}) {
  const { t } = useTranslation('memory');
  const memories = usePersonMemories(familyId, personId);
  const items = memories.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <section aria-labelledby="profile-memories" className="flex flex-col gap-4">
      <h2 id="profile-memories" className="text-section text-text">
        {t('profile.title')}
      </h2>
      {memories.isError ? (
        <ErrorState error={memories.error} onRetry={() => void memories.refetch()} />
      ) : memories.isPending ? (
        <div className="flex flex-col gap-3" aria-busy="true">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : items.length === 0 ? (
        <p className="text-body text-text-muted">{t('profile.empty', { name })}</p>
      ) : (
        <>
          <ul aria-labelledby="profile-memories" className="flex flex-col gap-3">
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
              {t('profile.more')}
            </Button>
          )}
        </>
      )}
      {canAdd && (
        <Link
          to={addMemoryPath(familyId, { personId })}
          className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
        >
          {t('profile.add')}
        </Link>
      )}
    </section>
  );
}
