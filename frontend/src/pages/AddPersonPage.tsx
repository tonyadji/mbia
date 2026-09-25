import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { i18n } from '../i18n';
import {
  BiographyField,
  EMPTY_PERSON_FORM,
  LifeFields,
  NameFields,
  OtherIdentityFields,
  hasOptionalFieldError,
  toPersonFields,
  type PersonFormValues,
} from '../persons/PersonFormFields';
import { useCreatePerson, type CreatePersonRequest } from '../persons/useCreatePerson';
import { familyHomePath, type FamilyHomeState } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** "Start with me" (`mode=me`) or a standalone Person (SCREEN-004). */
export function addPersonPath(familyId: string, { startWithMe = false } = {}) {
  return `/families/${familyId}/persons/new${startWithMe ? '?mode=me' : ''}`;
}

/**
 * SCREEN-004 — Add a Person, in "Start with me" or standalone mode. Only first and last names are
 * shown first; other details are behind "More information" (family-tree-ux.md §5). No photo in
 * Phase 2.
 */
export function AddPersonPage() {
  const { familyId = '' } = useParams();
  const startWithMe = useSearchParams()[0].get('mode') === 'me';
  if (!UUID.test(familyId)) {
    return <FamilyNotFoundPage />;
  }
  return <AddPersonForm familyId={familyId} startWithMe={startWithMe} />;
}

function AddPersonForm({ familyId, startWithMe }: { familyId: string; startWithMe: boolean }) {
  const { t } = useTranslation(['person', 'settings']);
  const navigate = useNavigate();
  const create = useCreatePerson(familyId);
  const [showMore, setShowMore] = useState(false);
  const form = useForm<PersonFormValues>({ defaultValues: EMPTY_PERSON_FORM });

  const submit = form.handleSubmit(
    (values) => {
      create.mutate(toRequest(values, startWithMe), {
        onSuccess: (person) => {
          const state: FamilyHomeState = {
            personAdded: {
              id: person.id,
              name: person.displayName ?? person.firstName,
              self: startWithMe,
            },
          };
          void navigate(familyHomePath(familyId), { replace: true, state });
        },
      });
    },
    (invalid) => {
      if (hasOptionalFieldError(invalid)) setShowMore(true);
    },
  );

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={familyHomePath(familyId)}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display text-text">
          {startWithMe ? t('person:form.titleMe') : t('person:form.title')}
        </h1>
        <p className="text-body text-text-muted">
          {startWithMe ? t('person:form.introMe') : t('person:form.intro')}
        </p>
      </header>
      <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
        <NameFields form={form} autoComplete={startWithMe} />

        <button
          type="button"
          aria-expanded={showMore}
          aria-controls="person-more"
          onClick={() => {
            setShowMore((open) => !open);
          }}
          className="flex min-h-12 items-center justify-between rounded-xl text-body font-semibold text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          {t('person:form.more')}
          <ChevronIcon open={showMore} />
        </button>
        <div id="person-more" hidden={!showMore} className="flex flex-col gap-4">
          <OtherIdentityFields form={form} />
          <LifeFields form={form} />
          <BiographyField form={form} />
        </div>

        {create.isError && <p role="alert">{errorMessage(i18n, create.error)}</p>}
        <Button type="submit" disabled={create.isPending}>
          {startWithMe ? t('person:form.submitMe') : t('person:form.submit')}
        </Button>
      </form>
    </div>
  );
}

/** A blank optional value is left out of the creation. */
function toRequest(values: PersonFormValues, startWithMe: boolean): CreatePersonRequest {
  const fields = toPersonFields(values);
  return {
    ...fields,
    lastName: fields.lastName || undefined,
    middleNames: fields.middleNames || undefined,
    preferredName: fields.preferredName || undefined,
    biography: fields.biography || undefined,
    linkToCurrentUser: startWithMe,
    confirmPossibleDuplicate: false,
  };
}

function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={`size-5 transition-transform ${open ? 'rotate-180' : ''}`}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}
