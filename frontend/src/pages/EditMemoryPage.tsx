import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import type { components } from '../api/generated/schema';
import { Button, buttonClassName } from '../components/Button';
import { i18n } from '../i18n';
import { RelatedPersonsPicker, type RelatedPerson } from '../memories/RelatedPersonsPicker';
import {
  MEMORY_ERRORS,
  showServerFieldErrors,
  StoryFields,
  type StoryFormValues,
} from '../memories/StoryFields';
import { useUpdateMemory } from '../memories/useUpdateMemory';
import { MemoryRoute, canChangeMemory, memoryPath, type MemoryPageState } from './MemoryPage';

type Memory = components['schemas']['MemoryResponse'];

/**
 * SCREEN-014 — Edit Memory, for its creator or an ADMIN with a role that can write (OQ-041): the
 * fields of SCREEN-006. An archived Person already on the Memory may stay but not be added back;
 * when the Persons change, at least one ACTIVE Person stays (OQ-035, OQ-043). The form sends the
 * version it was loaded with; when someone changed the Memory meanwhile, the User reloads the
 * latest version before retrying, and form values are never merged (SCREEN-012).
 *
 * The form takes the place of the Memory in the history, and the Memory takes it back when the
 * form is left: `Archive` then still returns where the User came from before the Memory.
 */
export function EditMemoryPage() {
  return (
    <MemoryRoute>
      {({ familyId, memory, role, myUserId, reload }) =>
        canChangeMemory(memory, role, myUserId) ? (
          <EditMemoryForm familyId={familyId} loaded={memory} reload={reload} />
        ) : (
          <Navigate to={memoryPath(familyId, memory.id)} replace />
        )
      }
    </MemoryRoute>
  );
}

function toFormValues(memory: Memory): StoryFormValues {
  return { title: memory.title ?? '', content: memory.content ?? '' };
}

function toPersons(memory: Memory): RelatedPerson[] {
  return memory.relatedPersons.map((person) => ({
    id: person.id,
    name: person.displayName,
    archived: person.status !== 'ACTIVE',
  }));
}

function sameIds(a: RelatedPerson[], b: RelatedPerson[]) {
  const ids = new Set(a.map((person) => person.id));
  return a.length === b.length && b.every((person) => ids.has(person.id));
}

function EditMemoryForm({
  familyId,
  loaded,
  reload,
}: {
  familyId: string;
  loaded: Memory;
  reload: () => Promise<Memory | undefined>;
}) {
  const { t } = useTranslation(['memory', 'settings']);
  const navigate = useNavigate();
  // The version this form was built from; a background refresh of the Memory does not change it.
  const [base, setBase] = useState(loaded);
  const [persons, setPersons] = useState(() => toPersons(loaded));
  const form = useForm<StoryFormValues>({ defaultValues: toFormValues(loaded) });
  const update = useUpdateMemory(familyId, base.id);
  const back = memoryPath(familyId, base.id);
  const isConflict =
    update.error instanceof ApiError && update.error.code === 'CONCURRENT_MODIFICATION';
  const code = update.error instanceof ApiError ? update.error.code : null;
  const memoryError = MEMORY_ERRORS.find((known) => known === code);

  const personsChanged = !sameIds(persons, toPersons(base));
  // Changed Persons keep at least one ACTIVE Person (OQ-035); unchanged ones may all be archived (OQ-043).
  const lacksActivePerson = personsChanged && persons.every((person) => person.archived);

  const submit = form.handleSubmit((values) => {
    update.mutate(
      {
        version: base.version,
        body: {
          title: values.title.trim(),
          content: values.content,
          ...(personsChanged ? { relatedPersonIds: persons.map((person) => person.id) } : {}),
        },
      },
      {
        onSuccess: () => {
          const state: MemoryPageState = { saved: true };
          void navigate(back, { replace: true, state });
        },
        onError: (failure) => {
          showServerFieldErrors(form, failure);
        },
      },
    );
  });

  const reloadLatest = async () => {
    const latest = await reload();
    if (latest) {
      setBase(latest);
      setPersons(toPersons(latest));
      form.reset(toFormValues(latest));
      update.reset();
    }
  };

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={back}
          replace
          className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display text-text">{t('edit.title')}</h1>
      </header>
      <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-6">
        <StoryFields form={form} />
        <RelatedPersonsPicker
          familyId={familyId}
          persons={persons}
          onChange={setPersons}
          disabled={update.isPending}
        />
        {lacksActivePerson && (
          <p role="status" className="text-caption text-text-muted">
            {t('edit.activePersonRequired')}
          </p>
        )}
        {isConflict ? (
          <div
            role="alert"
            className="flex flex-col gap-3 rounded-xl border border-border bg-surface px-4 py-3"
          >
            <p className="text-body">{t('edit.conflict')}</p>
            <Button variant="secondary" onClick={() => void reloadLatest()}>
              {t('edit.reload')}
            </Button>
          </div>
        ) : (
          update.isError && (
            <p role="alert" className="text-body text-text">
              {memoryError ? t(`edit.errors.${memoryError}`) : errorMessage(i18n, update.error)}
            </p>
          )
        )}
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button
            type="submit"
            disabled={persons.length === 0 || lacksActivePerson || update.isPending || isConflict}
            className="sm:w-auto"
          >
            {t('edit.submit')}
          </Button>
          <Link to={back} replace className={buttonClassName('secondary', 'sm:w-auto')}>
            {t('edit.cancel')}
          </Link>
        </div>
      </form>
    </div>
  );
}
