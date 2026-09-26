/** The first letter of the display name, otherwise of the email (user content, never translated). */
export function initialOf(displayName: string | null | undefined, email: string): string {
  const name = displayName?.trim() ?? '';
  const source = name === '' ? email : name;
  return (Array.from(source)[0] ?? '').toLocaleUpperCase();
}

const sizes = {
  md: 'size-10 text-body',
  card: 'size-14 text-section',
  lg: 'size-20 text-display',
} as const;

/**
 * Round avatar showing the initial of a User or a Person (design-guidelines.md §7); decorative.
 * Persons have no photo in Phase 2.
 */
export function Avatar({
  displayName,
  email = '',
  size = 'md',
}: {
  displayName?: string | null;
  email?: string;
  size?: keyof typeof sizes;
}) {
  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center rounded-full bg-primary font-semibold text-on-primary ${sizes[size]}`}
    >
      {initialOf(displayName, email)}
    </span>
  );
}
