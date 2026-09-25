import { useWatch, type FieldErrors, type UseFormReturn } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { components } from '../api/generated/schema';
import { Checkbox } from '../components/Checkbox';
import { Select } from '../components/Select';
import { TextArea } from '../components/TextArea';
import { TextField } from '../components/TextField';

/** Length limits of `CreatePersonRequest` and `UpdatePersonRequest` in openapi.yaml. */
const PERSON_LIMITS = {
  firstName: 150,
  lastName: 150,
  middleNames: 250,
  preferredName: 150,
  biography: 10000,
} as const;

const YEAR_MIN = 1;
const YEAR_MAX = 9999;

type Gender = components['schemas']['Gender'];
type Precision = components['schemas']['DatePrecision'];
type PartialDate = components['schemas']['PartialDate'];
type Person = components['schemas']['PersonResponse'];

/** The Person form of SCREEN-004 (Add) and SCREEN-012 (Edit). */
export interface PersonFormValues {
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

export type PersonForm = UseFormReturn<PersonFormValues>;

/** Validation errors are stored as keys, so that their message follows a language change. */
type FieldErrorKey = 'required' | 'tooLong' | 'dateRequired' | 'invalidYear';

/** Fields that are not in the first rows of the Add form. */
const OPTIONAL_FIELDS = [
  'middleNames',
  'preferredName',
  'birthDate',
  'birthYear',
  'deathDate',
  'deathYear',
  'biography',
] as const;

export const EMPTY_PERSON_FORM: PersonFormValues = {
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
};

/** The form filled with a Person as the server returned it. */
export function toFormValues(person: Person): PersonFormValues {
  return {
    firstName: person.firstName,
    lastName: person.lastName ?? '',
    middleNames: person.middleNames ?? '',
    preferredName: person.preferredName ?? '',
    gender: person.gender,
    birthPrecision: person.birth.precision,
    birthDate: person.birth.date ?? '',
    birthYear: person.birth.year?.toString() ?? '',
    isDeceased: person.isDeceased,
    deathPrecision: person.death.precision,
    deathDate: person.death.date ?? '',
    deathYear: person.death.year?.toString() ?? '',
    biography: person.biography ?? '',
  };
}

function partialDate(precision: Precision, date: string, year: string): PartialDate {
  if (precision === 'EXACT') return { precision, date };
  if (precision === 'YEAR_ONLY') return { precision, year: Number(year) };
  return { precision };
}

/**
 * Every field of the form, trimmed. A blank optional text stays `''`: `updatePerson` clears the
 * value with it (OQ-008); the Add form leaves it out.
 */
export function toPersonFields(values: PersonFormValues) {
  return {
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    middleNames: values.middleNames.trim(),
    preferredName: values.preferredName.trim(),
    gender: values.gender,
    birth: partialDate(values.birthPrecision, values.birthDate, values.birthYear),
    isDeceased: values.isDeceased,
    death: values.isDeceased
      ? partialDate(values.deathPrecision, values.deathDate, values.deathYear)
      : { precision: 'UNKNOWN' as const },
    biography: values.biography.trim(),
  };
}

/** `onInvalid` of `handleSubmit`: whether an invalid field is one of {@link OPTIONAL_FIELDS}. */
export function hasOptionalFieldError(invalid: FieldErrors<PersonFormValues>) {
  return OPTIONAL_FIELDS.some((field) => invalid[field]);
}

const maxLength = (max: number) => (value: string) =>
  value.trim().length <= max || ('tooLong' satisfies FieldErrorKey);

const validYear = (value: string) =>
  (/^\d{1,4}$/.test(value.trim()) && Number(value) >= YEAR_MIN && Number(value) <= YEAR_MAX) ||
  ('invalidYear' satisfies FieldErrorKey);

const requiredDate = (value: string) => value !== '' || ('dateRequired' satisfies FieldErrorKey);

function useFieldMessage(form: PersonForm) {
  const { t } = useTranslation('person');
  const { errors } = form.formState;
  return (field: keyof PersonFormValues, max?: number) => {
    const key = errors[field]?.message as FieldErrorKey | undefined;
    if (key === undefined) return undefined;
    if (key === 'tooLong') return t('form.tooLong', { max });
    if (key === 'invalidYear') return t('form.invalidYear', { min: YEAR_MIN, max: YEAR_MAX });
    return t(`form.${key}`);
  };
}

/** First name (required) and last name. */
export function NameFields({ form, autoComplete }: { form: PersonForm; autoComplete: boolean }) {
  const { t } = useTranslation('person');
  const message = useFieldMessage(form);
  return (
    <>
      <TextField
        label={t('form.firstName')}
        autoComplete={autoComplete ? 'given-name' : 'off'}
        required
        error={message('firstName', PERSON_LIMITS.firstName)}
        {...form.register('firstName', {
          validate: (value) =>
            value.trim() === ''
              ? ('required' satisfies FieldErrorKey)
              : maxLength(PERSON_LIMITS.firstName)(value),
        })}
      />
      <TextField
        label={t('form.lastName')}
        autoComplete={autoComplete ? 'family-name' : 'off'}
        error={message('lastName', PERSON_LIMITS.lastName)}
        {...form.register('lastName', { validate: maxLength(PERSON_LIMITS.lastName) })}
      />
    </>
  );
}

/** Middle names, preferred name and gender. */
export function OtherIdentityFields({ form }: { form: PersonForm }) {
  const { t } = useTranslation('person');
  const message = useFieldMessage(form);
  return (
    <>
      <TextField
        label={t('form.middleNames')}
        autoComplete="off"
        error={message('middleNames', PERSON_LIMITS.middleNames)}
        {...form.register('middleNames', { validate: maxLength(PERSON_LIMITS.middleNames) })}
      />
      <TextField
        label={t('form.preferredName')}
        autoComplete="off"
        error={message('preferredName', PERSON_LIMITS.preferredName)}
        {...form.register('preferredName', { validate: maxLength(PERSON_LIMITS.preferredName) })}
      />
      <Select
        label={t('form.gender')}
        options={(['UNKNOWN', 'FEMALE', 'MALE', 'OTHER'] as const).map((value) => ({
          value,
          label: t(`form.genders.${value}`),
        }))}
        {...form.register('gender')}
      />
    </>
  );
}

/** Birth and death, each with an exact, year-only or unknown date (mvp.md §6). */
export function LifeFields({ form }: { form: PersonForm }) {
  const { t } = useTranslation('person');
  const message = useFieldMessage(form);
  const { register, control } = form;
  const [birthPrecision, isDeceased, deathPrecision] = useWatch({
    control,
    name: ['birthPrecision', 'isDeceased', 'deathPrecision'],
  });
  const precisionOptions = (['UNKNOWN', 'YEAR_ONLY', 'EXACT'] as const).map((value) => ({
    value,
    label: t(`form.precision.${value}`),
  }));

  return (
    <>
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-2 text-body font-semibold text-text">{t('form.birth')}</legend>
        <Select
          label={t('form.birthPrecision')}
          options={precisionOptions}
          {...register('birthPrecision')}
        />
        {birthPrecision === 'EXACT' && (
          <TextField
            type="date"
            label={t('form.birthDate')}
            error={message('birthDate')}
            {...register('birthDate', { validate: requiredDate })}
          />
        )}
        {birthPrecision === 'YEAR_ONLY' && (
          <TextField
            inputMode="numeric"
            label={t('form.birthYear')}
            error={message('birthYear')}
            {...register('birthYear', { validate: validYear })}
          />
        )}
      </fieldset>

      <fieldset className="flex flex-col gap-4">
        <legend className="mb-2 text-body font-semibold text-text">{t('form.death')}</legend>
        <Checkbox label={t('form.isDeceased')} {...register('isDeceased')} />
        {isDeceased && (
          <>
            <Select
              label={t('form.deathPrecision')}
              options={precisionOptions}
              {...register('deathPrecision')}
            />
            {deathPrecision === 'EXACT' && (
              <TextField
                type="date"
                label={t('form.deathDate')}
                error={message('deathDate')}
                {...register('deathDate', { validate: requiredDate })}
              />
            )}
            {deathPrecision === 'YEAR_ONLY' && (
              <TextField
                inputMode="numeric"
                label={t('form.deathYear')}
                error={message('deathYear')}
                {...register('deathYear', { validate: validYear })}
              />
            )}
          </>
        )}
      </fieldset>
    </>
  );
}

export function BiographyField({ form }: { form: PersonForm }) {
  const { t } = useTranslation('person');
  const message = useFieldMessage(form);
  return (
    <TextArea
      label={t('form.biography')}
      error={message('biography', PERSON_LIMITS.biography)}
      {...form.register('biography', { validate: maxLength(PERSON_LIMITS.biography) })}
    />
  );
}
