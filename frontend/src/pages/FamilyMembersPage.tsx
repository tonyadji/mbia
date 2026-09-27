import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import { HOME_PATH } from '../auth/AuthProvider';
import { useCurrentUser } from '../auth/useCurrentUser';
import { AccountLink } from '../components/AccountLink';
import { Button, buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Modal } from '../components/Modal';
import { NavigationBar } from '../components/NavigationBar';
import { Select } from '../components/Select';
import { Skeleton } from '../components/Skeleton';
import { familyQueryKey, useFamily } from '../families/useFamily';
import { familiesQueryKey, useMyFamilies } from '../families/useMyFamilies';
import { formatDate } from '../i18n/formatDate';
import { DEFAULT_LANGUAGE, isSupportedLanguage } from '../i18n/language';
import { InvitationEmailResult } from '../invitations/InvitationEmailResult';
import { InvitationLinkShare } from '../invitations/InvitationLinkShare';
import { useFamilyInvitations, type Invitation } from '../invitations/useFamilyInvitations';
import { useRenewInvitation } from '../invitations/useRenewInvitation';
import { useRevokeInvitation } from '../invitations/useRevokeInvitation';
import { membersQueryKey, useFamilyMembers, type Member } from '../members/useFamilyMembers';
import { useRemoveMember } from '../members/useRemoveMember';
import { useUpdateMemberRole } from '../members/useUpdateMemberRole';
import { kinshipLabel } from '../persons/kinship';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { invitePath } from './InviteMemberPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** The refusals of a member action that are explained with the member's name. */
const MEMBER_REFUSALS = ['CONCURRENT_MODIFICATION', 'RESOURCE_NOT_FOUND'] as const;
/** The refusals of `Renew` and `Revoke` explained on the invitation (OQ-057). */
const INVITATION_REFUSALS = [
  'CONCURRENT_MODIFICATION',
  'INVITATION_ALREADY_USED',
  'INVITATION_REVOKED',
] as const;

export function familyMembersPath(familyId: string) {
  return `/families/${familyId}/members`;
}

/**
 * SCREEN-008 — Family Members, the `Members` tab (family-tree-ux.md §4): the ACTIVE members with
 * their kinship to the current User or their linked Person, and their role. The ADMIN invites,
 * follows the pending invitations (`Renew`, `Revoke`), changes roles and removes members; any other
 * member can leave (mvp.md §5). Every decision is the backend's; this screen only offers the
 * actions of the caller's role.
 */
export function FamilyMembersPage() {
  const { t } = useTranslation('family');
  const { familyId = '' } = useParams();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });
  const members = useFamilyMembers(familyId, { enabled: isValidId });
  const me = useCurrentUser();
  const isAdmin = family.data?.myRole === 'ADMIN';

  const notFound = [family.error, members.error].some(
    (error) => error instanceof ApiError && error.status === 404,
  );
  if (!isValidId || notFound) {
    return <FamilyNotFoundPage />;
  }

  let content;
  if (family.isError || members.isError) {
    const retry = () => {
      if (family.isError) void family.refetch();
      if (members.isError) void members.refetch();
    };
    content = <ErrorState error={family.error ?? members.error} onRetry={retry} />;
  } else if (family.isPending || members.isPending || me.data === undefined) {
    content = (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    );
  } else {
    const myId = me.data.id;
    const myMembership = members.data.find((member) => member.userId === myId);
    content = (
      <>
        {isAdmin && (
          <Link
            to={invitePath(familyId)}
            className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
          >
            {t('members.invite')}
          </Link>
        )}
        <ul aria-labelledby="family-members" className="flex flex-col gap-3">
          {members.data.map((member) => (
            <li key={member.id}>
              <MemberRow
                familyId={familyId}
                member={member}
                isMe={member.userId === myId}
                canManage={isAdmin && member.userId !== myId}
              />
            </li>
          ))}
        </ul>
        {isAdmin && <PendingInvitations familyId={familyId} familyName={family.data.name} />}
        {isAdmin ? (
          <p className="text-body text-text-muted">{t('members.onlyAdmin')}</p>
        ) : (
          myMembership && <LeaveFamily familyId={familyId} membership={myMembership} />
        )}
      </>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 pb-24">
      <header className="flex items-center gap-3">
        <h1 id="family-members" className="text-display min-w-0 flex-1 text-text">
          {t('members.title')}
        </h1>
        <AccountLink />
      </header>
      {content}
      <NavigationBar familyId={familyId} />
    </div>
  );
}

