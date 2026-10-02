import { useId, type SelectHTMLAttributes } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  options: readonly { value: string; label: string }[];
}

/** Labelled native select (design-guidelines.md §7, §9). */
export function Select({ label, options, className = '', ...props }: SelectProps) {
  const id = useId();

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={id} className="text-caption font-semibold text-text">
        {label}
      </label>
      <select
        id={id}
        className="min-h-12 rounded-xl border border-border bg-surface px-4 text-body text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        {...props}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}
