import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'secondary';

const variants: Record<Variant, string> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-hover',
  secondary: 'border border-primary text-primary hover:bg-surface',
};

/** The button look, also for links that act as a button (for example to another site). */
export function buttonClassName(variant: Variant = 'primary', className = '') {
  return `inline-flex min-h-12 w-full items-center justify-center rounded-full px-6 text-center text-body font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${variants[variant]} ${className}`;
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

/** Full-width, touch-friendly button (design-guidelines.md §7, §9). */
export function Button({ variant = 'primary', className = '', ...props }: ButtonProps) {
  return <button type="button" className={buttonClassName(variant, className)} {...props} />;
}
