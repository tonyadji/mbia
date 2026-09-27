import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { familiesQueryKey } from '../families/useMyFamilies';

/** The first letter of the display name, otherwise of the email (user content, never translated). */
export function initialOf(displayName: string | null | undefined, email: string): string {
  const name = displayName?.trim() ?? '';
  const source = name === '' ? email : name;
  return (Array.from(source)[0] ?? '').toLocaleUpperCase();
}

const sizes = {
  md: 'size-10 text-body',
  card: 'size-14 text-section',
  lg: 'size-20 text-display',
} as const;

/**
 * Round avatar of a User or a Person (design-guidelines.md §5, §7). A Person's photo is shown
 * centre-cropped (OQ-040), with the initial as the fallback. It is decorative where the name is the
 * text of the same card or row; `alt` (the Person's name) describes the photo where it stands for
 * the Person on its own, as in a profile header.
 *
 * Photo URLs are pre-signed for 60 minutes (data-model.md §13). When one fails to load, most likely
 * because it expired, the Family data on screen is fetched again to get freshly signed URLs; a new
 * URL that fails as well leaves the initial.
 */
export function Avatar({
  displayName,
  email = '',
  photoUrl = null,
  size = 'md',
  alt,
}: {
  displayName?: string | null;
  email?: string;
  photoUrl?: string | null;
  size?: keyof typeof sizes;
  /** The Person's name, for a photo that is not decorative. */
  alt?: string;
}) {
  const queryClient = useQueryClient();
  // The last URL that failed to load, until a photo loads again.
  const [failed, setFailed] = useState<string | null>(null);
  const showPhoto = photoUrl !== null && failed !== photoUrl;
  const described = showPhoto && alt !== undefined && alt !== '';

  const onError = () => {
    if (photoUrl === null) return;
    setFailed(photoUrl);
    // A second failure in a row, with a fresh URL, keeps the initial: no reload loop.
    if (failed === null) {
      void queryClient.invalidateQueries(
        { queryKey: familiesQueryKey, refetchType: 'active' },
        { cancelRefetch: false },
      );
    }
  };

  return (
    <span
      aria-hidden={described ? undefined : true}
      className={`inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary font-semibold text-on-primary ${sizes[size]}`}
    >
      {showPhoto ? (
        <img
          src={photoUrl}
          alt={described ? alt : ''}
          loading="lazy"
          onError={onError}
          onLoad={() => {
            setFailed(null);
          }}
          className="size-full object-cover"
        />
      ) : (
        initialOf(displayName, email)
      )}
    </span>
  );
}
