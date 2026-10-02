import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { Modal } from '../components/Modal';

/**
 * Confirmation before an ADMIN archives a Person (SCREEN-005): the Person leaves the tree and
 * search, and can be restored (mvp.md §13). A linked Person is refused and explained (OQ-023).
 */
export function ArchivePersonDialog({
  name,
  pending,
  error,
  onCancel,
  onConfirm,
}: {
  name: string;
  pending: boolean;
  error: unknown;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { t, i18n } = useTranslation('person');
  const refusal =
    error instanceof ApiError && error.code === 'PERSON_ALREADY_CLAIMED'
      ? t('archive.refused.PERSON_ALREADY_CLAIMED')
      : error != null
        ? errorMessage(i18n, error)
        : null;
  return (
    <Modal message={t('archive.warning', { name })} onClose={onCancel}>
      {refusal && (
        <p role="alert" className="text-body text-text">
          {refusal}
        </p>
      )}
      <div className="flex flex-col gap-3 sm:flex-row-reverse">
        <Button disabled={pending} onClick={onConfirm} className="sm:w-auto">
          {t('archive.confirm')}
        </Button>
        <Button variant="secondary" onClick={onCancel} className="sm:w-auto">
          {t('archive.cancel')}
        </Button>
      </div>
    </Modal>
  );
}