function nameOf(member: Member, unnamed: string) {
  return member.displayName ?? member.linkedPersonDisplayName ?? unnamed;
}

/**
 * One member: their name, then "You", their kinship to the current User when both have a linked
 * Person, or else the name of their linked Person (OQ-050); their role; and, for the ADMIN on
 * another member, the role choice and `Remove` (mvp.md §5). Never their email (OQ-061).
 */
function MemberRow({
  familyId,
  member,
  isMe,
  canManage,
}: {
  familyId: string;
  member: Member;
  isMe: boolean;
  canManage: boolean;
}) {
  const { t, i18n } = useTranslation('family');
  const { t: tPerson } = useTranslation('person');
  const { t: tInvitation } = useTranslation('invitation');
  const queryClient = useQueryClient();
  const updateRole = useUpdateMemberRole(familyId);
  const remove = useRemoveMember(familyId);
  const [confirming, setConfirming] = useState(false);
  const name = nameOf(member, t('members.unnamed'));
  // The only ADMIN is the caller (mvp.md §4): the role of an ADMIN is never offered.
  const managed = canManage && member.role !== 'ADMIN';

  // Gender is not in the member list: FIRST_COUSIN takes its neutral form (OQ-062).
  const kinship =
    member.relationshipToCurrentUser != null
      ? kinshipLabel(tPerson, member.relationshipToCurrentUser, 'UNKNOWN')
      : null;
  const detail = isMe
    ? tPerson('kinship.label.SELF')
    : (kinship ??
      (member.linkedPersonDisplayName != null
        ? t('members.linkedTo', { name: member.linkedPersonDisplayName })
        : null));
  const roleLabel =
    member.role === 'ADMIN' ? t('members.roles.ADMIN') : tInvitation(`invite.roles.${member.role}`);

  const refusal = (error: unknown) =>
    error instanceof ApiError && MEMBER_REFUSALS.some((code) => code === error.code)
      ? t(`members.refused.${error.code as (typeof MEMBER_REFUSALS)[number]}`, { name })
      : errorMessage(i18n, error);

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-surface px-4 py-3">
      <div className="flex flex-col gap-1">
        <p className="text-body font-semibold break-words text-text">{name}</p>
        {detail && <p className="text-caption break-words text-text-muted">{detail}</p>}
        {!managed && <p className="text-caption text-text">{roleLabel}</p>}
      </div>
      {managed && (
        <>
          <Select
            label={t('members.roleOf', { name })}
            value={member.role}
            disabled={updateRole.isPending}
            options={(['CONTRIBUTOR', 'VIEWER'] as const).map((role) => ({
              value: role,
              label: tInvitation(`invite.roles.${role}`),
            }))}
            onChange={(event) => {
              updateRole.mutate({
                memberId: member.id,
                version: member.version,
                role: event.target.value as 'CONTRIBUTOR' | 'VIEWER',
              });
            }}
          />
          {updateRole.isError && (
            <p role="alert" className="text-body text-text">
              {refusal(updateRole.error)}
            </p>
          )}
          <Button
            variant="secondary"
            className="sm:w-auto sm:self-start"
            aria-label={t('members.removeName', { name })}
            onClick={() => {
              remove.reset();
              setConfirming(true);
            }}
          >
            {t('members.remove')}
          </Button>
        </>
      )}
      {confirming && (
        <Modal
          message={t('members.removeConfirm', { name })}
          onClose={() => {
            setConfirming(false);
          }}
        >
          <p className="text-body text-text">{t('members.contributionsStay', { name })}</p>
          {remove.isError && (
            <p role="alert" className="text-body text-text">
              {refusal(remove.error)}
            </p>
          )}
          <div className="flex flex-col gap-3 sm:flex-row-reverse">
            <Button
              className="sm:w-auto"
              disabled={remove.isPending || remove.isError}
              onClick={() => {
                remove.mutate(
                  { memberId: member.id, version: member.version },
                  {
                    onSuccess: () => {
                      setConfirming(false);
                    },
                    // The released Person changes the tree too.
                    onSettled: () => {
                      void queryClient.invalidateQueries({ queryKey: familyQueryKey(familyId) });
                    },
                  },
                );
              }}
            >
              {t('members.removeConfirmAction')}
            </Button>
            <Button
              variant="secondary"
              className="sm:w-auto"
              onClick={() => {
                setConfirming(false);
              }}
            >
              {t('members.cancel')}
            </Button>
          </div>
        </Modal>
      )}
    </div>
  );
}

