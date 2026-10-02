/** Provisional Mbia logo: two leaves and the name, until the real logo file exists. */
export function Logo() {
  return (
    <span className="inline-flex items-center gap-2 text-section text-primary">
      <svg viewBox="0 0 32 32" className="size-8" aria-hidden="true">
        <path className="fill-accent" d="M14 28C6 24 4 14 10 4c4 8 7 16 4 24z" />
        <path className="fill-primary" d="M17 28c1-9 6-15 13-17-1 9-6 15-13 17z" />
      </svg>
      Mbia
    </span>
  );
}
