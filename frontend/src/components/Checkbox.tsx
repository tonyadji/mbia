import { useId, type InputHTMLAttributes, type Ref } from 'react';

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  ref?: Ref<HTMLInputElement>;
}

/** Labelled checkbox with a touch-friendly target (design-guidelines.md §9). */
export function Checkbox({ label, className = '', ...props }: CheckboxProps) {
  const id = useId();

  return (
    <div className={`flex min-h-12 items-center gap-3 ${className}`}>
      <input
        id={id}
        type="checkbox"
        className="size-5 accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        {...props}
      />
      <label htmlFor={id} className="text-body text-text">
        {label}
      </label>
    </div>
  );
}
