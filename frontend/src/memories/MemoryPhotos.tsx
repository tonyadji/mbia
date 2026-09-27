import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { components } from '../api/generated/schema';
import type { Language } from '../i18n/language';
import { useFreshImageUrl } from '../media/useFreshImageUrl';
import { MemoryPhotoViewer } from './MemoryPhotoViewer';
import { usePhotoAlt } from './photoAlt';

type MemoryPhoto = components['schemas']['MemoryPhotoResponse'];

/**
 * SCREEN-013 photos, in the order they were added: a grid of square thumbnails without text, each
 * a button named by the photo's alternative text, opening the photo viewer on it. Provisional
 * layout until the human has confirmed it (OQ-048, option B).
 */
export function MemoryPhotos({ photos, language }: { photos: MemoryPhoto[]; language: Language }) {
  const { t } = useTranslation('memory');
  const altOf = usePhotoAlt();
  const [open, setOpen] = useState<number | null>(null);
  if (photos.length === 0) return null;
  return (
    <>
      <ul aria-label={t('screen.photos.label')} className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {photos.map((photo, index) => (
          <li key={photo.mediaAssetId} className="min-w-0">
            <MemoryPhotoThumbnail
              photo={photo}
              alt={altOf(photo, index, photos.length)}
              onOpen={() => {
                setOpen(index);
              }}
            />
          </li>
        ))}
      </ul>
      {open !== null && (
        <MemoryPhotoViewer
          photos={photos}
          index={Math.min(open, photos.length - 1)}
          language={language}
          onChange={setOpen}
          onClose={() => {
            setOpen(null);
          }}
        />
      )}
    </>
  );
}

function MemoryPhotoThumbnail({
  photo,
  alt,
  onOpen,
}: {
  photo: MemoryPhoto;
  alt: string;
  onOpen: () => void;
}) {
  const { t } = useTranslation('memory');
  const image = useFreshImageUrl(photo.thumbnailUrl);
  return (
    <button
      type="button"
      aria-label={alt}
      onClick={onOpen}
      className="block aspect-square w-full overflow-hidden rounded-xl bg-border focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      {image.show ? (
        <img
          src={photo.thumbnailUrl}
          alt={alt}
          loading="lazy"
          decoding="async"
          onError={image.onError}
          onLoad={image.onLoad}
          className="size-full object-cover"
        />
      ) : (
        <span className="flex size-full items-center justify-center border border-border bg-surface p-2 text-center text-caption text-text-muted">
          {t('screen.photos.unavailable')}
        </span>
      )}
    </button>
  );
}
