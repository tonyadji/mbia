import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthProvider';
import { Button } from '../components/Button';
import { Logo } from '../components/Logo';

/** Shown when the API answers 403 EMAIL_NOT_VERIFIED (mvp.md §21). */
export function EmailNotVerifiedPage() {
  const { t } = useTranslation('auth');
  const { signOut } = useAuth();

  return (
    <div className="flex flex-1 flex-col gap-6">
      <header>
        <Logo />
      </header>
      <div className="flex flex-1 flex-col gap-2">
        <h1 className="text-display text-text">{t('emailNotVerified.title')}</h1>
        <p className="text-body text-text-muted">{t('emailNotVerified.body')}</p>
      </div>
      <Button variant="secondary" onClick={() => void signOut()}>
        {t('signOut')}
      </Button>
    </div>
  );
}
