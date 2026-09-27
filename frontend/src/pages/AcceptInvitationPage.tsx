import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { useAuth } from '../auth/AuthProvider';
import { Button } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Logo } from '../components/Logo';
import { Skeleton } from '../components/Skeleton';
import { formatDate } from '../i18n/formatDate';
import { DEFAULT_LANGUAGE, isSupportedLanguage } from '../i18n/language';
import {
  forgetInvitation,
  invitationPath,
  rememberInvitation,
} from '../invitations/pendingInvitation';
import { useAcceptInvitation, useInvitationPreview } from '../invitations/useInvitation';
import { familyHomePath } from './FamilyHomePage';
import { joinFamilyPath } from './JoinFamilyPage';

const INVALID_CODES = [
  'INVITATION_EXPIRED',
  'INVITATION_REVOKED',
  'INVITATION_ALREADY_USED',
  'INVITATION_NOT_FOUND',
] as const;
type InvalidCode = (typeof INVALID_CODES)[number];

/** Expired, revoked, used or unknown (a renewed link answers 404, OQ-058): the link is no longer valid. */
function invalidCode(error: unknown): InvalidCode | null {
  if (!(error instanceof ApiError)) return null;
  const code = INVALID_CODES.find((known) => known === error.code);
  if (code) return code;
  return error.status === 404 || error.status === 410 ? 'INVITATION_NOT_FOUND' : null;
}

/**
 * SCREEN-010 — Accept Invitation (public preview, authenticated acceptance): the Family, who
 * invites and the permission offered, never the Person (OQ-050). Signed out, `Join the family` goes
 * to Keycloak sign-in (with its sign-up option) and back here, where the invitation is accepted
 * without another tap. The browser keeps the invitation until it is accepted or no longer valid
 * (mvp.md §18).
 */
export function AcceptInvitationPage() {
  const { t, i18n } = useTranslation(['invitation', 'auth']);
  const { token = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const { user, isLoading, signIn, signOut } = useAuth();
  const navigate = useNavigate();
  const preview = useInvitationPreview(token);
  const accept = useAcceptInvitation(token);
  const autoJoin = params.get('join') === '1';
  const autoJoined = useRef(false);
  const invalid = invalidCode(preview.error) ?? invalidCode(accept.error);

  useEffect(() => {
    if (preview.isSuccess) rememberInvitation(token);
  }, [preview.isSuccess, token]);

  useEffect(() => {
    if (invalid !== null) forgetInvitation(token);
  }, [invalid, token]);

  function join() {
    if (user === null) {
      rememberInvitation(token);
      void signIn(invitationPath(token, { join: true }));
      return;
    }
    accept.mutate(undefined, {
      onSuccess: (result) => {
        forgetInvitation(token);
        // An ACTIVE member is simply taken to the Family; the invitation stays pending (mvp.md §18).
        void navigate(
          result.alreadyMember
            ? familyHomePath(result.family.id)
            : joinFamilyPath(result.family.id, result.suggestedPerson?.id),
          { replace: true },
        );
      },
    });
  }

  // Back from Keycloak: the User already chose to join.
  useEffect(() => {
    if (!autoJoin || isLoading || user === null || !preview.isSuccess || autoJoined.current) return;
    autoJoined.current = true;
    setParams({}, { replace: true });
    join();
  });

  let content;
  if (invalid !== null) {
    content = (
      <div className="flex flex-1 flex-col gap-2">
        <h1 className="text-display text-text">{t(`join.invalid.${invalid}`)}</h1>
        <p className="text-body text-text-muted">{t('join.invalid.askAgain')}</p>
      </div>
    );
  } else if (accept.error instanceof ApiError && accept.error.code === 'EMAIL_NOT_VERIFIED') {
    content = (
      <>
        <div className="flex flex-1 flex-col gap-2">
          <h1 className="text-display text-text">{t('auth:emailNotVerified.title')}</h1>
          <p className="text-body text-text-muted">{t('auth:emailNotVerified.body')}</p>
        </div>
        <Button variant="secondary" onClick={() => void signOut()}>
          {t('auth:signOut')}
        </Button>
      </>
    );
  } else if (preview.isError) {
    content = <ErrorState error={preview.error} onRetry={() => void preview.refetch()} />;
  } else if (preview.isPending) {
    content = (
      <div aria-busy="true" className="flex flex-col gap-3">
        <Skeleton className="h-9 w-64" />
        <Skeleton className="h-16" />
        <Skeleton className="h-12" />
      </div>
    );
  } else {
    const invitation = preview.data;
    const joining = accept.isPending || (autoJoin && user !== null);
    content = (
      <>
        <div className="flex flex-1 flex-col gap-4">
          <h1 className="text-display break-words text-text">
            {t('join.title', { family: invitation.familyName })}
          </h1>
          <p className="text-body break-words text-text">
            {t('join.invitedBy', { name: invitation.invitedByDisplayName })}
          </p>
          <dl className="flex flex-col gap-1 rounded-xl border border-border bg-surface px-4 py-3">
            <dt className="text-caption text-text-muted">{t('join.permission')}</dt>
            <dd className="text-body font-semibold text-text">
              {t(`invite.roles.${invitation.role}`)}
            </dd>
            <dd className="text-caption text-text-muted">
              {t(`invite.roleHelp.${invitation.role}`)}
            </dd>
          </dl>
          <p className="text-caption text-text-muted">
            {t('join.expires', {
              date: formatDate(
                new Date(invitation.expiresAt),
                isSupportedLanguage(i18n.resolvedLanguage)
                  ? i18n.resolvedLanguage
                  : DEFAULT_LANGUAGE,
              ),
            })}
          </p>
        </div>
        {accept.isError && (
          <p role="alert" className="text-body text-text">
            {errorMessage(i18n, accept.error)}
          </p>
        )}
        <Button disabled={joining || isLoading} onClick={join}>
          {t('join.submit')}
        </Button>
      </>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6">
      <header>
        <Logo />
      </header>
      {content}
    </div>
  );
}
