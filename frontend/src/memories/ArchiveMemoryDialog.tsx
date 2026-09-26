import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { Modal } from '../components/Modal';

/**
 * Confirmation before archiving a Memory (SCREEN-013): it disappears for the whole Family. When
 * the Memory changed since it was shown, the User reloads it before deciding again (SCREEN-012).
 */
export function ArchiveMemoryDialog({
  pending,
  error,
  onCancel,
  onConfirm,
  onReload,
}: {
  pending: boolean;
  error: unknown;
  onCancel: () => void;
  onConfirm: () => void;
  onReload: () => Promise<void>;
}) {
  const { t, i18n } = useTranslation('memory');
  const isConflict = error instanceof ApiError && error.code === 'CONCURRENT_MODIFICATION';
  return (
    <Modal message={t('archive.warning')} onClose={onCancel}>
      {isConflict ? (
        <div role="alert" className="flex flex-col gap-3">
          <p className="text-body text-text">{t('edit.conflict')}</p>
          <Button variant="secondary" onClick={() => void onReload()}>
            {t('edit.reload')}
          </Button>
        </div>
      ) : (
        error != null && (
          <p role="alert" className="text-body text-text">
            {errorMessage(i18n, error)}
          </p>
        )
      )}
      <div className="flex flex-col gap-3 sm:flex-row-reverse">
        <Button disabled={pending || isConflict} onClick={onConfirm} className="sm:w-auto">
          {t('archive.confirm')}
        </Button>
        <Button variant="secondary" onClick={onCancel} className="sm:w-auto">
          {t('archive.cancel')}
        </Button>
      </div>
    </Modal>
  );
}
