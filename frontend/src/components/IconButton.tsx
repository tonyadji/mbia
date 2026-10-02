import type { ButtonHTMLAttributes } from 'react';

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** What the button does, read by screen readers: the icon is decorative. */
  label: string;
}

/** Round, touch-friendly button showing only an icon (design-guidelines.md §7, §9). */
export function IconButton({ label, className = '', children, ...props }: IconButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={`inline-flex size-12 shrink-0 items-center justify-center rounded-full border border-border bg-surface text-primary transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-40 ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
