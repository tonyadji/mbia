import { useQueries } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { kinshipPathSentences, type PathPerson } from '../persons/kinship';
import { useKinship } from '../persons/useKinship';
import { personQueryKey } from '../persons/usePerson';

/**
 * "See how" under a relationship label: the path from the current User's Person to `personId`, one
 * sentence per step (localization-and-kinship-labels.md §3, §4). Persons of the path that the
 * tree does not hold are loaded with their profile.
 */
export function KinshipPath({
  familyId,
  fromPersonId,
  personId,
  known,
}: {
  familyId: string;
  fromPersonId: string;
  personId: string;
  known: Record<string, PathPerson>;
}) {
  const { t } = useTranslation('tree');
  const [open, setOpen] = useState(false);

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => {
          setOpen((value) => !value);
        }}
        className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline underline-offset-4"
      >
        {t('quickView.seeHow')}
      </button>
      {open && (
        <PathSentences
          familyId={familyId}
          fromPersonId={fromPersonId}
          personId={personId}
          known={known}
        />
      )}
    </div>
  );
}

function PathSentences({
  familyId,
  fromPersonId,
  personId,
  known,
}: {
  familyId: string;
  fromPersonId: string;
  personId: string;
  known: Record<string, PathPerson>;
}) {
  const { t } = useTranslation('person');
  const kinship = useKinship(familyId, fromPersonId, personId);
  const path = kinship.data?.path ?? [];
  const missing = [...new Set(path.flatMap((step) => [step.fromPersonId, step.toPersonId]))].filter(
    (id) => !(id in known),
  );
  const persons = useQueries({
    queries: missing.map((id) => ({
      queryKey: personQueryKey(familyId, id),
      queryFn: async () => {
        const { data } = await apiClient.GET('/families/{familyId}/persons/{personId}', {
          params: { path: { familyId, personId: id } },
        });
        if (data === undefined) throw new Error('GET /persons/{personId} returned no body');
        return data;
      },
    })),
  });

  const failed = kinship.error ?? persons.find((query) => query.isError)?.error;
  if (failed) {
    return (
      <ErrorState
        error={failed}
        onRetry={() => {
          void kinship.refetch();
          for (const query of persons) void query.refetch();
        }}
      />
    );
  }
  if (kinship.isPending || persons.some((query) => query.isPending)) {
    return <Skeleton className="h-12 w-full" />;
  }
  const all: Record<string, PathPerson> = { ...known };
  for (const query of persons) {
    if (query.data) {
      all[query.data.id] = {
        displayName: query.data.displayName ?? query.data.firstName,
        gender: query.data.gender,
      };
    }
  }
  return (
    <ol className="flex flex-col gap-1 rounded-xl bg-background px-4 py-3 text-body text-text">
      {kinshipPathSentences(t, path, all).map((sentence) => (
        <li key={sentence}>{sentence}</li>
      ))}
    </ol>
  );
}
