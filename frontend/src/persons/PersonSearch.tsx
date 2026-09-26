import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { lifeYears } from '../components/PersonCard';
import { Skeleton } from '../components/Skeleton';
import { TextField } from '../components/TextField';
import { kinshipLabel } from './kinship';
import { useDebouncedValue } from './useDebouncedValue';
import {
  searchTerm,
  usePersonSearch,
  type PersonSummary,
  type SearchStatus,
} from './usePersonSearch';

/** About 250 ms after the last keystroke (SCREEN-007). */
const DEBOUNCE_MS = 250;

/**
 * SCREEN-007 — Search Person: a search field and the matching Persons, each with avatar, name,
 * birth/death years and what they are to the current User. The search starts after 2 characters;
 * below that, the first page of the Family is listed when `listWhenEmpty`. What a result does is
 * up to the entry point (`onSelect`). `status="ARCHIVED"` is the ADMIN "Archived people" view.
 * `excludedMessage` replaces the "no result" message when every Person found is excluded.
 */
export function PersonSearch({
  familyId,
  label,
  onSelect,
  excludeIds = [],
  listWhenEmpty = true,
  autoFocus = false,
  status = 'ACTIVE',
  excludedMessage,
}: {
  familyId: string;
  label: string;
  onSelect: (person: PersonSummary) => void;
  excludeIds?: string[];
  listWhenEmpty?: boolean;
  autoFocus?: boolean;
  status?: SearchStatus;
  excludedMessage?: string;
}) {
  const { t } = useTranslation('person');
  const [text, setText] = useState('');
  const term = searchTerm(useDebouncedValue(text, DEBOUNCE_MS));
  const enabled = listWhenEmpty || term !== '';
  const search = usePersonSearch(familyId, term, { enabled, status });
  const found = search.data?.pages.flatMap((page) => page.items) ?? [];
  const persons = found.filter((person) => !excludeIds.includes(person.id));

  let results;
  if (!enabled) {
    results = null;
  } else if (search.isError) {
    results = <ErrorState error={search.error} onRetry={() => void search.refetch()} />;
  } else if (search.isPending) {
    results = (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
      </div>
    );
  } else if (persons.length === 0) {
    results = (
      <p role="status" className="text-body text-text-muted">
        {excludedMessage !== undefined && found.length > 0
          ? excludedMessage
          : term !== ''
            ? t('search.noResult', { text: term })
            : status === 'ARCHIVED'
              ? t('archivedPeople.empty')
              : t('search.emptyFamily')}
      </p>
    );
  } else {
    results = (
      <>
        <ul
          aria-label={t('search.results')}
          aria-busy={search.isPlaceholderData}
          className={`flex flex-col gap-2 transition-opacity ${search.isPlaceholderData ? 'opacity-70' : ''}`}
        >
          {persons.map((person) => (
            <li key={person.id}>
              <SearchResult
                person={person}
                onSelect={() => {
                  onSelect(person);
                }}
              />
            </li>
          ))}
        </ul>
        {search.hasNextPage && (
          <Button
            variant="secondary"
            disabled={search.isFetchingNextPage}
            onClick={() => void search.fetchNextPage()}
          >
            {t('search.more')}
          </Button>
        )}
      </>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <TextField
        type="search"
        label={label}
        placeholder={t('search.placeholder')}
        value={text}
        maxLength={200}
        autoComplete="off"
        // The User opened the search to type in it.
        autoFocus={autoFocus}
        onChange={(event) => {
          setText(event.target.value);
        }}
      />
      {results}
    </div>
  );
}

/** A result card (SCREEN-007): avatar, display name, years when known, relationship to the User. */
function SearchResult({ person, onSelect }: { person: PersonSummary; onSelect: () => void }) {
  const { t } = useTranslation('person');
  const name = person.displayName ?? person.firstName;
  const years = lifeYears(person);
  const code = person.relationshipToCurrentUser;
  const relationship = code == null ? null : kinshipLabel(t, code, person.gender);

  return (
    <button
      type="button"
      onClick={onSelect}
      className="flex min-h-16 w-full items-center gap-3 rounded-xl border border-border bg-surface px-4 py-2 text-left transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Avatar displayName={name} photoUrl={person.profilePictureUrl} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="text-body font-semibold break-words text-text">{name}</span>
        {years && <span className="text-caption text-text-muted">{years}</span>}
        {relationship && (
          <span className="self-start rounded-full bg-background px-3 text-caption font-semibold text-primary">
            {relationship}
          </span>
        )}
      </span>
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        className="size-5 shrink-0 text-text-muted"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="m9 6 6 6-6 6" />
      </svg>
    </button>
  );
}
