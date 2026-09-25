import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useCurrentUser } from '../auth/useCurrentUser';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { Logo } from '../components/Logo';
import { SETTINGS_PATH } from './AccountSettingsPage';

/** Temporary signed-in landing page, replaced by the Family screens in PR-15. */
export function LandingPage() {
  const { t } = useTranslation(['auth', 'settings']);
  const { signOut } = useAuth();
  const currentUser = useCurrentUser().data;
  const displayName = currentUser?.displayName;

  return (
    <div className="flex flex-1 flex-col gap-6">
      <header className="flex items-center justify-between gap-4">
        <Logo />
        {currentUser && (
          <Link
            to={SETTINGS_PATH}
            aria-label={t('settings:open')}
            className="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <Avatar displayName={displayName} email={currentUser.email} />
          </Link>
        )}
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
