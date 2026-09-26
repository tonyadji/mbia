import { useEffect, useEffectEvent, useId, useRef, type ReactNode } from 'react';

/**
 * A short message that needs an answer before going on (design-guidelines.md §7), for example a
 * confirmation: centred over the screen, labelled by its message. Escape and the backdrop close
 * it; focus moves into it and comes back where it was.
 */
export function Modal({
  message,
  onClose,
  children,
}: {
  message: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const messageId = useId();
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
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div aria-hidden="true" className="absolute inset-0 bg-text/30" onClick={onClose} />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={messageId}
        tabIndex={-1}
        className="relative flex w-full max-w-md flex-col gap-6 rounded-2xl bg-surface p-6 outline-none"
      >
        <p id={messageId} className="text-body break-words text-text">
          {message}
        </p>
        {children}
      </div>
    </div>
  );
}
