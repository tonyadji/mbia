import { useEffect, useId, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { PHOTO_TYPES } from './prepareImage';
import { usePhotoUpload } from './usePhotoUpload';

/** What the form does with the Person's photo when it is saved. */
export type PhotoChange =
  { kind: 'unchanged' } | { kind: 'new'; assetId: string } | { kind: 'removed' };

export const PHOTO_UNCHANGED: PhotoChange = { kind: 'unchanged' };

/**
 * The Person photo control of SCREEN-004 (`profilePicture`) and SCREEN-012 (OQ-040): `Add a photo`
 * when there is none, otherwise `Change the photo` and `Remove the photo`. A new photo is uploaded
 * at once, with progress, and shown centre-cropped in the round avatar; the Person changes only
 * when the form is saved. `onBusy` tells the form an upload is running. The form gives the field a
 * new `key` to go back to the saved photo (for example after `Reload latest version`).
 */
export function PhotoField({
  familyId,
  name,
  currentUrl,
  value,
  onChange,
  onBusy,
  disabled = false,
}: {
  familyId: string;
  /** The Person's name: the avatar's initial and the photo's alternative text. */
  name: string;
  /** The Person's saved photo. */
  currentUrl: string | null;
  value: PhotoChange;
  onChange: (value: PhotoChange) => void;
  onBusy: (busy: boolean) => void;
  disabled?: boolean;
}) {
  const { t } = useTranslation('person');
  const labelId = useId();
  const input = useRef<HTMLInputElement>(null);
  const { state, start, retry, reset } = usePhotoUpload(familyId);
  const busy =
    state.status === 'preparing' || state.status === 'uploading' || state.status === 'processing';

  useEffect(() => {
    onBusy(busy);
  }, [busy, onBusy]);

  const shownUrl =
    value.kind === 'removed'
      ? null
      : state.status === 'ready'
        ? (state.asset.thumbnailUrl ?? null)
        : currentUrl;
  const hasPhoto = shownUrl !== null;

  const choose = () => input.current?.click();

  return (
    <div role="group" aria-labelledby={labelId} className="flex flex-col gap-3">
      <span id={labelId} className="text-caption font-semibold text-text">
        {t('photo.label')}
      </span>
      <div className="flex items-center gap-4">
        <Avatar displayName={name} photoUrl={shownUrl} size="lg" alt={name} />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <Button variant="secondary" disabled={disabled || busy} onClick={choose}>
            {hasPhoto ? t('photo.change') : t('photo.add')}
          </Button>
          {hasPhoto && (
            <Button
              variant="secondary"
              disabled={disabled || busy}
              onClick={() => {
                reset();
                onChange({ kind: 'removed' });
              }}
            >
              {t('photo.remove')}
            </Button>
          )}
        </div>
      </div>
      <input
        ref={input}
        type="file"
        accept={PHOTO_TYPES.join(',')}
        hidden
        data-testid="photo-input"
        onChange={(event) => {
          const picked = event.target.files?.[0];
          // The same file may be chosen again after an error.
          event.target.value = '';
          if (picked) {
            void start(picked).then((asset) => {
              if (asset) onChange({ kind: 'new', assetId: asset.id });
            });
          }
        }}
      />
      {value.kind === 'removed' && currentUrl !== null && (
        <p className="text-caption text-text-muted">{t('photo.removedHint')}</p>
      )}
      {busy && <UploadProgress percent={state.status === 'uploading' ? state.percent : null} />}
      {busy && state.status !== 'uploading' && (
        <p className="text-caption text-text-muted" aria-live="polite">
          {state.status === 'processing' ? t('photo.processing') : t('photo.preparing')}
        </p>
      )}
      {state.status === 'error' && (
        <div role="alert" className="flex flex-col gap-2">
          <p className="text-body text-text">{t(`photo.errors.${state.kind}`)}</p>
          {state.kind === 'failed' ? (
            <Button
              variant="secondary"
              disabled={disabled}
              onClick={() => {
                void retry().then((asset) => {
                  if (asset) onChange({ kind: 'new', assetId: asset.id });
                });
              }}
            >
              {t('photo.retry')}
            </Button>
          ) : (
            <Button variant="secondary" disabled={disabled} onClick={choose}>
              {t('photo.chooseAnother')}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

function UploadProgress({ percent }: { percent: number | null }) {
  const { t } = useTranslation('person');
  return (
    <div className="flex flex-col gap-1">
      <div
        role="progressbar"
        aria-label={t('photo.progressLabel')}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
        className="h-2 overflow-hidden rounded-full bg-border"
      >
        <div
          className="h-full rounded-full bg-primary transition-[width]"
          style={{ width: `${String(percent ?? 0)}%` }}
        />
      </div>
      {percent !== null && (
        <p className="text-caption text-text-muted" aria-live="polite">
          {t('photo.uploading', { percent })}
        </p>
      )}
    </div>
  );
}
