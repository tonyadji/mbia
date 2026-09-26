import { useEffect, useEffectEvent, useId, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * A panel over the screen (design-guidelines.md §7): a bottom sheet on a phone, a side panel on
 * desktop (family-tree-ux.md §3, §8). Escape, the backdrop and the close button close it; focus
 * moves into it and comes back where it was.
 */
export function BottomSheet({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);
  const close = useEffectEvent(onClose);

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    panel.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previous?.focus();
    };
  }, []);

  return (
    <div className="fixed inset-0 z-40">
      <div aria-hidden="true" className="absolute inset-0 bg-text/30" onClick={onClose} />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="absolute inset-x-0 bottom-0 flex max-h-[85dvh] flex-col gap-4 overflow-y-auto rounded-t-2xl bg-surface px-4 pt-3 pb-[max(1.5rem,env(safe-area-inset-bottom))] outline-none sm:inset-y-0 sm:right-0 sm:left-auto sm:max-h-none sm:w-96 sm:rounded-none sm:px-6 sm:pt-6"
      >
        <div aria-hidden="true" className="mx-auto h-1 w-10 rounded-full bg-border sm:hidden" />
        <div className="flex items-start justify-between gap-4">
          <h2 id={titleId} className="text-section break-words text-text">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('actions.close')}
            className="-m-2 inline-flex size-12 shrink-0 items-center justify-center rounded-full text-text-muted hover:text-text focus-visible:outline-2 focus-visible:outline-primary"
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              className="size-6"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            >
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
