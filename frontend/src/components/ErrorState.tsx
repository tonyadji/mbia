import { useTranslation } from 'react-i18next';
import { errorMessage } from '../api/errorMessage';
import { i18n } from '../i18n';
import { Button } from './Button';

/** A failed load: the translated error message and a way to try again. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <p role="alert">{errorMessage(i18n, error)}</p>
      <Button variant="secondary" onClick={onRetry}>
        {t('actions.retry')}
      </Button>
    </div>
  );
}
