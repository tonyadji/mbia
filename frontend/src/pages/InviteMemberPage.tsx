import { useId, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { errorMessage } from '../api/errorMessage';
import type { components } from '../api/generated/schema';
import { Button } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Select } from '../components/Select';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { InvitationLinkShare } from '../invitations/InvitationLinkShare';
import { inviteBlocker, type InviteBlocker } from '../invitations/invitable';
import { useCreateInvitation } from '../invitations/useCreateInvitation';
import type { CreatedInvitation } from '../invitations/useFamilyInvitations';
import { usePerson, type Person } from '../persons/usePerson';
import { familyHomePath } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { displayNameOf, personPath } from './PersonProfilePage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type InvitationRole = components['schemas']['InvitationRole'];

const ROLES = ['CONTRIBUTOR', 'VIEWER'] as const satisfies readonly InvitationRole[];

/** SCREEN-009, for a Person of the tree (`personId`, from their profile) or for anyone. */
export function invitePath(familyId: string, { personId }: { personId?: string } = {}) {
  return `/families/${familyId}/invitations/new${personId ? `?person=${personId}` : ''}`;
}

/**
 * SCREEN-009 — Invite Member, ADMIN only (OQ-051), channel `Share a link` (the email channel
 * comes with PR-51). From a profile, the invitation carries the Person, named in the title (OQ-050).
 */
export function InviteMemberPage() {
  const { t } = useTranslation(['invitation', 'settings']);
  const { familyId = '' } = useParams();
  const [params] = useSearchParams();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });
  const fromPerson = params.get('person');
  const personId = fromPerson !== null && UUID.test(fromPerson) ? fromPerson : null;
  const person = usePerson(familyId, personId ?? '', { enabled: isValidId && personId !== null });

  if (!isValidId || (family.error instanceof ApiError && family.error.status === 404)) {
    return <FamilyNotFoundPage />;
  }
  if (family.isError) {
    return <ErrorState error={family.error} onRetry={() => void family.refetch()} />;
  }
  // An unknown Person gives an invitation without Person, as if none was asked.
  const invited = personId === null ? undefined : person.data;
  const name = invited ? displayNameOf(invited) : null;
  const backPath = invited ? personPath(familyId, invited.id) : familyHomePath(familyId);
  const loading = family.isPending || (personId !== null && person.isPending);

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={backPath}
          className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <h1 className="text-display break-words text-text">
          {name ? t('invite.titlePerson', { name }) : t('invite.title')}
        </h1>
      </header>
      {loading ? (
        <div className="flex flex-col gap-4" aria-busy="true">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      ) : family.data.myRole !== 'ADMIN' ? (
        <p role="status" className="text-body text-text">
          {t('invite.notAdmin')}
        </p>
      ) : (
        <InviteForm
          // `Invite someone else` starts again from an empty screen.
          key={invited?.id ?? 'anyone'}
          familyId={familyId}
          familyName={family.data.name}
          person={invited ?? null}
        />
      )}
    </div>
  );
}

function InviteForm({
  familyId,
  familyName,
  person,
}: {
  familyId: string;
  familyName: string;
  person: Person | null;
}) {
  const { t, i18n } = useTranslation('invitation');
  const navigate = useNavigate();
  const create = useCreateInvitation(familyId);
  const [created, setCreated] = useState<CreatedInvitation | null>(null);
  const form = useForm<{ role: InvitationRole }>({ defaultValues: { role: 'CONTRIBUTOR' } });
  const role = useWatch({ control: form.control, name: 'role' });
  const roleHelpId = useId();
  const name = person ? displayNameOf(person) : '';
  const blocker = person ? inviteBlocker(person) : null;

  if (created) {
    return (
      <div className="flex flex-col gap-6">
        <InvitationLinkShare invitation={created} familyName={familyName} />
        <Button
          variant="secondary"
          onClick={() => {
            setCreated(null);
            create.reset();
            form.reset();
            void navigate(invitePath(familyId));
          }}
        >
          {t('share.inviteSomeoneElse')}
        </Button>
        {person && <BackToProfile familyId={familyId} person={person} />}
      </div>
    );
  }
  if (blocker) {
    return (
      <div className="flex flex-col gap-4">
        <p role="status" className="text-body text-text">
          {t(`invite.cannot.${blocker}`, { name })}
        </p>
        {person && <BackToProfile familyId={familyId} person={person} />}
      </div>
    );
  }

  const refusal = person ? refusalOf(create.error) : null;
  return (
    <form
      noValidate
      className="flex flex-col gap-6"
      onSubmit={(event) => {
        void form.handleSubmit((values) => {
          create.mutate(
            { role: values.role, personId: person?.id ?? null },
            { onSuccess: setCreated },
          );
        })(event);
      }}
    >
      <p className="text-body text-text-muted">{t('invite.intro')}</p>
      <div className="flex flex-col gap-1">
        <Select
          label={t('invite.role')}
          options={ROLES.map((value) => ({ value, label: t(`invite.roles.${value}`) }))}
          aria-describedby={roleHelpId}
          disabled={create.isPending}
          {...form.register('role')}
        />
        <p id={roleHelpId} className="text-caption text-text-muted">
          {t(`invite.roleHelp.${role}`)}
        </p>
      </div>
      {create.isError && (
        <div className="flex flex-col gap-2">
          <p role="alert" className="text-body text-text">
            {refusal ? t(`invite.cannot.${refusal}`, { name }) : errorMessage(i18n, create.error)}
          </p>
          {refusal && person && <BackToProfile familyId={familyId} person={person} />}
        </div>
      )}
      <Button type="submit" disabled={create.isPending}>
        {t('invite.submit')}
      </Button>
    </form>
  );
}

/** The refusals of an invitation for a Person, explained with the Person's name. */
function refusalOf(error: unknown): InviteBlocker | 'pending' | null {
  if (!(error instanceof ApiError)) return null;
  switch (error.code) {
    case 'INVITATION_ALREADY_PENDING':
      return 'pending';
    case 'PERSON_ALREADY_CLAIMED':
      return 'linked';
    case 'PERSON_NOT_FOUND':
      return 'inactive';
    // The only value of this form the server can refuse: the Person is now recorded as deceased.
    case 'VALIDATION_FAILED':
      return 'deceased';
    default:
      return null;
  }
}

function BackToProfile({ familyId, person }: { familyId: string; person: Person }) {
  const { t } = useTranslation('invitation');
  return (
    <Link
      to={personPath(familyId, person.id)}
      className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
    >
      {t('invite.backToProfile', { name: displayNameOf(person) })}
    </Link>
  );
}