/**
 * The ADMIN's pending invitations: for whom (the Person while it is ACTIVE, OQ-056; otherwise the
 * email or "Shared link"), role, expiry, an email that could not be sent (OQ-055), `Renew` and
 * `Revoke`. Hidden when there is none.
 */
function PendingInvitations({ familyId, familyName }: { familyId: string; familyName: string }) {
  const { t } = useTranslation('family');
  const invitations = useFamilyInvitations(familyId);
  // A secondary section: nothing is shown while it loads or when it cannot be loaded.
  const pending = invitations.data?.filter((invitation) => invitation.status === 'PENDING') ?? [];
  if (pending.length === 0) return null;

  return (
    <section aria-labelledby="pending-invitations" className="flex flex-col gap-3">
      <h2 id="pending-invitations" className="text-section text-text">
        {t('members.invitations.title')}
      </h2>
      <ul className="flex flex-col gap-3">
        {pending.map((invitation) => (
          <li key={invitation.id}>
            <InvitationRow familyId={familyId} familyName={familyName} invitation={invitation} />
          </li>
        ))}
      </ul>
    </section>
  );
}

function InvitationRow({
  familyId,
  familyName,
  invitation,
}: {
  familyId: string;
  familyName: string;
  invitation: Invitation;
}) {
  const { t, i18n } = useTranslation('family');
  const { t: tInvitation } = useTranslation('invitation');
  const renew = useRenewInvitation(familyId);
  const revoke = useRevokeInvitation(familyId);
  const [confirmingRevoke, setConfirmingRevoke] = useState(false);
  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  const target = invitation.person
    ? t('members.invitations.for', { name: invitation.person.displayName })
    : (invitation.email ?? t('members.invitations.sharedLink'));

  const refusal = (error: unknown) =>
    error instanceof ApiError && INVITATION_REFUSALS.some((code) => code === error.code)
      ? t(`members.invitations.refused.${error.code as (typeof INVITATION_REFUSALS)[number]}`)
      : errorMessage(i18n, error);
  const error = renew.error ?? revoke.error;

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-surface px-4 py-3">
      <div className="flex flex-col gap-1">
        <p className="text-body font-semibold break-words text-text">{target}</p>
        <p className="text-caption text-text">{tInvitation(`invite.roles.${invitation.role}`)}</p>
        <p className="text-caption text-text-muted">
          {tInvitation('profile.expires', {
            date: formatDate(new Date(invitation.expiresAt), language),
          })}
        </p>
        {invitation.emailDelivery === 'FAILED' && (
          <p className="text-body text-text">{tInvitation('email.failed')}</p>
        )}
      </div>
      <div className="flex flex-col gap-3 sm:flex-row">
        <Button
          variant="secondary"
          className="sm:w-auto"
          disabled={renew.isPending}
          aria-label={t('members.invitations.renewFor', { target })}
          onClick={() => {
            revoke.reset();
            renew.mutate({ invitationId: invitation.id, version: invitation.version });
          }}
        >
          {tInvitation('profile.renew')}
        </Button>
        <Button
          variant="secondary"
          className="sm:w-auto"
          aria-label={t('members.invitations.revokeFor', { target })}
          onClick={() => {
            renew.reset();
            revoke.reset();
            setConfirmingRevoke(true);
          }}
        >
          {t('members.invitations.revoke')}
        </Button>
      </div>
      {error != null && !confirmingRevoke && (
        <p role="alert" className="text-body text-text">
          {refusal(error)}
        </p>
      )}
      {renew.isSuccess && (
        // The new link exists only here: once this is closed, it cannot be shown again.
        <Modal message={target} size="lg" onClose={renew.reset}>
          {renew.data.channel === 'EMAIL' ? (
            <InvitationEmailResult invitation={renew.data} />
          ) : (
            <>
              <p role="status" className="text-body text-text">
                {tInvitation('profile.renewed')}
              </p>
              <InvitationLinkShare invitation={renew.data} familyName={familyName} />
            </>
          )}
          <Button variant="secondary" className="sm:w-auto sm:self-end" onClick={renew.reset}>
            {t('members.close')}
          </Button>
        </Modal>
      )}
      {confirmingRevoke && (
        <Modal
          message={t('members.invitations.revokeConfirm', { target })}
          onClose={() => {
            setConfirmingRevoke(false);
          }}
        >
          <p className="text-body text-text">{t('members.invitations.revokeHelp')}</p>
          {revoke.isError && (
            <p role="alert" className="text-body text-text">
              {refusal(revoke.error)}
            </p>
          )}
          <div className="flex flex-col gap-3 sm:flex-row-reverse">
            <Button
              className="sm:w-auto"
              disabled={revoke.isPending || revoke.isError}
              onClick={() => {
                revoke.mutate(
                  { invitationId: invitation.id, version: invitation.version },
                  {
                    onSuccess: () => {
                      setConfirmingRevoke(false);
                    },
                  },
                );
              }}
            >
              {t('members.invitations.revokeAction')}
            </Button>
            <Button
              variant="secondary"
              className="sm:w-auto"
              onClick={() => {
                setConfirmingRevoke(false);
              }}
            >
              {t('members.cancel')}
            </Button>
          </div>
        </Modal>
      )}
    </div>
  );
}

