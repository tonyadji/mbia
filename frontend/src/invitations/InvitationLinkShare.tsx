import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useCurrentUser } from '../auth/useCurrentUser';
import { Button } from '../components/Button';
import type { CreatedInvitation } from './useFamilyInvitations';

type CopyState = 'copied' | 'failed' | null;

/**
 * SCREEN-009 success for `Share a link`, also shown after `Renew`: the link with `Copy link` and
 * `Share` (the device share sheet, when there is one), the pre-filled message naming the inviter
 * and the Person, and why the link must be sent now (mvp.md §18). The link is only in memory: it
 * cannot be displayed again.
 */
export function InvitationLinkShare({
  invitation,
  familyName,
}: {
  invitation: CreatedInvitation;
  familyName: string;
}) {
  const { t } = useTranslation('invitation');
  const currentUser = useCurrentUser();
  const [copy, setCopy] = useState<CopyState>(null);
  const link = invitation.inviteUrl;
  const values = {
    inviter:
      invitation.invitedBy.displayName ??
      currentUser.data?.displayName ??
      currentUser.data?.email ??
      '',
    family: familyName,
    link,
  };
  const message = invitation.person
    ? t('share.withPerson', { ...values, person: invitation.person.displayName })
    : t('share.withoutPerson', values);
  const canShare = typeof navigator.share === 'function';

  return (
    <section aria-labelledby="invitation-link" className="flex flex-col gap-4">
      <h2 id="invitation-link" className="text-section text-text">
        {t('share.done')}
      </h2>
      <div className="flex flex-col gap-1">
        <h3 className="text-caption font-semibold text-text-muted">{t('share.link')}</h3>
        <p className="rounded-xl border border-border bg-surface px-4 py-3 text-body break-all text-text select-all">
          {link}
        </p>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row">
        <Button
          variant={canShare ? 'secondary' : 'primary'}
          className="sm:w-auto"
          onClick={() => {
            navigator.clipboard.writeText(link).then(
              () => {
                setCopy('copied');
              },
              () => {
                setCopy('failed');
              },
            );
          }}
        >
          {t('share.copy')}
        </Button>
        {canShare && (
          <Button
            className="sm:w-auto"
            onClick={() => {
              // A share cancelled by the User is not an error.
              navigator.share({ text: message }).catch(() => undefined);
            }}
          >
            {t('share.share')}
          </Button>
        )}
      </div>
      {copy && (
        <p role="status" className="text-caption text-text-muted">
          {t(copy === 'copied' ? 'share.copied' : 'share.copyFailed')}
        </p>
      )}
      <div className="flex flex-col gap-1">
        <h3 className="text-caption font-semibold text-text-muted">{t('share.message')}</h3>
        <p className="rounded-xl border border-border bg-surface px-4 py-3 text-body break-words text-text">
          {message}
        </p>
      </div>
      <p className="text-body text-text">{t('share.once')}</p>
      <p className="text-caption text-text-muted">{t('share.notShownAgain')}</p>
    </section>
  );
}
