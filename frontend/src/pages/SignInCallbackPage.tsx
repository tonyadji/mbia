import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { Button } from '../components/Button';

/** Where Keycloak sends the browser back after sign-in or sign-up. */
export function SignInCallbackPage() {
  const { t } = useTranslation('auth');
  const { completeSignIn } = useAuth();
  const navigate = useNavigate();
  const [failed, setFailed] = useState(false);
  const started = useRef(false);

  useEffect(() => {
    // The authorization code is single-use: complete it once, even when effects run twice.
    if (started.current) return;
    started.current = true;
    completeSignIn()
      .then((returnTo) => navigate(returnTo, { replace: true }))
      .catch(() => {
        setFailed(true);
      });
  }, [completeSignIn, navigate]);

  if (!failed) return null;
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <p role="alert">{t('callback.failed')}</p>
      <Button variant="secondary" onClick={() => void navigate('/', { replace: true })}>
        {t('callback.backToWelcome')}
      </Button>
    </div>
  );
}
