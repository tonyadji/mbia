import { useTranslation } from 'react-i18next';
import type { components } from '../api/generated/schema';
import { Select } from '../components/Select';
import { TextField } from '../components/TextField';
import {
  partialDate,
  requiredDate,
  validYear,
  YEAR_MAX,
  YEAR_MIN,
} from '../persons/PersonFormFields';

type Precision = components['schemas']['DatePrecision'];
type PartialDate = components['schemas']['PartialDate'];

/** A partial date as a form edits it: its precision, then the date or the year typed. */
export interface DateDraft {
  precision: Precision;
  date: string;
  year: string;
}

/** Why a date cannot be sent; `invalid` is a refusal of the server without a known reason. */
export type DateError = 'dateRequired' | 'invalidYear' | 'futureDate' | 'invalid';

export const EMPTY_DATE: DateDraft = { precision: 'UNKNOWN', date: '', year: '' };

export function fromPartialDate(value: PartialDate | null | undefined): DateDraft {
  return {
    precision: value?.precision ?? 'UNKNOWN',
    date: value?.date ?? '',
    year: value?.year?.toString() ?? '',
  };
}

export function toPartialDate(draft: DateDraft): PartialDate {
  return partialDate(draft.precision, draft.date, draft.year.trim());
}

/** Whether two drafts send the same date. */
export function sameDate(a: DateDraft, b: DateDraft) {
  const [left, right] = [toPartialDate(a), toPartialDate(b)];
  return left.precision === right.precision && left.date === right.date && left.year === right.year;
}

/** The User's current day, `YYYY-MM-DD`, as a date field writes it. */
export function today() {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${String(now.getFullYear()).padStart(4, '0')}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Why a date cannot be sent, like a birth date (OQ-033); with `notFuture`, a day after today or a
 * year after this one is refused too, as the date of a Memory (OQ-063).
 */
export function dateDraftError(draft: DateDraft, { notFuture }: { notFuture: boolean }) {
  if (draft.precision === 'EXACT') {
    const valid = requiredDate(draft.date);
    if (valid !== true) return valid;
    return notFuture && draft.date > today() ? 'futureDate' : null;
  }
  if (draft.precision === 'YEAR_ONLY') {
    const valid = validYear(draft.year);
    if (valid !== true) return valid;
    return notFuture && Number(draft.year) > new Date().getFullYear() ? 'futureDate' : null;
  }
  return null;
}

/**
 * A partial date: unknown, year only or exact date, like a birth date (SCREEN-006). Shared by the
 * date of a Memory and the taken date of its photos.
 */
export function PartialDateField({
  label,
  dateLabel,
  yearLabel,
  value,
  onChange,
  error,
  notFuture = false,
  disabled = false,
}: {
  label: string;
  dateLabel: string;
  yearLabel: string;
  value: DateDraft;
  onChange: (patch: Partial<DateDraft>) => void;
  error: DateError | null;
  /** Offers no day after today in the date picker. */
  notFuture?: boolean;
  disabled?: boolean;
}) {
  const { t } = useTranslation(['memory', 'person']);
  const message =
    error === 'invalidYear'
      ? t('person:form.invalidYear', { min: YEAR_MIN, max: YEAR_MAX })
      : error === 'dateRequired'
        ? t('person:form.dateRequired')
        : error === 'futureDate'
          ? t('memory:form.happenedAt.future')
          : error === 'invalid'
            ? t('memory:form.invalid')
            : undefined;
  const precisionOptions = (['UNKNOWN', 'YEAR_ONLY', 'EXACT'] as const).map((precision) => ({
    value: precision,
    label: t(`person:form.precision.${precision}`),
  }));

  return (
    <div className="flex flex-col gap-4">
      <Select
        label={label}
        options={precisionOptions}
        value={value.precision}
        disabled={disabled}
        onChange={(event) => {
          onChange({ precision: event.target.value as Precision });
        }}
      />
      {value.precision === 'EXACT' && (
        <TextField
          type="date"
          label={dateLabel}
          max={notFuture ? today() : undefined}
          value={value.date}
          error={message}
          disabled={disabled}
          onChange={(event) => {
            onChange({ date: event.target.value });
          }}
        />
      )}
      {value.precision === 'YEAR_ONLY' && (
        <TextField
          inputMode="numeric"
          label={yearLabel}
          value={value.year}
          error={message}
          disabled={disabled}
          onChange={(event) => {
            onChange({ year: event.target.value });
          }}
        />
      )}
    </div>
  );
}
