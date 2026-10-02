import { useTranslation } from 'react-i18next';
import type { components } from '../api/generated/schema';

type MemoryPhoto = components['schemas']['MemoryPhotoResponse'];

/** The alternative text of a photo: its caption, otherwise "Photo {n} of {count}" (SCREEN-013). */
export function usePhotoAlt() {
  const { t } = useTranslation('memory');
  return (photo: MemoryPhoto, index: number, total: number) =>
    photo.caption?.trim() ? photo.caption : t('screen.photos.alt', { n: index + 1, total });
}
