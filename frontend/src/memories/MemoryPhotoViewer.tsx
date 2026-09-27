import { useEffect, useEffectEvent, useId, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { components } from '../api/generated/schema';
import { IconButton } from '../components/IconButton';
import { trapTab } from '../components/focusTrap';
import type { Language } from '../i18n/language';
import { useFreshImageUrl } from '../media/useFreshImageUrl';
import { formatPartialDate } from '../persons/formatPartialDate';
import { usePhotoAlt } from './photoAlt';

type MemoryPhoto = components['schemas']['MemoryPhotoResponse'];

/** The horizontal distance, in CSS pixels, from which a swipe changes the photo. */
const SWIPE = 50;
/** Icon paths, on a 24 × 24 grid. */
const CLOSE = 'M6 6l12 12M18 6 6 18';
const PREVIOUS = 'M15 6l-6 6 6 6';
const NEXT = 'M9 6l6 6-6 6';

/**
 * The photo viewer of SCREEN-013 (OQ-048, option B): over the whole screen, the display version
 * of a photo as large as it fits, its caption, when it was taken and its position. `Previous photo`,
 * `Next photo`, a horizontal swipe and the arrow keys move between the photos, never past the
 * first or the last; `Close` and Escape close it. Focus stays inside and comes back where it was,
 * on the thumbnail that opened it.
 */
export function MemoryPhotoViewer({
  photos,
  index,
  language,
  onChange,
  onClose,
}: {
  photos: MemoryPhoto[];
  index: number;
  language: Language;
  onChange: (index: number) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation(['memory', 'common']);
  const altOf = usePhotoAlt();
  const positionId = useId();
  const panel = useRef<HTMLDivElement>(null);
  const swipeStart = useRef<{ x: number; y: number } | null>(null);
  const photo = photos[index];
  const hasPrevious = index > 0;
  const hasNext = index < photos.length - 1;

  const move = (step: -1 | 1) => {
    const target = index + step;
    if (target >= 0 && target < photos.length) onChange(target);
  };
  const onKey = useEffectEvent((event: KeyboardEvent) => {
    if (event.key === 'Escape') onClose();
    else if (event.key === 'ArrowLeft') move(-1);
    else if (event.key === 'ArrowRight') move(1);
    trapTab(event, panel.current);
  });

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    panel.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      onKey(event);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previous?.focus();
    };
  }, []);

  if (photo === undefined) return null;
  const caption = photo.caption?.trim() ? photo.caption : null;
  const takenAt = photo.takenAt ? formatPartialDate(photo.takenAt, language) : null;

  return (
    <div
      ref={panel}
      role="dialog"
      aria-modal="true"
      aria-labelledby={positionId}
      tabIndex={-1}
      className="fixed inset-0 z-50 flex flex-col bg-text text-white outline-none"
    >
      <div className="flex items-center justify-between gap-4 px-4 pt-[max(0.75rem,env(safe-area-inset-top))]">
        <p id={positionId} aria-live="polite" className="text-body font-semibold">
          {t('memory:screen.photos.alt', { n: index + 1, total: photos.length })}
        </p>
        <IconButton label={t('common:actions.close')} onClick={onClose}>
          <Icon path={CLOSE} />
        </IconButton>
      </div>
      <div
        className="flex min-h-0 flex-1 touch-pan-y items-center justify-center p-4 select-none"
        onPointerDown={(event) => {
          swipeStart.current = { x: event.clientX, y: event.clientY };
        }}
        onPointerUp={(event) => {
          const start = swipeStart.current;
          swipeStart.current = null;
          if (start === null) return;
          const dx = event.clientX - start.x;
          if (Math.abs(dx) >= SWIPE && Math.abs(dx) > Math.abs(event.clientY - start.y)) {
            move(dx < 0 ? 1 : -1);
          }
        }}
        onPointerCancel={() => {
          swipeStart.current = null;
        }}
      >
        <ViewerImage
          key={photo.mediaAssetId}
          photo={photo}
          alt={altOf(photo, index, photos.length)}
        />
      </div>
      <div className="flex flex-col gap-3 px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
        {(caption !== null || takenAt !== null) && (
          <div className="flex max-h-[30dvh] min-w-0 flex-col gap-1 overflow-y-auto">
            {/* Plain text: rendered as a text node, never as HTML or Markdown (OQ-032). */}
            {caption !== null && (
              <p className="text-body break-words whitespace-pre-wrap">{caption}</p>
            )}
            {takenAt !== null && (
              <p className="text-caption">{t('memory:screen.photos.takenAt', { date: takenAt })}</p>
            )}
          </div>
        )}
        <div className="flex justify-between gap-4">
          <IconButton
            label={t('memory:screen.photos.previous')}
            disabled={!hasPrevious}
            onClick={() => {
              move(-1);
            }}
          >
            <Icon path={PREVIOUS} />
          </IconButton>
          <IconButton
            label={t('memory:screen.photos.next')}
            disabled={!hasNext}
            onClick={() => {
              move(1);
            }}
          >
            <Icon path={NEXT} />
          </IconButton>
        </div>
      </div>
    </div>
  );
}

/** The display version, uncropped; an expired URL is fetched again once (PR-44). */
function ViewerImage({ photo, alt }: { photo: MemoryPhoto; alt: string }) {
  const { t } = useTranslation('memory');
  const image = useFreshImageUrl(photo.url);
  if (!image.show) {
    return (
      <div role="img" aria-label={alt} className="px-4 text-center text-body">
        {t('screen.photos.unavailable')}
      </div>
    );
  }
  return (
    <img
      src={photo.url}
      alt={alt}
      draggable={false}
      onError={image.onError}
      onLoad={image.onLoad}
      className="size-full object-contain"
    />
  );
}

function Icon({ path }: { path: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-6"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={path} />
    </svg>
  );
}
