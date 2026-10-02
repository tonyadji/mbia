/** Loading placeholder shaped like the content it stands for (design-guidelines.md §7, §10). */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden="true" className={`animate-pulse rounded-xl bg-border ${className}`} />;
}
