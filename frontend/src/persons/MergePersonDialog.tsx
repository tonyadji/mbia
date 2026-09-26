import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { Modal } from '../components/Modal';
import { lifeYears } from '../components/PersonCard';
import { Skeleton } from '../components/Skeleton';
import { displayNameOf } from '../pages/PersonProfilePage';
import { PersonSearch } from './PersonSearch';
import { usePerson, type Person } from './usePerson';
import type { PersonSummary } from './usePersonSearch';

const REFUSAL_REASONS = ['DIFFERENT_LINKED_USERS', 'SELF_RELATIONSHIP', 'PARENTAL_CYCLE'] as const;

/**
 * SCREEN-COMPONENT-004 — Merge Persons, opened by an ADMIN from the profile of a duplicate: choose
 * the profile to keep, compare both, confirm. The kept profile keeps its values; the duplicate is
 * merged into it with its family links. A refusal is explained and never forced (OQ-026).
 */
export function MergePersonDialog({
  familyId,
  source,
  pending,
  error,
  onCancel,
  onConfirm,
}: {
  familyId: string;
  source: Person;
  pending: boolean;
  error: unknown;
  onCancel: () => void;
  onConfirm: (target: Person) => void;
}) {
  const { t } = useTranslation('person');
  const [chosen, setChosen] = useState<PersonSummary | null>(null);
  const sourceName = displayNameOf(source);

  return (
    <Modal message={t('merge.title', { name: sourceName })} size="lg" onClose={onCancel}>
      {chosen === null ? (
        <>
          <PersonSearch
            familyId={familyId}
            label={t('merge.search')}
            excludeIds={[source.id]}
            onSelect={setChosen}
          />
          <Button variant="secondary" onClick={onCancel} className="sm:w-auto sm:self-end">
            {t('merge.cancel')}
          </Button>
        </>
      ) : (
        <MergeComparison
          familyId={familyId}
          source={source}
          targetId={chosen.id}
          pending={pending}
          error={error}
          onChangeTarget={() => {
            setChosen(null);
          }}
          onCancel={onCancel}
          onConfirm={onConfirm}
        />
      )}
    </Modal>
  );
}

/** Both profiles side by side, what the merge does, and the explicit confirmation. */
function MergeComparison({
  familyId,
  source,
  targetId,
  pending,
  error,
  onChangeTarget,
  onCancel,
  onConfirm,
}: {
  familyId: string;
  source: Person;
  targetId: string;
  pending: boolean;
  error: unknown;
  onChangeTarget: () => void;
  onCancel: () => void;
  onConfirm: (target: Person) => void;
}) {
  const { t, i18n } = useTranslation('person');
  // The full profile gives the current version of the kept Person.
  const target = usePerson(familyId, targetId);
  const refusal = refusalMessage(error, t) ?? (error != null ? errorMessage(i18n, error) : null);

  if (target.isError) {
    return (
      <p role="alert" className="text-body text-text">
        {errorMessage(i18n, target.error)}
      </p>
    );
  }
  if (target.isPending) {
    return <Skeleton className="h-24 w-full" />;
  }
  const keptName = displayNameOf(target.data);
  const mergedName = displayNameOf(source);
  return (
    <>
      <div className="grid gap-3 sm:grid-cols-2">
        <Summary label={t('merge.kept')} person={target.data} />
        <Summary label={t('merge.merged')} person={source} />
      </div>
      <ul className="flex list-disc flex-col gap-1 pl-5 text-body text-text">
        <li>{t('merge.explain.kept', { name: keptName })}</li>
        <li>{t('merge.explain.merged', { name: mergedName, kept: keptName })}</li>
        <li>{t('merge.explain.links')}</li>
        <li>{t('merge.explain.values')}</li>
      </ul>
      {refusal && (
        <p role="alert" className="text-body text-text">
          {refusal}
        </p>
      )}
      <div className="flex flex-col gap-3 sm:flex-row-reverse">
        <Button
          disabled={pending}
          className="sm:w-auto"
          onClick={() => {
            onConfirm(target.data);
          }}
        >
          {t('merge.confirm')}
        </Button>
        <Button
          variant="secondary"
          disabled={pending}
          className="sm:w-auto"
          onClick={onChangeTarget}
        >
          {t('merge.change')}
        </Button>
        <Button variant="secondary" className="sm:w-auto" onClick={onCancel}>
          {t('merge.cancel')}
        </Button>
      </div>
    </>
  );
}

function Summary({ label, person }: { label: string; person: Person }) {
  const name = displayNameOf(person);
  const years = lifeYears(person);
  return (
    <div className="flex flex-col gap-2 rounded-xl border border-border bg-surface px-4 py-3">
      <p className="text-caption text-text-muted">{label}</p>
      <div className="flex items-center gap-3">
        <Avatar displayName={name} />
        <div className="flex min-w-0 flex-col">
          <p className="text-body font-semibold break-words text-text">{name}</p>
          {years && <p className="text-caption text-text-muted">{years}</p>}
        </div>
      </div>
    </div>
  );
}

/** The reason of a `PERSON_MERGE_CONFLICT`, in family language (OQ-026). */
function refusalMessage(error: unknown, t: ReturnType<typeof useTranslation<'person'>>['t']) {
  if (!(error instanceof ApiError) || error.code !== 'PERSON_MERGE_CONFLICT') return null;
  const reason = REFUSAL_REASONS.find((known) => known === error.details?.reason);
  return reason ? t(`merge.refused.${reason}`) : null;
}
