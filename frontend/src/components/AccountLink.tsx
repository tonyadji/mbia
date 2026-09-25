import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useCurrentUser } from '../auth/useCurrentUser';
import { SETTINGS_PATH } from '../pages/AccountSettingsPage';
import { Avatar } from './Avatar';

/** The current User's avatar, opening account settings (SCREEN-011). */
export function AccountLink() {
  const { t } = useTranslation('settings');
  const currentUser = useCurrentUser().data;
  if (currentUser === undefined) return null;

  return (
    <Link
      to={SETTINGS_PATH}
      aria-label={t('open')}
      className="shrink-0 rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Avatar displayName={currentUser.displayName} email={currentUser.email} />
    </Link>
  );
}
