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
      // The 40 px avatar sits in a 48 px touch target (design-guidelines.md §9).
      className="inline-flex size-12 shrink-0 items-center justify-center rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Avatar displayName={currentUser.displayName} email={currentUser.email} />
    </Link>
  );
}
