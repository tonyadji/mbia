import { useId, useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { BottomSheet } from '../components/BottomSheet';
import { Button } from '../components/Button';
import { IconButton } from '../components/IconButton';
import { TextField } from '../components/TextField';
import { PersonSearch } from '../persons/PersonSearch';

/** A Person related to the Memory being written. */
export interface RelatedPerson {
  id: string;
  name: string;
  photoUrl?: string | null;
  /** Already on an edited Memory while archived: it may stay, never be added again (OQ-035). */
  archived?: boolean;
}

/**
 * A Person not in the tree yet, added from the form: created when the Memory is published, just
 * before it (SCREEN-006, OQ-065). `self` is the User's own Person (`Me`).
 */
export interface NewPerson {
  key: string;
  firstName: string;
  lastName: string;
  self?: boolean;
  /** The User chose `Create anyway` over the similar Persons already in the Family. */
  confirmDuplicate?: boolean;
}

/** The first and last names the server accepts (`CreatePersonRequest`). */
export const NAME_MAX_LENGTH = 150;

export function newPersonName(person: NewPerson) {
  return [person.firstName.trim(), person.lastName.trim()].filter(Boolean).join(' ');
}

/**
 * The related Persons of SCREEN-006: the chosen ones, each removable, and `Add a person`, which
 * opens the Family search (SCREEN-007) over ACTIVE Persons not chosen yet. With `newPersons`, a
 * Person not in the tree yet can be added from the search (`Add {typed name}`, first and last name
 * only), marked as to be added. On SCREEN-014, an archived Person already on the Memory is marked
 * archived.
 */
export function RelatedPersonsPicker({
  familyId,
  persons,
  onChange,
  newPersons,
  disabled = false,
}: {
  familyId: string;
  persons: RelatedPerson[];
  onChange: (persons: RelatedPerson[]) => void;
  newPersons?: { value: NewPerson[]; onChange: (persons: NewPerson[]) => void };
  disabled?: boolean;
}) {
  const { t } = useTranslation('memory');
  const [choosing, setChoosing] = useState(false);
  // The names of the Person being added, once `Add {typed name}` is chosen.
  const [naming, setNaming] = useState<{ firstName: string; lastName: string } | null>(null);
  const legendId = useId();
  const added = newPersons?.value ?? [];

  function close() {
    setChoosing(false);
    setNaming(null);
  }

  function addNamed() {
    if (!newPersons || naming === null || naming.firstName.trim() === '') return;
    newPersons.onChange([
      ...added,
      { key: `new-${String(Date.now())}-${String(added.length)}`, ...naming },
    ]);
    close();
  }

  return (
    <fieldset className="flex flex-col gap-3">
      <legend id={legendId} className="text-caption font-semibold text-text">
        {t('form.persons')}
      </legend>
      {persons.length + added.length === 0 ? (
        <p className="text-caption text-text-muted">{t('form.personsHint')}</p>
      ) : (
        <ul aria-labelledby={legendId} className="flex flex-col gap-2">
          {persons.map((person) => (
            <li
              key={person.id}
              className="flex items-center gap-3 rounded-xl border border-border bg-surface py-1 pr-1 pl-4"
            >
              <Avatar displayName={person.name} photoUrl={person.photoUrl} />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="text-body font-semibold break-words text-text">{person.name}</span>
                {person.archived && (
                  <span className="text-caption text-text-muted">
                    {t('screen.status.ARCHIVED')}
                  </span>
                )}
              </span>
              <IconButton
                label={t('form.removePerson', { name: person.name })}
                disabled={disabled}
                className="border-transparent"
                onClick={() => {
                  onChange(persons.filter((other) => other.id !== person.id));
                }}
              >
                <CloseIcon />
              </IconButton>
            </li>
          ))}
          {added.map((person) => {
            const name = newPersonName(person);
            return (
              <li
                key={person.key}
                className="flex items-center gap-3 rounded-xl border border-border bg-surface py-1 pr-1 pl-4"
              >
                <Avatar displayName={name} />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="text-body font-semibold break-words text-text">{name}</span>
                  <span className="text-caption text-text-muted">{t('form.newPerson')}</span>
                </span>
                <IconButton
                  label={t('form.removePerson', { name })}
                  disabled={disabled}
                  className="border-transparent"
                  onClick={() => {
                    newPersons?.onChange(added.filter((other) => other.key !== person.key));
                  }}
                >
                  <CloseIcon />
                </IconButton>
              </li>
            );
          })}
        </ul>
      )}
      <Button
        variant="secondary"
        disabled={disabled}
        className="sm:w-auto sm:self-start"
        onClick={() => {
          setChoosing(true);
        }}
      >
        {t('form.addPerson')}
      </Button>
      {choosing && (
        <BottomSheet title={naming ? t('form.newTitle') : t('form.chooseTitle')} onClose={close}>
          {naming ? (
            <NewPersonFields
              value={naming}
              onChange={setNaming}
              onAdd={addNamed}
              onBack={() => {
                setNaming(null);
              }}
            />
          ) : (
            <PersonSearch
              familyId={familyId}
              label={t('form.searchLabel')}
              excludeIds={persons.map((person) => person.id)}
              excludedMessage={t('form.allChosen')}
              autoFocus
              onSelect={(person) => {
                onChange([
                  ...persons,
                  {
                    id: person.id,
                    name: person.displayName ?? person.firstName,
                    photoUrl: person.profilePictureUrl,
                  },
                ]);
                setChoosing(false);
              }}
              addNew={
                newPersons && {
                  label: (text) => t('form.addNew', { name: text }),
                  onAdd: (text) => {
                    const [firstName = '', ...rest] = text.split(/\s+/);
                    setNaming({
                      firstName: firstName.slice(0, NAME_MAX_LENGTH),
                      lastName: rest.join(' ').slice(0, NAME_MAX_LENGTH),
                    });
                  },
                }
              }
            />
          )}
        </BottomSheet>
      )}
    </fieldset>
  );
}

/**
 * The first and last names of a Person added on the way; Enter adds them without publishing the
 * Memory around them.
 */
function NewPersonFields({
  value,
  onChange,
  onAdd,
  onBack,
}: {
  value: { firstName: string; lastName: string };
  onChange: (value: { firstName: string; lastName: string }) => void;
  onAdd: () => void;
  onBack: () => void;
}) {
  const { t } = useTranslation(['memory', 'person']);
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    onAdd();
  };
  return (
    <div className="flex flex-col gap-4">
      <TextField
        label={t('person:form.firstName')}
        required
        autoFocus
        autoComplete="off"
        maxLength={NAME_MAX_LENGTH}
        value={value.firstName}
        onKeyDown={onKeyDown}
        onChange={(event) => {
          onChange({ ...value, firstName: event.target.value });
        }}
      />
      <TextField
        label={t('person:form.lastName')}
        autoComplete="off"
        maxLength={NAME_MAX_LENGTH}
        value={value.lastName}
        onKeyDown={onKeyDown}
        onChange={(event) => {
          onChange({ ...value, lastName: event.target.value });
        }}
      />
      <div className="flex flex-col gap-3 sm:flex-row">
        <Button variant="secondary" onClick={onBack}>
          {t('memory:form.backToSearch')}
        </Button>
        <Button disabled={value.firstName.trim() === ''} onClick={onAdd}>
          {t('memory:form.addThisPerson')}
        </Button>
      </div>
    </div>
  );
}

function CloseIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    >
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  );
}
