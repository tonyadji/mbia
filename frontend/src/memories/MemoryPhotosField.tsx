import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type Dispatch,
  type SetStateAction,
} from 'react';
import { useTranslation } from 'react-i18next';
import type { components } from '../api/generated/schema';
import { Button } from '../components/Button';
import { Select } from '../components/Select';
import { TextField } from '../components/TextField';
import { PHOTO_TYPES } from '../media/prepareImage';
import { UploadProgress } from '../media/UploadProgress';
import { usePhotoUpload } from '../media/usePhotoUpload';
import {
  partialDate,
  requiredDate,
  validYear,
  YEAR_MAX,
  YEAR_MIN,
} from '../persons/PersonFormFields';

type Precision = components['schemas']['DatePrecision'];
type MemoryPhotoInput = components['schemas']['MemoryPhotoInput'];
type MemoryPhoto = components['schemas']['MemoryPhotoResponse'];

/** `MemoryPhotoInput.caption` limit (data-model.md §14bis). */
const CAPTION_MAX = 5000;

/**
 * A photo of a Memory's form: a file chosen, with its upload, or a photo already on the Memory
 * (SCREEN-014), then its caption and taken date (SCREEN-006).
 */
export interface MemoryPhotoDraft {
  key: string;
  /** The file to send; `null` for a photo already on the Memory. */
  file: File | null;
  status: 'busy' | 'ready' | 'error';
  /** The READY asset, once sent. */
  assetId: string | null;
  thumbnailUrl: string | null;
  caption: string;
  precision: Precision;
  date: string;
  year: string;
}

let nextKey = 0;

function newDraft(file: File): MemoryPhotoDraft {
  nextKey++;
  return {
    key: `photo-${String(nextKey)}`,
    file,
    status: 'busy',
    assetId: null,
    thumbnailUrl: null,
    caption: '',
    precision: 'UNKNOWN',
    date: '',
    year: '',
  };
}

/** The photos of a Memory, in position order, as a form edits them (SCREEN-014). */
export function fromMemoryPhotos(photos: MemoryPhoto[]): MemoryPhotoDraft[] {
  return photos.map((photo) => ({
    key: `saved-${photo.mediaAssetId}`,
    file: null,
    status: 'ready',
    assetId: photo.mediaAssetId,
    thumbnailUrl: photo.thumbnailUrl,
    caption: photo.caption ?? '',
    precision: photo.takenAt?.precision ?? 'UNKNOWN',
    date: photo.takenAt?.date ?? '',
    year: photo.takenAt?.year?.toString() ?? '',
  }));
}

/** Why the taken date of a photo cannot be sent, like a birth date (OQ-033). */
export function takenAtError(photo: MemoryPhotoDraft) {
  const valid =
    photo.precision === 'EXACT'
      ? requiredDate(photo.date)
      : photo.precision === 'YEAR_ONLY'
        ? validYear(photo.year)
        : true;
  return valid === true ? null : valid;
}

/** The photos ready to be published, in the order they were added (`MemoryPhotoInput[]`). */
export function toPhotoInputs(photos: MemoryPhotoDraft[]): MemoryPhotoInput[] {
  return photos.flatMap((photo) =>
    photo.status === 'ready' && photo.assetId !== null
      ? [
          {
            mediaAssetId: photo.assetId,
            caption: photo.caption.trim() === '' ? null : photo.caption.trim(),
            takenAt:
              photo.precision === 'UNKNOWN'
                ? null
                : partialDate(photo.precision, photo.date, photo.year.trim()),
          },
        ]
      : [],
  );
}

/**
 * The photos section of SCREEN-006 and SCREEN-014 (family-tree-ux.md §13): `Add a photo` picks one or several
 * files, of which only the free places up to the Family's `limit` are kept. Each photo is reduced
 * and sent at once (PR-38 pipeline, purpose `MEMORY_PHOTO`), with its progress, `Try again` after a
 * failure and `Remove`, then gets a caption and, behind `More information`, a taken date. The
 * photos already on a Memory are described or removed the same way; above a lowered limit, they
 * stay and `Add a photo` waits until enough are removed (mvp.md §17).
 * `showErrors` shows the invalid taken dates once the User tried to publish.
 */
