import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import type { components } from '../api/generated/schema';
import { Button, buttonClassName } from '../components/Button';
import { familyQueryKey } from '../families/useFamily';
import { i18n } from '../i18n';
import {
  fromMemoryPhotos,
  MemoryPhotosField,
  takenAtError,
  toPhotoInputs,
  type MemoryPhotoDraft,
} from '../memories/MemoryPhotosField';
import {
  dateDraftError,
  fromPartialDate,
  PartialDateField,
  sameDate,
  toPartialDate,
  type DateDraft,
  type DateError,
} from '../memories/PartialDateField';
import { RelatedPersonsPicker, type RelatedPerson } from '../memories/RelatedPersonsPicker';
import {
  MEMORY_ERRORS,
  PHOTO_ERRORS,
  serverDateError,
  showServerFieldErrors,
  StoryFields,
  type StoryFormValues,
} from '../memories/StoryFields';
import { useUpdateMemory } from '../memories/useUpdateMemory';
import { MemoryRoute, canChangeMemory, memoryPath, type MemoryPageState } from './MemoryPage';

type Memory = components['schemas']['MemoryResponse'];

/**
 * SCREEN-014 — Edit Memory, for its creator or an ADMIN with a role that can write (OQ-041): the
 * fields of SCREEN-006, with the Memory's date, changed or removed (OQ-063), its photos, described, removed or added within the Family's
 * limit; the text may be emptied only while a photo remains (mvp.md §17). An archived Person already on the Memory may stay but not be added back;
 * when the Persons change, at least one ACTIVE Person stays (OQ-035, OQ-043). The form sends the
 * version it was loaded with; when someone changed the Memory meanwhile, the User reloads the
 * latest version before retrying, and form values are never merged (SCREEN-012): photos sent but
 * not saved are dropped (the cleanup removes them, OQ-036).
 *
 * The form takes the place of the Memory in the history, and the Memory takes it back when the
 * form is left: `Archive` then still returns where the User came from before the Memory.
 */
