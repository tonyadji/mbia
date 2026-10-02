import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router';
import { ApiError } from '../api/client';
import { ErrorState } from '../components/ErrorState';
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
    return <ErrorState error={error} onRetry={() => void currentUser.refetch()} />;
  }
  return <Outlet />;
}
