import type { UseFormReturn } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { TextArea } from '../components/TextArea';
import { TextField } from '../components/TextField';
import type { DateError } from './PartialDateField';

/** `CreateStoryMemoryRequest` / `UpdateMemoryRequest` limits (mvp.md §17, data-model.md §14). */
const LIMITS = { title: 250, content: 50_000 } as const;

/** Validation errors are stored as keys, so that their message follows a language change. */
type FieldErrorKey = 'required' | 'contentOrPhoto' | 'tooLong' | 'invalid';

/** Codes explained in the words of a Memory; others use the shared `errors` messages. */
export const MEMORY_ERRORS = ['PERSON_NOT_ACTIVE', 'PERSON_NOT_FOUND'] as const;

/** Refusals of a Memory's photos, explained in the words of a Memory. */
export const PHOTO_ERRORS = [
  'MEMORY_PHOTO_LIMIT_REACHED',
  'MEDIA_NOT_READY',
  'MEDIA_ALREADY_USED',
  'MEDIA_NOT_FOUND',
] as const;

export interface StoryFormValues {
  title: string;
  content: string;
}

/**
 * The title and text of a story (SCREEN-006, SCREEN-014). The text may stay empty when
 * `contentOptional`, that is when the Memory has a photo (mvp.md §17); otherwise an empty text is
 * explained by `emptyContent`.
 */
export function StoryFields({
  form,
  contentOptional = false,
  emptyContent = 'required',
}: {
  form: UseFormReturn<StoryFormValues>;
  contentOptional?: boolean;
  emptyContent?: 'required' | 'contentOrPhoto';
}) {
  const { t } = useTranslation('memory');

  const validate =
    (max: number, optional = false, empty: FieldErrorKey = 'required') =>
    (value: string) => {
      if (value.trim() === '' && !optional) return empty;
      if (value.length > max) return 'tooLong' satisfies FieldErrorKey;
      return true;
    };

  function message(field: keyof StoryFormValues) {
    const key = form.formState.errors[field]?.message as FieldErrorKey | undefined;
    return key === undefined ? undefined : t(`form.${key}`, { max: LIMITS[field] });
  }

  return (
    <>
      <TextField
        label={t('form.titleLabel')}
        autoComplete="off"
        required
        error={message('title')}
        {...form.register('title', { validate: validate(LIMITS.title) })}
      />
      <div className="flex flex-col gap-1">
        <TextArea
          label={t('form.contentLabel')}
          rows={10}
          required={!contentOptional}
          error={message('content')}
          {...form.register('content', {
            validate: validate(LIMITS.content, contentOptional, emptyContent),
          })}
        />
        {contentOptional && (
          <p className="text-caption text-text-muted">{t('form.contentOptional')}</p>
        )}
      </div>
    </>
  );
}

/**
 * Marks the title or text the server refused, and moves the focus to the first of them; the text
 * typed stays in the form.
 */
export function showServerFieldErrors(form: UseFormReturn<StoryFormValues>, failure: unknown) {
  if (!(failure instanceof ApiError)) return;
  let focused = false;
  for (const fieldError of failure.fieldErrors) {
    if (fieldError.field === 'title' || fieldError.field === 'content') {
      form.setError(
        fieldError.field,
        { type: 'server', message: 'invalid' },
        { shouldFocus: !focused },
      );
      focused = true;
    }
  }
}

/** Why the server refused the date of a Memory (`happenedAt`), if it did (OQ-063). */
export function serverDateError(failure: unknown): DateError | null {
  if (!(failure instanceof ApiError)) return null;
  const refusal = failure.fieldErrors.find((fieldError) =>
    fieldError.field.startsWith('happenedAt'),
  );
  if (refusal === undefined) return null;
  return refusal.code === 'FUTURE_DATE' ? 'futureDate' : 'invalid';
}
