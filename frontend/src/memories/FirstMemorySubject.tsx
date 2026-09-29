import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { TextField } from '../components/TextField';
import { NAME_MAX_LENGTH, type NewPerson, type RelatedPerson } from './RelatedPersonsPicker';

/**
 * "Who is this memory about?" (SCREEN-006, OQ-065): the first question of a first memory, while the
 * Family has no Person. `Me` asks the User's own names, as SCREEN-004 "Start with me"; `Someone
 * else` a first name and a last name. The Person is created when the Memory is published; once
 * created (the Memory then refused), it is shown and no longer changes.
 */
export function FirstMemorySubject({
  value,
  created,
  onChange,
  showErrors,
  disabled,
}: {
  value: NewPerson | null;
  created: RelatedPerson | null;
  onChange: (value: NewPerson) => void;
  showErrors: boolean;
  disabled: boolean;
}) {
  const { t } = useTranslation(['memory', 'person']);
  const legendId = useId();

  if (created !== null) {
    return (
      <section aria-labelledby={legendId} className="flex flex-col gap-3">
        <h2 id={legendId} className="text-section text-text">
          {t('memory:form.subject.question')}
        </h2>
        <div className="flex items-center gap-3 rounded-xl border border-border bg-surface px-4 py-3">
          <Avatar displayName={created.name} />
          <p className="min-w-0 text-body break-words text-text">
            {t('memory:form.subject.added', { name: created.name })}
          </p>
        </div>
      </section>
    );
  }

  const choose = (self: boolean) => {
    onChange({ key: 'subject', firstName: '', lastName: '', ...value, self });
  };
  const self = value?.self === true;
  const missingFirstName = showErrors && value !== null && value.firstName.trim() === '';

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-3 text-section text-text">{t('memory:form.subject.question')}</legend>
      <div className="grid grid-cols-2 gap-3">
        <SubjectChoice
          label={t('memory:form.subject.me')}
          pressed={value !== null && self}
          disabled={disabled}
          onClick={() => {
            choose(true);
          }}
        />
        <SubjectChoice
          label={t('memory:form.subject.someoneElse')}
          pressed={value !== null && !self}
          disabled={disabled}
          onClick={() => {
            choose(false);
          }}
        />
      </div>
      {value === null ? (
        <p className="text-caption text-text-muted">{t('memory:form.subject.choose')}</p>
      ) : (
        <>
          {self && (
            <p className="text-caption text-text-muted">{t('memory:form.subject.meHint')}</p>
          )}
          <TextField
            label={t('person:form.firstName')}
            required
            autoComplete={self ? 'given-name' : 'off'}
            maxLength={NAME_MAX_LENGTH}
            disabled={disabled}
            value={value.firstName}
            error={missingFirstName ? t('memory:form.required') : undefined}
            onChange={(event) => {
              onChange({ ...value, firstName: event.target.value });
            }}
          />
          <TextField
            label={t('person:form.lastName')}
            autoComplete={self ? 'family-name' : 'off'}
            maxLength={NAME_MAX_LENGTH}
            disabled={disabled}
            value={value.lastName}
            onChange={(event) => {
              onChange({ ...value, lastName: event.target.value });
            }}
          />
        </>
      )}
    </fieldset>
  );
}

/** One of the two answers, pressed when chosen: marked by its border and weight, not only color. */
function SubjectChoice({
  label,
  pressed,
  disabled,
  onClick,
}: {
  label: string;
  pressed: boolean;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      disabled={disabled}
      onClick={onClick}
      className={`min-h-12 rounded-xl border bg-surface px-4 text-body text-text transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-60 ${pressed ? 'border-2 border-primary font-semibold' : 'border-border hover:border-primary'}`}
    >
      {label}
    </button>
  );
}
