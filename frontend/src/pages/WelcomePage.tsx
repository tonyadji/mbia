import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate } from 'react-router';
import { CREATE_FAMILY_PATH, HOME_PATH, useAuth } from '../auth/AuthProvider';
import { Button } from '../components/Button';
import { Logo } from '../components/Logo';
import welcomeFamily from '../assets/welcome-family.webp';
import { invitationPath, pendingInvitation } from '../invitations/pendingInvitation';

/**
 * SCREEN-001 — Welcome (public). While an invitation is pending in this browser, it leads back to
 * it: the email verification page of Keycloak sends the User here (mvp.md §18, OQ-050).
 */
export function WelcomePage() {
  const { t } = useTranslation('auth');
  const { user, signIn, signUp } = useAuth();
  const navigate = useNavigate();
  const signedIn = user !== null;
  const invitation = pendingInvitation();

  if (invitation !== null) return <Navigate to={invitationPath(invitation)} replace />;

  return (
    <div className="flex flex-1 flex-col gap-6">
      <header>
        <Logo />
      </header>
      {/* The family visual (OQ-076): decorative, the title says what Mbia is. Cropped to fill the frame,
          centred on the two faces. */}
      <div className="relative min-h-40 flex-1 overflow-hidden rounded-3xl bg-accent/20">
        <img
          src={welcomeFamily}
          alt=""
          width={2048}
          height={768}
          fetchPriority="high"
          decoding="async"
          className="absolute inset-0 h-full w-full object-cover object-[63%_35%]"
        />
      </div>
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
