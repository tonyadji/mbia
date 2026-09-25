import { useState } from 'react';
import { useForm, useWatch, type FieldErrors } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { errorMessage } from '../api/errorMessage';
import type { components } from '../api/generated/schema';
import { Button } from '../components/Button';
import { Checkbox } from '../components/Checkbox';
import { Select } from '../components/Select';
import { TextArea } from '../components/TextArea';
import { TextField } from '../components/TextField';
import { i18n } from '../i18n';
import { useCreatePerson, type CreatePersonRequest } from '../persons/useCreatePerson';
import { familyHomePath, type FamilyHomeState } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Length limits of `CreatePersonRequest` in openapi.yaml. */
export const PERSON_LIMITS = {
  firstName: 150,
  lastName: 150,
  middleNames: 250,
  preferredName: 150,
  biography: 10000,
} as const;

const YEAR_MIN = 1;
const YEAR_MAX = 9999;

/** "Start with me" (`mode=me`) or a standalone Person (SCREEN-004). */
export function addPersonPath(familyId: string, { startWithMe = false } = {}) {
  return `/families/${familyId}/persons/new${startWithMe ? '?mode=me' : ''}`;
}

type Gender = components['schemas']['Gender'];
type Precision = components['schemas']['DatePrecision'];

interface PersonForm {
  firstName: string;
  lastName: string;
  middleNames: string;
  preferredName: string;
  gender: Gender;
  birthPrecision: Precision;
  birthDate: string;
  birthYear: string;
  isDeceased: boolean;
  deathPrecision: Precision;
  deathDate: string;
  deathYear: string;
  biography: string;
}

/** Validation errors are stored as keys, so that their message follows a language change. */
type FieldErrorKey = 'required' | 'tooLong' | 'dateRequired' | 'invalidYear';

