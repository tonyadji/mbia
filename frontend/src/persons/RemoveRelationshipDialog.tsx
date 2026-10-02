import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { Modal } from '../components/Modal';

/**
 * SCREEN-COMPONENT-003 — Remove Relationship Confirmation: removing a link may change the
 * relationships Mbia calculates (person-relationships-collaboration.md §8).
 */
export function RemoveRelationshipDialog({
  pending,
  error,
  onCancel,
  onConfirm,
}: {
  pending: boolean;
  error: unknown;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { t, i18n } = useTranslation('person');
  return (
    <Modal message={t('removeLink.warning')} onClose={onCancel}>
      {error != null && (
        <p role="alert" className="text-body text-text">
          {errorMessage(i18n, error)}
        </p>
      )}
      <div className="flex flex-col gap-3 sm:flex-row-reverse">
        <Button disabled={pending} onClick={onConfirm} className="sm:w-auto">
          {t('removeLink.confirm')}
        </Button>
        <Button variant="secondary" onClick={onCancel} className="sm:w-auto">
          {t('removeLink.cancel')}
        </Button>
      </div>
    </Modal>
  );
}
