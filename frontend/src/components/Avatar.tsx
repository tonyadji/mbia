/** The first letter of the display name, otherwise of the email (user content, never translated). */
export function initialOf(displayName: string | null | undefined, email: string): string {
  const name = displayName?.trim() ?? '';
  const source = name === '' ? email : name;
  return (Array.from(source)[0] ?? '').toLocaleUpperCase();
}

/** Round avatar showing the User's initial (design-guidelines.md §7); decorative. */
export function Avatar({ displayName, email }: { displayName?: string | null; email: string }) {
  return (
    <span
      aria-hidden="true"
      className="inline-flex size-10 items-center justify-center rounded-full bg-primary text-body font-semibold text-on-primary"
    >
      {initialOf(displayName, email)}
    </span>
  );
}
