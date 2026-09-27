import { useTranslation } from 'react-i18next';
import type { CreatedInvitation } from './useFamilyInvitations';

/**
 * SCREEN-009 success for `Send by email`, also shown after `Renew` of an email invitation:
 * `Invitation sent`, or "The email could not be sent" when the mail provider refused it; the
 * invitation exists in both cases, and `Renew` tries again (mvp.md §18, OQ-055).
 */
export function InvitationEmailResult({ invitation }: { invitation: CreatedInvitation }) {
  const { t } = useTranslation('invitation');
  const failed = invitation.emailDelivery === 'FAILED';

  return (
    <div role="status" className="flex flex-col gap-2">
      <h2 className="text-section text-text">{failed ? t('email.failed') : t('email.sent')}</h2>
      <p className="text-body break-words text-text">
        {failed ? t('email.failedHelp') : t('email.sentTo', { email: invitation.email ?? '' })}
      </p>
    </div>
  );
}
