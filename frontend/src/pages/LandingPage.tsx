import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthProvider';
import { useCurrentUser } from '../auth/useCurrentUser';
import { Button } from '../components/Button';
import { Logo } from '../components/Logo';

/** Temporary signed-in landing page, replaced by the Family screens in PR-15. */
export function LandingPage() {
  const { t } = useTranslation('auth');
  const { signOut } = useAuth();
  const displayName = useCurrentUser().data?.displayName;

  return (
    <div className="flex flex-1 flex-col gap-6">
      <header>
        <Logo />
      </header>
      <h1 className="flex-1 text-display text-text">
        {displayName ? t('landing.greeting', { name: displayName }) : t('landing.greetingNoName')}
      </h1>
      <Button variant="secondary" onClick={() => void signOut()}>
        {t('signOut')}
      </Button>
    </div>
  );
}
