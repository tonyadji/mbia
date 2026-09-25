import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { CREATE_FAMILY_PATH, HOME_PATH, useAuth } from '../auth/AuthProvider';
import { Button } from '../components/Button';
import { Logo } from '../components/Logo';

/** SCREEN-001 — Welcome (public). */
export function WelcomePage() {
  const { t } = useTranslation('auth');
  const { user, signIn, signUp } = useAuth();
  const navigate = useNavigate();
  const signedIn = user !== null;

  return (
    <div className="flex flex-1 flex-col gap-6">
      <header>
        <Logo />
      </header>
      {/* Neutral placeholder for the family visual. */}
      <div aria-hidden="true" className="min-h-40 flex-1 rounded-3xl bg-accent/20" />
      <div className="flex flex-col gap-2">
        <h1 className="text-display text-text">{t('welcome.title')}</h1>
        <p className="text-body text-text-muted">{t('welcome.valueProposition')}</p>
      </div>
      <div className="flex flex-col gap-3">
        <Button onClick={() => (signedIn ? void navigate(CREATE_FAMILY_PATH) : void signUp())}>
          {t('welcome.createFamily')}
        </Button>
        <Button
          variant="secondary"
          onClick={() => (signedIn ? void navigate(HOME_PATH) : void signIn())}
        >
          {t('welcome.signIn')}
        </Button>
      </div>
    </div>
  );
}
