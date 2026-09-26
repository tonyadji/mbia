import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { BottomSheet } from '../components/BottomSheet';
import { Button } from '../components/Button';
import { IconButton } from '../components/IconButton';
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
 * The related Persons of SCREEN-006: the chosen ones, each removable, and `Add a person`, which
 * opens the Family search (SCREEN-007) over ACTIVE Persons not chosen yet. On SCREEN-014, an
 * archived Person already on the Memory is marked archived.
 */
export function RelatedPersonsPicker({
  familyId,
  persons,
  onChange,
  disabled = false,
}: {
  familyId: string;
  persons: RelatedPerson[];
  onChange: (persons: RelatedPerson[]) => void;
  disabled?: boolean;
}) {
  const { t } = useTranslation('memory');
  const [choosing, setChoosing] = useState(false);
  const legendId = useId();

  return (
    <fieldset className="flex flex-col gap-3">
      <legend id={legendId} className="text-caption font-semibold text-text">
        {t('form.persons')}
      </legend>
      {persons.length === 0 ? (
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
              </IconButton>
            </li>
          ))}
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
        <BottomSheet
          title={t('form.chooseTitle')}
          onClose={() => {
            setChoosing(false);
          }}
        >
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
          />
        </BottomSheet>
      )}
    </fieldset>
  );
}