const OPTIONAL_FIELDS = [
  'middleNames',
  'preferredName',
  'birthDate',
  'birthYear',
  'deathDate',
  'deathYear',
  'biography',
] as const;

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
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<PersonForm>({
    defaultValues: {
      firstName: '',
      lastName: '',
      middleNames: '',
      preferredName: '',
      gender: 'UNKNOWN',
      birthPrecision: 'UNKNOWN',
      birthDate: '',
      birthYear: '',
      isDeceased: false,
      deathPrecision: 'UNKNOWN',
      deathDate: '',
      deathYear: '',
      biography: '',
    },
  });

  const [birthPrecision, isDeceased, deathPrecision] = useWatch({
    control,
    name: ['birthPrecision', 'isDeceased', 'deathPrecision'],
  });

  const maxLength = (max: number) => (value: string) =>
    value.trim().length <= max || ('tooLong' satisfies FieldErrorKey);
  const validYear = (value: string) =>
    (/^\d{1,4}$/.test(value.trim()) && Number(value) >= YEAR_MIN && Number(value) <= YEAR_MAX) ||
    ('invalidYear' satisfies FieldErrorKey);

  const onInvalid = (invalid: FieldErrors<PersonForm>) => {
    if (OPTIONAL_FIELDS.some((field) => invalid[field])) setShowMore(true);
  };

  const submit = handleSubmit((values) => {
    create.mutate(toRequest(values, startWithMe), {
      onSuccess: (person) => {
        const state: FamilyHomeState = {
          personAdded: { name: person.displayName ?? person.firstName, self: startWithMe },
        };
        void navigate(familyHomePath(familyId), { replace: true, state });
      },
    });
  }, onInvalid);

  const message = (field: keyof PersonForm, max?: number) => {
    const key = errors[field]?.message as FieldErrorKey | undefined;
    if (key === undefined) return undefined;
    if (key === 'tooLong') return t('person:form.tooLong', { max });
    if (key === 'invalidYear')
      return t('person:form.invalidYear', { min: YEAR_MIN, max: YEAR_MAX });
    return t(`person:form.${key}`);
  };

  const precisionOptions = (['UNKNOWN', 'YEAR_ONLY', 'EXACT'] as const).map((value) => ({
    value,
    label: t(`person:form.precision.${value}`),
  }));

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
        <TextField
          label={t('person:form.firstName')}
          autoComplete={startWithMe ? 'given-name' : 'off'}
          required
          error={message('firstName', PERSON_LIMITS.firstName)}
          {...register('firstName', {
            validate: (value) =>
              value.trim() === ''
                ? ('required' satisfies FieldErrorKey)
                : maxLength(PERSON_LIMITS.firstName)(value),
          })}
        />
        <TextField
          label={t('person:form.lastName')}
          autoComplete={startWithMe ? 'family-name' : 'off'}
          error={message('lastName', PERSON_LIMITS.lastName)}
          {...register('lastName', { validate: maxLength(PERSON_LIMITS.lastName) })}
        />

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
          <TextField
            label={t('person:form.middleNames')}
            autoComplete="off"
            error={message('middleNames', PERSON_LIMITS.middleNames)}
            {...register('middleNames', { validate: maxLength(PERSON_LIMITS.middleNames) })}
          />
          <TextField
            label={t('person:form.preferredName')}
            autoComplete="off"
            error={message('preferredName', PERSON_LIMITS.preferredName)}
            {...register('preferredName', { validate: maxLength(PERSON_LIMITS.preferredName) })}
          />
          <Select
            label={t('person:form.gender')}
            options={(['UNKNOWN', 'FEMALE', 'MALE', 'OTHER'] as const).map((value) => ({
              value,
              label: t(`person:form.genders.${value}`),
            }))}
            {...register('gender')}
          />

          <fieldset className="flex flex-col gap-4">
            <legend className="mb-2 text-body font-semibold text-text">
              {t('person:form.birth')}
            </legend>
            <Select
              label={t('person:form.birthPrecision')}
              options={precisionOptions}
              {...register('birthPrecision')}
            />
            {birthPrecision === 'EXACT' && (
              <TextField
                type="date"
                label={t('person:form.birthDate')}
                error={message('birthDate')}
                {...register('birthDate', {
                  validate: (value) => value !== '' || ('dateRequired' satisfies FieldErrorKey),
                })}
              />
            )}
            {birthPrecision === 'YEAR_ONLY' && (
              <TextField
                inputMode="numeric"
                label={t('person:form.birthYear')}
                error={message('birthYear')}
                {...register('birthYear', { validate: validYear })}
              />
            )}
          </fieldset>

          <fieldset className="flex flex-col gap-4">
            <legend className="mb-2 text-body font-semibold text-text">
              {t('person:form.death')}
            </legend>
            <Checkbox label={t('person:form.isDeceased')} {...register('isDeceased')} />
            {isDeceased && (
              <>
                <Select
                  label={t('person:form.deathPrecision')}
                  options={precisionOptions}
                  {...register('deathPrecision')}
                />
                {deathPrecision === 'EXACT' && (
                  <TextField
                    type="date"
                    label={t('person:form.deathDate')}
                    error={message('deathDate')}
                    {...register('deathDate', {
                      validate: (value) => value !== '' || ('dateRequired' satisfies FieldErrorKey),
                    })}
                  />
                )}
                {deathPrecision === 'YEAR_ONLY' && (
                  <TextField
                    inputMode="numeric"
                    label={t('person:form.deathYear')}
                    error={message('deathYear')}
                    {...register('deathYear', { validate: validYear })}
                  />
                )}
              </>
            )}
          </fieldset>

          <TextArea
            label={t('person:form.biography')}
            error={message('biography', PERSON_LIMITS.biography)}
            {...register('biography', { validate: maxLength(PERSON_LIMITS.biography) })}
          />
        </div>

        {create.isError && <p role="alert">{errorMessage(i18n, create.error)}</p>}
        <Button type="submit" disabled={create.isPending}>
          {startWithMe ? t('person:form.submitMe') : t('person:form.submit')}
        </Button>
      </form>
    </div>
  );
}

function optional(value: string) {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

function partialDate(precision: Precision, date: string, year: string) {
  if (precision === 'EXACT') return { precision, date };
  if (precision === 'YEAR_ONLY') return { precision, year: Number(year) };
  return { precision };
}

function toRequest(values: PersonForm, startWithMe: boolean): CreatePersonRequest {
  return {
    firstName: values.firstName.trim(),
    lastName: optional(values.lastName),
    middleNames: optional(values.middleNames),
    preferredName: optional(values.preferredName),
    gender: values.gender,
    birth: partialDate(values.birthPrecision, values.birthDate, values.birthYear),
    isDeceased: values.isDeceased,
    death: values.isDeceased
      ? partialDate(values.deathPrecision, values.deathDate, values.deathYear)
      : { precision: 'UNKNOWN' },
    biography: optional(values.biography),
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
