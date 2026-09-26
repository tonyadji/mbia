import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { i18n } from '../i18n';
import { RelatedPersonsPicker, type RelatedPerson } from '../memories/RelatedPersonsPicker';
import {
  MEMORY_ERRORS,
  showServerFieldErrors,
  StoryFields,
  type StoryFormValues,
} from '../memories/StoryFields';
import { useCreateStoryMemory } from '../memories/useCreateStoryMemory';
import { usePerson } from '../persons/usePerson';
import { familyHomePath } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { memoryPath, type MemoryPageState } from './MemoryPage';
import { displayNameOf, personPath } from './PersonProfilePage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** SCREEN-006, started from a Person (`personId`, preselected) or from Family Home. */
export function addMemoryPath(familyId: string, { personId }: { personId?: string } = {}) {
  return `/families/${familyId}/memories/new${personId ? `?person=${personId}` : ''}`;
}

/**
 * SCREEN-006 — Add Memory. Only stories exist in Phase 3, so the screen opens on the story form
 * (phase-3-family-memories.md §3.1): title, text, related Persons, `Publish` (family-tree-ux.md
 * §13). The Person the flow starts from is preselected, otherwise the User's own Person.
 */
export function AddMemoryPage() {
  const { t } = useTranslation(['memory', 'settings']);
  const { familyId = '' } = useParams();
  const [params] = useSearchParams();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });
  const fromPerson = params.get('person');
  const startPersonId = fromPerson !== null && UUID.test(fromPerson) ? fromPerson : null;
  const preselectId = startPersonId ?? family.data?.myLinkedPersonId ?? null;
  const preselect = usePerson(familyId, preselectId ?? '', { enabled: preselectId !== null });

  if (!isValidId || (family.error instanceof ApiError && family.error.status === 404)) {
    return <FamilyNotFoundPage />;
  }
  if (family.isError) {
    return <ErrorState error={family.error} onRetry={() => void family.refetch()} />;
  }

  const backPath = startPersonId ? personPath(familyId, startPersonId) : familyHomePath(familyId);
  const canWrite = family.data?.myRole === 'ADMIN' || family.data?.myRole === 'CONTRIBUTOR';
  const loading = family.isPending || (preselectId !== null && preselect.isPending);
  // An unknown or archived Person is simply not preselected.
  const initialPersons: RelatedPerson[] =
    preselect.data?.status === 'ACTIVE'
      ? [
          {
            id: preselect.data.id,
            name: displayNameOf(preselect.data),
            photoUrl: preselect.data.profilePictureUrl,
          },
        ]
      : [];

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={backPath}
          className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display text-text">{t('form.title')}</h1>
        {!family.isPending && canWrite && (
          <p className="text-body text-text-muted">{t('form.intro')}</p>
        )}
      </header>
      {loading ? (
        <div className="flex flex-col gap-4" aria-busy="true">
          <Skeleton className="h-12" />
          <Skeleton className="h-32" />
        </div>
      ) : canWrite ? (
        <StoryForm familyId={familyId} initialPersons={initialPersons} />
      ) : (
        <p role="status" className="text-body text-text">
          {t('form.readOnly')}
        </p>
      )}
    </div>
  );
}

function StoryForm({
  familyId,
  initialPersons,
}: {
  familyId: string;
  initialPersons: RelatedPerson[];
}) {
  const { t } = useTranslation('memory');
  const navigate = useNavigate();
  const createStory = useCreateStoryMemory(familyId);
  const [persons, setPersons] = useState(initialPersons);
  const [error, setError] = useState<unknown>(null);
  // Nothing typed is ever reset: after a refusal, the form keeps the title, text and Persons.
  const form = useForm<StoryFormValues>({ defaultValues: { title: '', content: '' } });
  const pending = createStory.isPending;

  const submit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const memory = await createStory.mutateAsync({
        title: values.title.trim(),
        content: values.content,
        relatedPersonIds: persons.map((person) => person.id),
      });
      // The User lands on the Memory just published (SCREEN-013).
      const state: MemoryPageState = { published: true };
      void navigate(memoryPath(familyId, memory.id), { replace: true, state });
    } catch (failure) {
      showServerFieldErrors(form, failure);
      setError(failure);
    }
  });

  const code = error instanceof ApiError ? error.code : null;
  const memoryError = MEMORY_ERRORS.find((known) => known === code);

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-6">
      <StoryFields form={form} />
      <RelatedPersonsPicker
        familyId={familyId}
        persons={persons}
        onChange={setPersons}
        disabled={pending}
      />
      {error !== null && (
        <p role="alert" className="text-body text-text">
          {memoryError ? t(`errors.${memoryError}`) : errorMessage(i18n, error)}
        </p>
      )}
      <Button type="submit" disabled={persons.length === 0 || pending}>
        {t('form.publish')}
      </Button>
    </form>
  );
}
