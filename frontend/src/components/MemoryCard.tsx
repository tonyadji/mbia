import { Link } from 'react-router';

/**
 * A story card (design-guidelines.md §6, §7): the title and the first lines of the text, opening
 * the Memory. The text is plain: line breaks are kept, nothing is interpreted (OQ-032).
 */
export function MemoryCard({ title, content, to }: { title: string; content: string; to: string }) {
  return (
    <Link
      to={to}
      className="flex flex-col gap-1 rounded-2xl border border-border bg-surface px-4 py-3 transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <span className="text-body font-semibold break-words text-text">{title}</span>
      <span className="line-clamp-3 text-caption break-words whitespace-pre-line text-text-muted">
        {content}
      </span>
    </Link>
  );
}
