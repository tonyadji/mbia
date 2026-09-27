import { useTranslation } from 'react-i18next';
import type { components } from '../api/generated/schema';
import type { Language } from '../i18n/language';
import { useFreshImageUrl } from '../media/useFreshImageUrl';
import { formatPartialDate } from '../persons/formatPartialDate';

type MemoryPhoto = components['schemas']['MemoryPhotoResponse'];

/**
 * SCREEN-013 photos, in the order they were added: the display version of each at full width,
 * stacked, with its caption and when it was taken if known. The alternative text is the caption,
 * otherwise "Photo {n} of {count}". Provisional layout until the human has seen it (OQ-048).
 */
export function MemoryPhotos({ photos, language }: { photos: MemoryPhoto[]; language: Language }) {
  const { t } = useTranslation('memory');
  if (photos.length === 0) return null;
  return (
    <ol aria-label={t('screen.photos.label')} className="flex min-w-0 flex-col gap-6">
      {photos.map((photo, index) => (
        <li key={photo.mediaAssetId} className="min-w-0">
          <MemoryPhotoFigure
            photo={photo}
            alt={
              photo.caption?.trim()
                ? photo.caption
                : t('screen.photos.alt', { n: index + 1, total: photos.length })
            }
            language={language}
          />
        </li>
      ))}
    </ol>
  );
}

function MemoryPhotoFigure({
  photo,
  alt,
  language,
}: {
  photo: MemoryPhoto;
  alt: string;
  language: Language;
}) {
  const { t } = useTranslation('memory');
  const image = useFreshImageUrl(photo.url);
  const caption = photo.caption?.trim() ? photo.caption : null;
  const takenAt = photo.takenAt ? formatPartialDate(photo.takenAt, language) : null;
  return (
    <figure className="flex min-w-0 flex-col gap-2">
      {image.show ? (
        <img
          src={photo.url}
          alt={alt}
          loading="lazy"
          decoding="async"
          // The display size reserves the place of the image before it loads.
          width={photo.widthPx ?? undefined}
          height={photo.heightPx ?? undefined}
          onError={image.onError}
          onLoad={image.onLoad}
          className="block h-auto w-full rounded-xl bg-border"
        />
      ) : (
        <div
          role="img"
          aria-label={alt}
          className="flex min-h-40 items-center justify-center rounded-xl border border-border bg-surface px-4 py-6 text-center text-caption text-text-muted"
        >
          {t('screen.photos.unavailable')}
        </div>
      )}
      {(caption !== null || takenAt !== null) && (
        <figcaption className="flex flex-col gap-1">
          {/* Plain text: rendered as a text node, never as HTML or Markdown (OQ-032). */}
          {caption !== null && (
            <span className="text-body break-words whitespace-pre-wrap text-text">{caption}</span>
          )}
          {takenAt !== null && (
            <span className="text-caption text-text-muted">
              {t('screen.photos.takenAt', { date: takenAt })}
            </span>
          )}
        </figcaption>
      )}
    </figure>
  );
}
