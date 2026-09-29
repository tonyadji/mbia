import { useId, type InputHTMLAttributes, type Ref } from 'react';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Translated validation message; marks the field invalid when present. */
  error?: string | undefined;
  ref?: Ref<HTMLInputElement>;
}

/** Labelled text input with its validation message (design-guidelines.md §7, §9). */
export function TextField({ label, error, className = '', ...props }: TextFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={id} className="text-caption font-semibold text-text">
        {label}
      </label>
      {/* `focus-within`, not `focus-visible`: the calendar button inside a date field takes the focus
          without matching `:focus` in Chromium; a text field shows its focus either way (§9). */}
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`min-h-12 rounded-xl border bg-surface px-4 text-body text-text focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary ${error ? 'border-2 border-text' : 'border-border'}`}
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
