import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { prepareImage, type PhotoRefusal } from './prepareImage';
import { uploadPhoto, type MediaAsset } from './uploadPhoto';

/** Why a photo could not be used: refused before upload, not sent, or refused by the server. */
export type PhotoErrorKind = PhotoRefusal | 'failed' | 'invalid';

export type PhotoUploadState =
  | { status: 'idle' }
  | { status: 'preparing' }
  | { status: 'uploading'; percent: number }
  | { status: 'processing' }
  | { status: 'ready'; asset: MediaAsset }
  | { status: 'error'; kind: PhotoErrorKind };

function errorKind(error: unknown): PhotoErrorKind {
  if (error instanceof ApiError) {
    if (error.code === 'MEDIA_INVALID') return 'invalid';
    if (error.code === 'MEDIA_TOO_LARGE') return 'tooLarge';
  }
  return 'failed';
}

/**
 * Prepares and uploads a Person photo, with progress; after a failure, `retry` sends the same file
 * again through a new upload slot (family-tree-ux.md §13). Both resolve to the READY asset, or to
 * `null` when the photo was not usable or a later attempt replaced this one.
 */
export function usePhotoUpload(familyId: string) {
  const [state, setState] = useState<PhotoUploadState>({ status: 'idle' });
  const file = useRef<File | null>(null);
  // Only the latest attempt may change the state.
  const attempt = useRef(0);

  // An upload that ends after the field is gone changes nothing.
  useEffect(
    () => () => {
      attempt.current++;
    },
    [],
  );

  const run = useCallback(
    async (picked: File): Promise<MediaAsset | null> => {
      const current = ++attempt.current;
      const update = (next: PhotoUploadState) => {
        if (attempt.current === current) setState(next);
      };
      file.current = picked;
      update({ status: 'preparing' });
      try {
        const prepared = await prepareImage(picked);
        if (!prepared.ok) {
          update({ status: 'error', kind: prepared.refusal });
          return null;
        }
        const asset = await uploadPhoto(familyId, prepared, {
          onProgress: (share) => {
            update({ status: 'uploading', percent: Math.round(share * 100) });
          },
          onProcessing: () => {
            update({ status: 'processing' });
          },
        });
        update({ status: 'ready', asset });
        return attempt.current === current ? asset : null;
      } catch (error) {
        update({ status: 'error', kind: errorKind(error) });
        return null;
      }
    },
    [familyId],
  );

  const retry = useCallback(
    () => (file.current ? run(file.current) : Promise.resolve(null)),
    [run],
  );

  const reset = useCallback(() => {
    attempt.current++;
    file.current = null;
    setState({ status: 'idle' });
  }, []);

  return { state, start: run, retry, reset };
}
