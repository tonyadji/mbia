import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { lifeYears } from '../components/PersonCard';
import { displayNameOf } from '../pages/PersonProfilePage';
import type { PersonSummary } from './usePersonSearch';

/**
 * The possible duplicates reported when adding a Person (SCREEN-004,
 * person-relationships-collaboration.md §4.1): each similar Person as a card with `View existing
 * person`, and `Create anyway`. Nothing is ever merged automatically.
 */
export function DuplicateCandidates({
  candidates,
  disabled,
  onView,
  onCreateAnyway,
}: {
  candidates: PersonSummary[];
  disabled: boolean;
  onView: (person: PersonSummary) => void;
  onCreateAnyway: () => void;
}) {
  const { t } = useTranslation('person');
  return (
    <section
      role="alert"
      aria-labelledby="possible-duplicates"
      className="flex flex-col gap-3 rounded-xl border border-accent bg-surface px-4 py-3"
    >
      <h2 id="possible-duplicates" className="text-body font-semibold text-text">
        {t('duplicate.title', { count: candidates.length })}
      </h2>
      <ul className="flex flex-col gap-3">
        {candidates.map((candidate) => {
          const name = displayNameOf(candidate);
          const years = lifeYears(candidate);
          return (
            <li
              key={candidate.id}
              className="flex flex-col gap-3 rounded-xl border border-border bg-surface px-4 py-3 sm:flex-row sm:items-center"
            >
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <Avatar displayName={name} />
                <div className="flex min-w-0 flex-col">
                  <p className="text-body font-semibold break-words text-text">{name}</p>
                  {years && <p className="text-caption text-text-muted">{years}</p>}
                </div>
              </div>
              <Button
                variant="secondary"
                className="sm:w-auto"
                disabled={disabled}
                aria-label={t('duplicate.viewNamed', { name })}
                onClick={() => {
                  onView(candidate);
                }}
              >
                {t('duplicate.view')}
              </Button>
            </li>
          );
        })}
      </ul>
      <Button variant="secondary" disabled={disabled} onClick={onCreateAnyway}>
        {t('duplicate.createAnyway')}
      </Button>
    </section>
  );
}
