import type { InputHTMLAttributes, Ref } from 'react';

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  ref?: Ref<HTMLInputElement>;
}

/** Labelled checkbox with a touch-friendly target (design-guidelines.md §9). */
export function Checkbox({ label, className = '', ...props }: CheckboxProps) {
  // The whole row, box and text, is the label: one 48 px target.
  return (
    <label
      className={`flex min-h-12 cursor-pointer items-center gap-3 text-body text-text ${className}`}
    >
      <input
        type="checkbox"
        className="size-5 shrink-0 accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        {...props}
      />
      {label}
    </label>
  );
}