export function EditMemoryPage() {
  return (
    <MemoryRoute>
      {({ familyId, memory, role, myUserId, photoLimit, reload }) =>
        canChangeMemory(memory, role, myUserId) && photoLimit !== undefined ? (
          <EditMemoryForm
            familyId={familyId}
            loaded={memory}
            photoLimit={photoLimit}
            reload={reload}
          />
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
    photoUrl: person.profilePictureUrl,
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
  photoLimit,
  reload,
}: {
  familyId: string;
  loaded: Memory;
  photoLimit: number;
  reload: () => Promise<Memory | undefined>;
}) {
  const { t } = useTranslation(['memory', 'settings']);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  // The version this form was built from; a background refresh of the Memory does not change it.
  const [base, setBase] = useState(loaded);
  const [persons, setPersons] = useState(() => toPersons(loaded));
  const [photos, setPhotos] = useState<MemoryPhotoDraft[]>(() => fromMemoryPhotos(loaded.photos));
  const [happenedAt, setHappenedAt] = useState<DateDraft>(() => fromPartialDate(loaded.happenedAt));
  // The server's refusal of the date, until the User changes it.
  const [serverDate, setServerDate] = useState<DateError | null>(null);
  const formElement = useRef<HTMLFormElement>(null);
  const [triedToSave, setTriedToSave] = useState(false);
  const form = useForm<StoryFormValues>({ defaultValues: toFormValues(loaded) });
  const update = useUpdateMemory(familyId, base.id);
  const back = memoryPath(familyId, base.id);
  const isConflict =
    update.error instanceof ApiError && update.error.code === 'CONCURRENT_MODIFICATION';
  const code = update.error instanceof ApiError ? update.error.code : null;
  const memoryError = MEMORY_ERRORS.find((known) => known === code);
  const photoError = PHOTO_ERRORS.find((known) => known === code);
  const hasReadyPhoto = photos.some((photo) => photo.status === 'ready');
  // `Save` waits until every photo added is ready.
  const photosPending = photos.some((photo) => photo.status !== 'ready');

  // The text becomes optional, or required again, with the photos that remain.
  const { isSubmitted } = form.formState;
  useEffect(() => {
    if (isSubmitted) void form.trigger('content');
  }, [hasReadyPhoto, isSubmitted, form]);

  const heading = useRef<HTMLHeadingElement>(null);
  const conflict = useRef<HTMLDivElement>(null);
  // The screen is announced by its title; the keyboard of a phone opens only when the User picks a field.
  useEffect(() => {
    heading.current?.focus();
  }, []);
  // A refusal because someone else changed the Memory is read at once, with its reload action.
  useEffect(() => {
    if (isConflict) conflict.current?.focus();
  }, [isConflict]);
  // Once the latest version is shown, the User continues from its title.
  const reloaded = useRef(false);
  useEffect(() => {
    if (reloaded.current) form.setFocus('title');
  }, [base, form]);

  const personsChanged = !sameIds(persons, toPersons(base));
  // Changed Persons keep at least one ACTIVE Person (OQ-035); unchanged ones may all be archived (OQ-043).
  const lacksActivePerson = personsChanged && persons.every((person) => person.archived);

  const dateChanged = !sameDate(happenedAt, fromPartialDate(base.happenedAt));
  const dateError =
    serverDate ?? (triedToSave ? dateDraftError(happenedAt, { notFuture: true }) : null);
  // A date the server refused gets the focus, unless the title or text was refused too.
  useEffect(() => {
    if (serverDate === null || Object.keys(form.formState.errors).length > 0) return;
    formElement.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [serverDate, form]);
  const hasInvalidDate = () =>
    dateDraftError(happenedAt, { notFuture: true }) !== null ||
    photos.some((photo) => takenAtError(photo) !== null);

  const submit = form.handleSubmit((values) => {
    if (hasInvalidDate()) return;
    update.mutate(
      {
        version: base.version,
        body: {
          title: values.title.trim(),
          // A blank text empties it, allowed only while a photo remains (mvp.md §17).
          content: values.content,
          // The complete new list: kept photos keep their place, new ones follow (plan §3.2).
          photos: toPhotoInputs(photos),
          // Sent only when changed; UNKNOWN removes it (OQ-063).
          ...(dateChanged ? { happenedAt: toPartialDate(happenedAt) } : {}),
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
          setServerDate(serverDateError(failure));
          // The limit may have been lowered since the Family was loaded.
          if (failure instanceof ApiError && failure.code === 'MEMORY_PHOTO_LIMIT_REACHED') {
            void queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId), exact: true });
          }
        },
      },
    );
  });

  const reloadLatest = async () => {
    const latest = await reload();
    if (latest) {
      setBase(latest);
      setPersons(toPersons(latest));
      setPhotos(fromMemoryPhotos(latest.photos));
      setHappenedAt(fromPartialDate(latest.happenedAt));
      setServerDate(null);
      setTriedToSave(false);
      form.reset(toFormValues(latest));
      update.reset();
      reloaded.current = true;
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
        <h1 ref={heading} tabIndex={-1} className="text-display text-text outline-none">
          {t('edit.title')}
        </h1>
      </header>
      <form
        ref={formElement}
        noValidate
        onSubmit={(event) => {
          setTriedToSave(true);
          const element = event.currentTarget;
          const invalidDate = hasInvalidDate();
          void submit(event).then(() => {
            // The title and text get the focus first; otherwise the first invalid date, once shown.
            if (invalidDate && Object.keys(form.formState.errors).length === 0) {
              requestAnimationFrame(() => {
                element.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
              });
            }
          });
        }}
        className="flex flex-col gap-6"
      >
        <StoryFields form={form} contentOptional={hasReadyPhoto} emptyContent="contentOrPhoto" />
        <PartialDateField
          label={t('form.happenedAt.label')}
          dateLabel={t('form.happenedAt.date')}
          yearLabel={t('form.happenedAt.year')}
          value={happenedAt}
          error={dateError}
          notFuture
          disabled={update.isPending || isConflict}
          onChange={(patch) => {
            setServerDate(null);
            setHappenedAt((current) => ({ ...current, ...patch }));
          }}
        />
        <MemoryPhotosField
          familyId={familyId}
          limit={photoLimit}
          photos={photos}
          onChange={setPhotos}
          showErrors={triedToSave}
          disabled={update.isPending || isConflict}
        />
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
            ref={conflict}
            role="alert"
            tabIndex={-1}
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
              {memoryError
                ? t(`edit.errors.${memoryError}`)
                : photoError === 'MEMORY_PHOTO_LIMIT_REACHED'
                  ? t('edit.errors.MEMORY_PHOTO_LIMIT_REACHED', { count: photoLimit })
                  : photoError
                    ? t(`edit.errors.${photoError}`)
                    : errorMessage(i18n, update.error)}
            </p>
          )
        )}
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button
            type="submit"
            disabled={
              persons.length === 0 ||
              lacksActivePerson ||
              photosPending ||
              update.isPending ||
              isConflict
            }
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
