import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { AccountLink } from '../components/AccountLink';
import { Button, buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { MemoryCard } from '../components/MemoryCard';
import { NavigationBar } from '../components/NavigationBar';
import { Skeleton } from '../components/Skeleton';
import { YearStrip } from '../components/YearStrip';
import { useFamily } from '../families/useFamily';
import { DEFAULT_LANGUAGE, isSupportedLanguage } from '../i18n/language';
import { familyStoryPath, parseStoryYear, type StoryEntry } from '../memories/storyPath';
import { useFamilyMemories, type StoryFilter } from '../memories/useFamilyMemories';
import { useFamilyStoryYears } from '../memories/useFamilyStoryYears';
import { formatDayOfYear } from '../persons/formatPartialDate';
import { addMemoryPath } from './AddMemoryPage';
import { familyHomePath } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { memoryPath } from './MemoryPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const LINK_CLASS =
  'inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline';

/**
 * SCREEN-016 — What happened in {year}, or the undated Memories (mvp.md §20, OQ-064, OQ-067), for
 * every member, VIEWER included: the strip of years kept in view with the current entry marked,
 * `Previous year` / `Next year` to its neighbours, then the Memories in the server's order, each
 * with its day when exact, 20 at a time. A year left without Memory explains it; a year that is not
 * a number from 1 to 9999 is not found; another Family's year is "Family not found".
 */
export function FamilyStoryPage() {
  const { t, i18n } = useTranslation('memory');
  const { familyId = '', year: yearParam } = useParams();
  // `story/undated` has no `:year` parameter.
  const current: StoryEntry | null =
    yearParam === undefined ? 'undated' : parseStoryYear(yearParam);
  const isValidId = UUID.test(familyId);
  const isValid = isValidId && current !== null;
  const story: StoryFilter = typeof current === 'number' ? { year: current } : { undated: true };
  const family = useFamily(familyId, { enabled: isValidId });
  const years = useFamilyStoryYears(familyId, { enabled: isValid });
  const memories = useFamilyMemories(familyId, { enabled: isValid, story });
  const heading = useRef<HTMLHeadingElement>(null);
  // Opening a year, from the strip or its neighbours, starts reading at its title.
  useEffect(() => {
    heading.current?.focus();
  }, [current]);

  const notFound = [family.error, years.error, memories.error].some(
    (error) => error instanceof ApiError && error.status === 404,
  );
  if (!isValidId || notFound) {
    return <FamilyNotFoundPage />;
  }
  if (current === null) {
    return family.isPending ? <Skeleton className="h-20 w-full" /> : <StoryYearNotFound />;
  }

  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  const canAdd = family.data?.myRole === 'ADMIN' || family.data?.myRole === 'CONTRIBUTOR';
  const items = memories.data?.pages.flatMap((page) => page.items) ?? [];
  const entries: StoryEntry[] = [
    ...(years.data?.years.map(({ year }) => year) ?? []),
    ...((years.data?.undatedMemoryCount ?? 0) > 0 ? ['undated' as const] : []),
  ];

  // The strip stays in view while another year loads, so that the reader keeps their place.
  const strip = years.data !== undefined && entries.length > 0 && (
    <>
      <YearStrip
        familyId={familyId}
        years={years.data.years}
        undatedMemoryCount={years.data.undatedMemoryCount}
        current={current}
      />
      <Neighbours familyId={familyId} entries={entries} current={current} />
    </>
  );

  let content;
  if (family.isError || years.isError || memories.isError) {
    const retry = () => {
      if (family.isError) void family.refetch();
      if (years.isError) void years.refetch();
      if (memories.isError) void memories.refetch();
    };
    content = <ErrorState error={family.error ?? years.error ?? memories.error} onRetry={retry} />;
  } else if (family.isPending || years.isPending || memories.isPending) {
    content = (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    );
  } else {
    content = (
      <>
        {canAdd && (
          <Link
            to={addMemoryPath(familyId)}
            className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
          >
            {t('story.tell')}
          </Link>
        )}
        {items.length === 0 ? (
          <p className="text-body text-text-muted">
            {entries.length === 0
              ? t('story.empty')
              : current === 'undated'
                ? t('story.emptyUndated')
                : t('story.emptyYear', { year: String(current) })}
          </p>
        ) : (
          <ul aria-labelledby="family-story-year" className="flex flex-col gap-3">
            {items.map((memory) => (
              <li key={memory.id}>
                <MemoryCard
                  title={memory.title ?? ''}
                  content={memory.content ?? null}
                  thumbnailUrl={memory.photos[0]?.thumbnailUrl ?? null}
                  to={memoryPath(familyId, memory.id)}
                  date={formatDayOfYear(memory.happenedAt, language)}
                />
              </li>
            ))}
          </ul>
        )}
        {memories.hasNextPage && (
          <Button
            variant="secondary"
            className="sm:w-auto sm:self-start"
            disabled={memories.isFetchingNextPage}
            onClick={() => void memories.fetchNextPage()}
          >
            {t('story.more')}
          </Button>
        )}
      </>
    );
  }

  return (
    <div className="flex min-w-0 flex-1 flex-col gap-6 pb-24">
      <Link to={familyHomePath(familyId)} className={LINK_CLASS}>
        {t('story.back')}
      </Link>
      <header className="flex items-center gap-3">
        <h1
          id="family-story-year"
          ref={heading}
          tabIndex={-1}
          className="text-display min-w-0 flex-1 break-words text-text outline-none"
        >
          {current === 'undated'
            ? t('story.undated')
            : t('story.yearTitle', { year: String(current) })}
        </h1>
        <AccountLink />
      </header>
      {strip}
      {content}
      <NavigationBar familyId={familyId} />
    </div>
  );
}

/**
 * `Previous year` and `Next year`: the neighbouring entries of the strip, the undated Memories
 * last, disabled at its ends (SCREEN-016). A year no longer in the strip has the entries around it.
 */
function Neighbours({
  familyId,
  entries,
  current,
}: {
  familyId: string;
  entries: StoryEntry[];
  current: StoryEntry;
}) {
  const { t } = useTranslation('memory');
  const navigate = useNavigate();
  const order = (entry: StoryEntry) => (entry === 'undated' ? Infinity : entry);
  const previous = entries.findLast((entry) => order(entry) < order(current));
  const next = entries.find((entry) => order(entry) > order(current));
  const go = (entry: StoryEntry | undefined) => {
    if (entry !== undefined) void navigate(familyStoryPath(familyId, entry));
  };
  return (
    <div className="grid grid-cols-2 gap-3">
      <Button
        variant="secondary"
        className="disabled:opacity-40"
        disabled={previous === undefined}
        onClick={() => {
          go(previous);
        }}
      >
        {t('story.previous')}
      </Button>
      <Button
        variant="secondary"
        className="disabled:opacity-40"
        disabled={next === undefined}
        onClick={() => {
          go(next);
        }}
      >
        {t('story.next')}
      </Button>
    </div>
  );
}

/** A year that is not a number from 1 to 9999 (SCREEN-016). */
function StoryYearNotFound() {
  const { t } = useTranslation('memory');
  const { familyId = '' } = useParams();
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <h1 className="text-display text-text">{t('story.notFound.title')}</h1>
      <p className="text-body text-text-muted">{t('story.notFound.body')}</p>
      <Link to={familyHomePath(familyId)} className={buttonClassName('secondary', 'sm:w-auto')}>
        {t('story.notFound.back')}
      </Link>
    </div>
  );
}
