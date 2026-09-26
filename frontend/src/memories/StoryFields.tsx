import type { UseFormReturn } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { TextArea } from '../components/TextArea';
import { TextField } from '../components/TextField';

/** `CreateStoryMemoryRequest` / `UpdateMemoryRequest` limits (mvp.md §17, data-model.md §14). */
const LIMITS = { title: 250, content: 50_000 } as const;

/** Validation errors are stored as keys, so that their message follows a language change. */
type FieldErrorKey = 'required' | 'tooLong' | 'invalid';

/** Codes explained in the words of a Memory; others use the shared `errors` messages. */
export const MEMORY_ERRORS = ['PERSON_NOT_ACTIVE', 'PERSON_NOT_FOUND'] as const;

export interface StoryFormValues {
  title: string;
  content: string;
}

/** The title and text of a story (SCREEN-006, SCREEN-014). */
export function StoryFields({ form }: { form: UseFormReturn<StoryFormValues> }) {
  const { t } = useTranslation('memory');

  const validate = (max: number) => (value: string) => {
    if (value.trim() === '') return 'required' satisfies FieldErrorKey;
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
      <TextArea
        label={t('form.contentLabel')}
        rows={10}
        required
        error={message('content')}
        {...form.register('content', { validate: validate(LIMITS.content) })}
      />
    </>
  );
}

/** Marks the title or text the server refused; the text typed stays in the form. */
export function showServerFieldErrors(form: UseFormReturn<StoryFormValues>, failure: unknown) {
  if (!(failure instanceof ApiError)) return;
  for (const fieldError of failure.fieldErrors) {
    if (fieldError.field === 'title' || fieldError.field === 'content') {
      form.setError(fieldError.field, { type: 'server', message: 'invalid' });
    }
  }
}
