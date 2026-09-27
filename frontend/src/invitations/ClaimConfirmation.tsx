import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { lifeYears } from '../components/PersonCard';
import type { PersonSummary } from '../persons/usePersonSearch';
import { parentSentence } from '../persons/kinship';

type Candidate = Pick<
  PersonSummary,
  'displayName' | 'firstName' | 'gender' | 'birth' | 'death' | 'isDeceased' | 'profilePictureUrl'
>;

/** `Later`: a quiet text action under the buttons. */
export const laterClassName =
  'inline-flex min-h-12 items-center justify-center self-center text-body font-semibold text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-40';

/** One parent when known, otherwise the years, so that two Persons with the same name differ (mvp.md §18). */
export function useDescribeCandidate() {
  const { t } = useTranslation('person');
  return (person: Candidate, parentName: string | null) => {
    const displayName = person.displayName ?? person.firstName;
    return parentName === null
      ? lifeYears(person)
      : parentSentence(t, { displayName, gender: person.gender }, parentName);
  };
}

/**
 * "Are you {displayName}?" (SCREEN-010, OQ-050): the Person's name, one parent or the years, and
 * `Yes, it's me`, `No`, `Later`.
 */
export function ClaimConfirmation({
  person,
  parentName,
  isPending,
  error,
  onYes,
  onNo,
  onLater,
}: {
  person: Candidate;
  parentName: string | null;
  isPending: boolean;
  error: string | null;
  onYes: () => void;
  onNo: () => void;
  onLater: () => void;
}) {
  const { t } = useTranslation('invitation');
  const describe = useDescribeCandidate();
  const name = person.displayName ?? person.firstName;
  const detail = describe(person, parentName);

  return (
    <section className="flex flex-1 flex-col gap-6">
      <h1 className="text-display break-words text-text">{t('onboarding.areYou', { name })}</h1>
      <div className="flex items-center gap-3 rounded-xl border border-border bg-surface px-4 py-3">
        <Avatar displayName={name} photoUrl={person.profilePictureUrl} />
        <span className="flex min-w-0 flex-col">
          <span className="text-body font-semibold break-words text-text">{name}</span>
          {detail && <span className="text-caption text-text-muted">{detail}</span>}
        </span>
      </div>
      {error && (
        <p role="alert" className="text-body text-text">
          {error}
        </p>
      )}
      <div className="flex flex-col gap-3">
        <Button disabled={isPending} onClick={onYes}>
          {t('onboarding.yes')}
        </Button>
        <Button variant="secondary" disabled={isPending} onClick={onNo}>
          {t('onboarding.no')}
        </Button>
        <button type="button" className={laterClassName} disabled={isPending} onClick={onLater}>
          {t('onboarding.later')}
        </button>
      </div>
    </section>
  );
}
