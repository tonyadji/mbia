import { useId, type Ref, type TextareaHTMLAttributes } from 'react';

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  /** Translated validation message; marks the field invalid when present. */
  error?: string | undefined;
  ref?: Ref<HTMLTextAreaElement>;
}

/** Multi-line variant of `TextField`, for long free text such as a biography. */
export function TextArea({ label, error, className = '', ...props }: TextAreaProps) {
  const id = useId();
  const errorId = `${id}-error`;

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={id} className="text-caption font-semibold text-text">
        {label}
      </label>
      <textarea
        id={id}
        rows={4}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`rounded-xl border bg-surface px-4 py-3 text-body text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${error ? 'border-2 border-text' : 'border-border'}`}
        {...props}
      />
      {error && (
        <p id={errorId} className="text-caption font-semibold text-text">
          {error}
        </p>
      )}
    </div>
  );
}
