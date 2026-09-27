import { Link } from 'react-router';
import { useFreshImageUrl } from '../media/useFreshImageUrl';

/**
 * A story card (design-guidelines.md §6, §7): the thumbnail of the first photo when there is one,
 * the title and the first lines of the text, opening the Memory (SCREEN-005, SCREEN-015). The
 * thumbnail is decorative: the title names the card. A Memory without text shows its title only.
 * The text is plain: line breaks are kept, nothing is interpreted (OQ-032).
 */
export function MemoryCard({
  title,
  content,
  thumbnailUrl,
  to,
}: {
  title: string;
  content: string | null;
  thumbnailUrl: string | null;
  to: string;
}) {
  const thumbnail = useFreshImageUrl(thumbnailUrl);
  return (
    <Link
      to={to}
      className="flex items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3 transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      {thumbnail.show && (
        <img
          src={thumbnailUrl ?? undefined}
          alt=""
          aria-hidden="true"
          loading="lazy"
          decoding="async"
          onError={thumbnail.onError}
          onLoad={thumbnail.onLoad}
          className="size-16 shrink-0 rounded-xl bg-border object-cover"
        />
      )}
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-body font-semibold break-words text-text">{title}</span>
        {content?.trim() && (
          <span className="line-clamp-3 text-caption break-words whitespace-pre-line text-text-muted">
            {content}
          </span>
        )}
      </span>
    </Link>
  );
}