export function MemoryPhotosField({
  familyId,
  limit,
  photos,
  onChange,
  showErrors,
  disabled = false,
}: {
  familyId: string;
  limit: number;
  photos: MemoryPhotoDraft[];
  onChange: Dispatch<SetStateAction<MemoryPhotoDraft[]>>;
  showErrors: boolean;
  disabled?: boolean;
}) {
  const { t } = useTranslation('memory');
  const labelId = useId();
  const limitId = useId();
  const input = useRef<HTMLInputElement>(null);
  const [leftOut, setLeftOut] = useState(0);
  const atLimit = photos.length >= limit;
  // A lowered limit leaves the photos of a Memory in place; none can be added until enough are removed.
  const overLimit = photos.length > limit;

  const update = useCallback(
    (key: string, patch: Partial<MemoryPhotoDraft>) => {
      onChange((current) =>
        current.map((photo) => (photo.key === key ? { ...photo, ...patch } : photo)),
      );
    },
    [onChange],
  );

  const remove = (key: string) => {
    setLeftOut(0);
    onChange((current) => current.filter((photo) => photo.key !== key));
  };

  const add = (files: File[]) => {
    const free = Math.max(0, limit - photos.length);
    setLeftOut(Math.max(0, files.length - free));
    onChange((current) => [...current, ...files.slice(0, free).map(newDraft)]);
  };

  return (
    <div role="group" aria-labelledby={labelId} className="flex flex-col gap-4">
      <span id={labelId} className="text-caption font-semibold text-text">
        {t('form.photos.label')}
      </span>
      {photos.length > 0 && (
        <ol className="flex flex-col gap-6">
          {photos.map((photo, index) => (
            <li key={photo.key}>
              <MemoryPhotoItem
                familyId={familyId}
                photo={photo}
                position={index + 1}
                onUpdate={update}
                onRemove={remove}
                showErrors={showErrors}
                disabled={disabled}
              />
            </li>
          ))}
        </ol>
      )}
      <Button
        variant="secondary"
        disabled={disabled || atLimit}
        aria-describedby={atLimit ? limitId : undefined}
        onClick={() => input.current?.click()}
      >
        {t('form.photos.add')}
      </Button>
      <p id={limitId} role="status" className="text-caption text-text-muted">
        {overLimit
          ? t('form.photos.overLimit', { count: limit })
          : (atLimit || leftOut > 0) && t('form.photos.limit', { count: limit })}
        {leftOut > 0 && ` ${t('form.photos.leftOut', { count: leftOut })}`}
      </p>
      <input
        ref={input}
        type="file"
        accept={PHOTO_TYPES.join(',')}
        multiple
        hidden
        data-testid="memory-photo-input"
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          // The same files may be chosen again after a removal.
          event.target.value = '';
          if (files.length > 0) add(files);
        }}
      />
    </div>
  );
}

