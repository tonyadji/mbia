import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet, useLocation } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { i18n } from '../i18n';
import { EmailNotVerifiedPage } from '../pages/EmailNotVerifiedPage';
import { useAuth } from './AuthProvider';
import { useCurrentUser } from './useCurrentUser';

/** Pages for signed-in Users only: signs in first, then loads the Mbia User and its language. */
export function ProtectedRoute() {
  const { user, isLoading, isSigningOut, signIn } = useAuth();
  const { pathname, search } = useLocation();
  const mustSignIn = !isLoading && !isSigningOut && user === null;

  useEffect(() => {
    if (mustSignIn) void signIn(pathname + search);
  }, [mustSignIn, signIn, pathname, search]);

  return user === null ? null : <CurrentUserGate />;
}

function CurrentUserGate() {
  const { t } = useTranslation();
  const currentUser = useCurrentUser();
  const preferredLocale = currentUser.data?.preferredLocale;

  // The stored preference wins over the browser language (localization-and-kinship-labels.md §1).
  useEffect(() => {
    if (preferredLocale) void i18n.changeLanguage(preferredLocale);
  }, [preferredLocale]);

  if (currentUser.isPending) return null;
  if (currentUser.isError) {
    const { error } = currentUser;
    if (error instanceof ApiError && error.code === 'EMAIL_NOT_VERIFIED') {
      return <EmailNotVerifiedPage />;
    }
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
        <p role="alert">{errorMessage(i18n, error)}</p>
        <Button variant="secondary" onClick={() => void currentUser.refetch()}>
          {t('actions.retry')}
        </Button>
      </div>
    );
  }
  return <Outlet />;
}
