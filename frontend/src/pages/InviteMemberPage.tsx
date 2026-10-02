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
import { TextField } from '../components/TextField';
import { useFamily } from '../families/useFamily';
import { isSupportedLanguage, DEFAULT_LANGUAGE } from '../i18n/language';
import { InvitationEmailResult } from '../invitations/InvitationEmailResult';
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
type InvitationChannel = components['schemas']['InvitationChannel'];

const ROLES = ['CONTRIBUTOR', 'VIEWER'] as const satisfies readonly InvitationRole[];

/** A deliberately loose check: the mail provider is the judge of an address. */
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** `Share a link` first on a phone (SCREEN-009), `Send by email` first on a larger screen. */
function channelsInOrder(): InvitationChannel[] {
  const largeScreen =
    typeof window.matchMedia === 'function' && window.matchMedia('(min-width: 768px)').matches;
  return largeScreen ? ['EMAIL', 'LINK'] : ['LINK', 'EMAIL'];
}

/** SCREEN-009, for a Person of the tree (`personId`, from their profile) or for anyone. */
export function invitePath(familyId: string, { personId }: { personId?: string } = {}) {
  return `/families/${familyId}/invitations/new${personId ? `?person=${personId}` : ''}`;
}

/**
 * SCREEN-009 — Invite Member, ADMIN only (OQ-051), by `Send by email` or `Share a link`. From a
 * profile, the invitation carries the Person, named in the title (OQ-050).
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
  const [channels] = useState(channelsInOrder);
  const form = useForm<{ channel: InvitationChannel; email: string; role: InvitationRole }>({
    defaultValues: { channel: channels[0], email: '', role: 'CONTRIBUTOR' },
  });
  const role = useWatch({ control: form.control, name: 'role' });
  const channel = useWatch({ control: form.control, name: 'channel' });
  const roleHelpId = useId();
  const name = person ? displayNameOf(person) : '';
  const blocker = person ? inviteBlocker(person) : null;

  if (created) {
    return (
      <div className="flex flex-col gap-6">
        {created.channel === 'EMAIL' ? (
          <InvitationEmailResult invitation={created} />
        ) : (
          <InvitationLinkShare invitation={created} familyName={familyName} />
        )}
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
  // A refused address is shown on its field.
  const emailRefused = isEmailRefused(create.error);
  return (
    <form
      noValidate
      className="flex flex-col gap-6"
      onSubmit={(event) => {
        void form.handleSubmit((values) => {
          const personId = person?.id ?? null;
          create.mutate(
            values.channel === 'EMAIL'
              ? {
                  channel: 'EMAIL',
                  role: values.role,
                  personId,
                  email: values.email.trim(),
                  // The email is written in the inviter's current language (mvp.md §18).
                  locale: isSupportedLanguage(i18n.resolvedLanguage)
                    ? i18n.resolvedLanguage
                    : DEFAULT_LANGUAGE,
                }
              : { channel: 'LINK', role: values.role, personId },
            {
              onSuccess: setCreated,
              onError: (error) => {
                if (isEmailRefused(error)) {
                  form.setError('email', { message: t('invite.emailInvalid') });
                }
              },
            },
          );
        })(event);
      }}
    >
      <Select
        label={t('invite.channel')}
        options={channels.map((value) => ({ value, label: t(`invite.channels.${value}`) }))}
        disabled={create.isPending}
        {...form.register('channel')}
      />
      <p className="text-body text-text-muted">
        {channel === 'EMAIL' ? t('invite.introEmail') : t('invite.intro')}
      </p>
      {channel === 'EMAIL' && (
        <TextField
          label={t('invite.email')}
          type="email"
          inputMode="email"
          autoComplete="off"
          disabled={create.isPending}
          error={form.formState.errors.email?.message}
          {...form.register('email', {
            shouldUnregister: true,
            validate: (value) => {
              const email = value.trim();
              if (email === '') return t('invite.emailRequired');
              return EMAIL.test(email) || t('invite.emailInvalid');
            },
          })}
        />
      )}
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
      {create.isError && !emailRefused && (
        <div className="flex flex-col gap-2">
          <p role="alert" className="text-body text-text">
            {refusal ? t(`invite.cannot.${refusal}`, { name }) : errorMessage(i18n, create.error)}
          </p>
          {refusal && person && <BackToProfile familyId={familyId} person={person} />}
        </div>
      )}
      <Button type="submit" disabled={create.isPending}>
        {channel === 'EMAIL' ? t('invite.submitEmail') : t('invite.submit')}
      </Button>
    </form>
  );
}

/** The server refused the email address (`VALIDATION_FAILED` on `email`). */
function isEmailRefused(error: unknown) {
  return (
    error instanceof ApiError &&
    error.code === 'VALIDATION_FAILED' &&
    error.fieldErrors.some((fieldError) => fieldError.field === 'email')
  );
}

/** The refusals of an invitation for a Person, explained with the Person's name. */
function refusalOf(error: unknown): InviteBlocker | 'pending' | null {
  if (!(error instanceof ApiError) || isEmailRefused(error)) return null;
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