/**
 * `Leave this family` for a CONTRIBUTOR or VIEWER, after a confirmation (mvp.md §5). Once gone, the
 * User lands where a signed-in User lands (`HOME_PATH`): their other Family, the chooser, or Family
 * creation.
 */
function LeaveFamily({ familyId, membership }: { familyId: string; membership: Member }) {
  const { t, i18n } = useTranslation('family');
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const leave = useRemoveMember(familyId);
  const [confirming, setConfirming] = useState(false);

  return (
    <>
      <Button
        variant="secondary"
        className="sm:w-auto sm:self-start"
        onClick={() => {
          leave.reset();
          setConfirming(true);
        }}
      >
        {t('members.leave')}
      </Button>
      {confirming && (
        <Modal
          message={t('members.leaveConfirm')}
          onClose={() => {
            setConfirming(false);
          }}
        >
          <p className="text-body text-text">{t('members.leaveHelp')}</p>
          {leave.isError && (
            <p role="alert" className="text-body text-text">
              {errorMessage(i18n, leave.error)}
            </p>
          )}
          <div className="flex flex-col gap-3 sm:flex-row-reverse">
            <Button
              className="sm:w-auto"
              disabled={leave.isPending}
              onClick={() => {
                leave.mutate(
                  { memberId: membership.id, version: membership.version },
                  {
                    onSuccess: () => {
                      // The Family leaves the list before the landing reads it.
                      queryClient.setQueryData(
                        familiesQueryKey,
                        (families: ReturnType<typeof useMyFamilies>['data']) =>
                          families?.filter((family) => family.id !== familyId),
                      );
                      void Promise.resolve(navigate(HOME_PATH, { replace: true })).then(() => {
                        queryClient.removeQueries({ queryKey: familyQueryKey(familyId) });
                        return queryClient.invalidateQueries({
                          queryKey: familiesQueryKey,
                          exact: true,
                        });
                      });
                    },
                    onError: () => {
                      void queryClient.invalidateQueries({ queryKey: membersQueryKey(familyId) });
                    },
                  },
                );
              }}
            >
              {t('members.leaveAction')}
            </Button>
            <Button
              variant="secondary"
              className="sm:w-auto"
              onClick={() => {
                setConfirming(false);
              }}
            >
              {t('members.cancel')}
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}