function MemoryPhotoItem({
  familyId,
  photo,
  position,
  onUpdate,
  onRemove,
  showErrors,
  disabled,
}: {
  familyId: string;
  photo: MemoryPhotoDraft;
  position: number;
  onUpdate: (key: string, patch: Partial<MemoryPhotoDraft>) => void;
  onRemove: (key: string) => void;
  showErrors: boolean;
  disabled: boolean;
}) {
  const { t } = useTranslation(['memory', 'person']);
  const detailsId = useId();
  const [open, setOpen] = useState(false);
  const { key, file } = photo;

  const dateError = showErrors ? takenAtError(photo) : null;
  const dateMessage =
    dateError === 'invalidYear'
      ? t('person:form.invalidYear', { min: YEAR_MIN, max: YEAR_MAX })
      : dateError === 'dateRequired'
        ? t('person:form.dateRequired')
        : undefined;
  const precisionOptions = (['UNKNOWN', 'YEAR_ONLY', 'EXACT'] as const).map((value) => ({
    value,
    label: t(`person:form.precision.${value}`),
  }));

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-2 text-body font-semibold text-text">
        {t('form.photos.photo', { position })}
      </legend>
      <div className="flex items-start gap-4">
        {photo.thumbnailUrl ? (
          <img
            src={photo.thumbnailUrl}
            alt={t('form.photos.thumbnail', { position })}
            className="size-20 shrink-0 rounded-xl object-cover"
          />
        ) : (
          <div className="size-20 shrink-0 rounded-xl bg-border" />
        )}
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          {file !== null && (
            <NewPhotoUpload
              familyId={familyId}
              photoKey={key}
              file={file}
              position={position}
              onUpdate={onUpdate}
              disabled={disabled}
            />
          )}
          <Button
            variant="secondary"
            disabled={disabled}
            aria-label={t('form.photos.removeLabel', { position })}
            onClick={() => {
              onRemove(key);
            }}
          >
            {t('form.photos.remove')}
          </Button>
        </div>
      </div>
      <TextField
        label={t('form.photos.caption', { position })}
        autoComplete="off"
        maxLength={CAPTION_MAX}
        value={photo.caption}
        disabled={disabled}
        onChange={(event) => {
          onUpdate(key, { caption: event.target.value });
        }}
      />
      <button
        type="button"
        aria-expanded={open || dateError !== null}
        aria-controls={detailsId}
        aria-label={t('form.photos.moreInfoLabel', { position })}
        className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        onClick={() => {
          setOpen((value) => !value);
        }}
      >
        {t('form.photos.moreInfo')}
      </button>
      {(open || dateError !== null) && (
        <div id={detailsId} className="flex flex-col gap-4">
          <Select
            label={t('form.photos.takenAt')}
            options={precisionOptions}
            value={photo.precision}
            disabled={disabled}
            onChange={(event) => {
              onUpdate(key, { precision: event.target.value as Precision });
            }}
          />
          {photo.precision === 'EXACT' && (
            <TextField
              type="date"
              label={t('form.photos.takenDate')}
              value={photo.date}
              error={dateMessage}
              disabled={disabled}
              onChange={(event) => {
                onUpdate(key, { date: event.target.value });
              }}
            />
          )}
          {photo.precision === 'YEAR_ONLY' && (
            <TextField
              inputMode="numeric"
              label={t('form.photos.takenYear')}
              value={photo.year}
              error={dateMessage}
              disabled={disabled}
              onChange={(event) => {
                onUpdate(key, { year: event.target.value });
              }}
            />
          )}
        </div>
      )}
    </fieldset>
  );
}

/** The upload of a photo just chosen: sent once, with its progress, its error and `Try again`. */
function NewPhotoUpload({
  familyId,
  photoKey,
  file,
  position,
  onUpdate,
  disabled,
}: {
  familyId: string;
  photoKey: string;
  file: File;
  position: number;
  onUpdate: (key: string, patch: Partial<MemoryPhotoDraft>) => void;
  disabled: boolean;
}) {
  const { t } = useTranslation('memory');
  const { state, start, retry } = usePhotoUpload(familyId, 'MEMORY_PHOTO');
  const busy = state.status !== 'ready' && state.status !== 'error';

  // Sent once, as soon as it is chosen.
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void start(file);
  }, [start, file]);

  useEffect(() => {
    onUpdate(
      photoKey,
      state.status === 'ready'
        ? {
            status: 'ready',
            assetId: state.asset.id,
            thumbnailUrl: state.asset.thumbnailUrl ?? null,
          }
        : { status: state.status === 'error' ? 'error' : 'busy', assetId: null },
    );
  }, [state, photoKey, onUpdate]);

  return (
    <>
      {busy && (
        <>
          <UploadProgress
            percent={state.status === 'uploading' ? state.percent : null}
            label={t('form.photos.progressLabel', { position })}
            text={t('form.photos.uploading', {
              percent: state.status === 'uploading' ? state.percent : 0,
            })}
          />
          {state.status !== 'uploading' && (
            <p className="text-caption text-text-muted" aria-live="polite">
              {state.status === 'processing'
                ? t('form.photos.processing')
                : t('form.photos.preparing')}
            </p>
          )}
        </>
      )}
      {state.status === 'error' && (
        <p role="alert" className="text-body text-text">
          {t(`form.photos.errors.${state.kind}`)}
        </p>
      )}
      {state.status === 'error' && state.kind === 'failed' && (
        <Button
          variant="secondary"
          disabled={disabled}
          aria-label={t('form.photos.retryLabel', { position })}
          onClick={() => void retry()}
        >
          {t('form.photos.retry')}
        </Button>
      )}
    </>
  );
}
