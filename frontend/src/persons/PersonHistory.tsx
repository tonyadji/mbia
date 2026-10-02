import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Button } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { formatDate } from '../i18n/formatDate';
import { DEFAULT_LANGUAGE, isSupportedLanguage, type Language } from '../i18n/language';
import { formatPartialDate } from './formatPartialDate';
import { usePersonHistory, type PersonHistoryEntry } from './usePersonHistory';

const ACTIONS = [
  'PERSON_CREATED',
  'PERSON_CLAIMED',
  'PERSON_UNCLAIMED',
  'PERSON_ARCHIVED',
  'PERSON_RESTORED',
  'PERSONS_MERGED',
] as const;

const FIELDS = [
  'firstName',
  'middleNames',
  'lastName',
  'preferredName',
  'gender',
  'birth',
  'isDeceased',
  'death',
  'biography',
] as const;

type Field = (typeof FIELDS)[number];

/**
 * SCREEN-005 History section: presentation-safe recent changes of the Person, collapsed by default
 * and loaded when opened (data-model.md §18, OQ-031). Never shows action codes nor raw values.
 */
export function PersonHistory({ familyId, personId }: { familyId: string; personId: string }) {
  const { t } = useTranslation('person');
  const [open, setOpen] = useState(false);
  const history = usePersonHistory(familyId, personId, { enabled: open });
  const entries = history.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <section aria-labelledby="profile-history" className="flex flex-col gap-4">
      <details
        className="flex flex-col gap-4 rounded-xl border border-border bg-surface px-4 py-3"
        onToggle={(event) => {
          setOpen(event.currentTarget.open);
        }}
      >
        <summary className="min-h-12 cursor-pointer content-center">
          <h2 id="profile-history" className="inline text-section text-text">
            {t('history.title')}
          </h2>
        </summary>
        {history.isError ? (
          <ErrorState error={history.error} onRetry={() => void history.refetch()} />
        ) : history.isPending ? (
          <div className="mt-2 flex flex-col gap-3" aria-busy="true">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : entries.length === 0 ? (
          <p className="mt-2 text-body text-text-muted">{t('history.empty')}</p>
        ) : (
          <>
            <ul aria-labelledby="profile-history" className="mt-2 flex flex-col gap-4">
              {entries.map((entry) => (
                <li key={entry.id}>
                  <HistoryEntry entry={entry} />
                </li>
              ))}
            </ul>
            {history.hasNextPage && (
              <Button
                variant="secondary"
                disabled={history.isFetchingNextPage}
                onClick={() => void history.fetchNextPage()}
              >
                {t('history.more')}
              </Button>
            )}
          </>
        )}
      </details>
    </section>
  );
}

function HistoryEntry({ entry }: { entry: PersonHistoryEntry }) {
  const { t, i18n } = useTranslation('person');
  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  const actor = entry.actor.deleted
    ? t('history.formerMember')
    : (entry.actor.displayName ?? t('history.member'));

  return (
    <div className="flex flex-col">
      <p className="text-body break-words text-text">{describe(entry, t, language)}</p>
      <p className="text-caption text-text-muted">
        {t('history.byOn', { date: formatDate(new Date(entry.occurredAt), language), actor })}
      </p>
    </div>
  );
}

/** One human sentence per entry; an unknown action or field reads as a generic change. */
function describe(entry: PersonHistoryEntry, t: TFunction<'person'>, language: Language) {
  const action = ACTIONS.find((known) => known === entry.action);
  if (action) return t(`history.actions.${action}`);
  const field = FIELDS.find((known) => known === entry.field);
  if (entry.action !== 'PERSON_UPDATED' || !field) return t('history.actions.PERSON_UPDATED');
  if (field === 'biography') return t('history.biographyChanged');
  return t('history.fieldChanged', {
    field: t(`history.fields.${field}`),
    from: formatValue(field, entry.oldValue, t, language),
    to: formatValue(field, entry.newValue, t, language),
  });
}

function formatValue(field: Field, value: unknown, t: TFunction<'person'>, language: Language) {
  if (value == null || value === '') return t('history.noValue');
  if (field === 'isDeceased') return value === true ? t('history.yes') : t('history.no');
  if (typeof value !== 'string') return t('history.noValue');
  if (field === 'gender') {
    return value === 'MALE' || value === 'FEMALE' || value === 'OTHER' || value === 'UNKNOWN'
      ? t(`form.genders.${value}`)
      : t('history.noValue');
  }
  if (field === 'birth' || field === 'death') {
    // The audit writes a partial date as `YYYY-MM-DD`, `YYYY` or `UNKNOWN`.
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      return formatPartialDate({ precision: 'EXACT', date: value }, language) ?? value;
    }
    if (/^\d{1,4}$/.test(value)) return value;
    return t('form.precision.UNKNOWN');
  }
  return value;
}
