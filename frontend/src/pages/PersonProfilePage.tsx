import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useParams } from 'react-router';
import { ApiError } from '../api/client';
import type { components } from '../api/generated/schema';
import { Avatar } from '../components/Avatar';
import { buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { errorMessage } from '../api/errorMessage';
import { Button } from '../components/Button';
import { useFamily } from '../families/useFamily';
import { isSupportedLanguage, DEFAULT_LANGUAGE } from '../i18n/language';
import { AddRelativeMenu } from '../persons/AddRelativeMenu';
import { formatPartialDate, yearOf } from '../persons/formatPartialDate';
import { familySections } from '../persons/familySections';
import { kinshipLabel } from '../persons/kinship';
import { relativeChoiceGroups } from '../persons/relatives';
import { useClaimPerson } from '../persons/useClaimPerson';
import { useFamilyTree, type TreeNode } from '../persons/useFamilyTree';
import { usePerson, type Person } from '../persons/usePerson';
import { familyHomePath } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Role = components['schemas']['MembershipRole'];

export function personPath(familyId: string, personId: string) {
  return `/families/${familyId}/persons/${personId}`;
}

/** Navigation state set by Add Relative, for the success message. */
export interface PersonProfileState {
  relativeAdded?: { name: string };
}

export function displayNameOf(person: Person) {
  return person.displayName ?? person.firstName;
}

/**
 * Whether the caller may edit this Person: ADMIN always, CONTRIBUTOR unless the Person is linked to
 * another User (person-relationships-collaboration.md §2). The backend enforces the same rule.
 */
export function canEditPerson(person: Person, role: Role | undefined) {
  if (person.status !== 'ACTIVE') return false;
  if (role === 'ADMIN') return true;
  return (
    role === 'CONTRIBUTOR' &&
    (person.linkedUserId == null || person.relationshipToCurrentUser === 'SELF')
  );
}

/** Whether the caller may add relatives to this Person (ADMIN or CONTRIBUTOR, ACTIVE Person). */
export function canAddRelatives(person: Person, role: Role | undefined) {
  return person.status === 'ACTIVE' && (role === 'ADMIN' || role === 'CONTRIBUTOR');
}

/**
 * Whether the caller may say "This is me": the Person is ACTIVE and linked to nobody, and the
 * caller has no linked Person in the Family (`relationshipToCurrentUser` is then null, mvp.md §7).
 */
export function canClaimPerson(person: Person) {
  return (
    person.status === 'ACTIVE' &&
    person.linkedUserId == null &&
    person.relationshipToCurrentUser == null
  );
}

/**
 * Loads the Person of the route and the caller's role, then renders `children`; shows the loading,
 * not-found and error states otherwise.
 */
export function PersonRoute({
  children,
}: {
  children: (props: {
    familyId: string;
    person: Person;
    role: Role | undefined;
    reload: () => Promise<Person | undefined>;
  }) => ReactNode;
}) {
  const { familyId = '', personId = '' } = useParams();
  const isValid = UUID.test(familyId) && UUID.test(personId);
  const person = usePerson(familyId, personId, { enabled: isValid });
  const family = useFamily(familyId, { enabled: isValid });

  if (!UUID.test(familyId)) return <FamilyNotFoundPage />;
  if (!isValid) return <PersonNotFoundPage familyId={familyId} />;
  if (person.error instanceof ApiError && person.error.status === 404) {
    return person.error.code === 'FAMILY_NOT_FOUND' ? (
      <FamilyNotFoundPage />
    ) : (
      <PersonNotFoundPage familyId={familyId} />
    );
  }
  if (person.isError) {
    return <ErrorState error={person.error} onRetry={() => void person.refetch()} />;
  }
  if (person.isPending || family.isPending) {
    return <ProfileSkeleton />;
  }
  return children({
    familyId,
    person: person.data,
    role: family.data?.myRole,
    reload: async () => (await person.refetch()).data,
  });
}

/**
 * SCREEN-005 — Person profile: header, Family (ACTIVE Persons only) and About; no Memory, History
 * or photo in this phase (Phase 2 plan §3.1, §3.2).
 */
export function PersonProfilePage() {
  return (
    <PersonRoute>
      {({ familyId, person, role }) => (
        <PersonProfile familyId={familyId} person={person} role={role} />
      )}
    </PersonRoute>
  );
}

function PersonProfile({
  familyId,
  person,
  role,
}: {
  familyId: string;
  person: Person;
  role: Role | undefined;
}) {
  const { t, i18n } = useTranslation(['person', 'settings']);
  const { t: tPerson } = useTranslation('person');
  const relativeAdded = (useLocation().state as PersonProfileState | null)?.relativeAdded;
  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  const unknown = t('person:form.precision.UNKNOWN');

  const birthYear = yearOf(person.birth);
  const lifespan: string[] = [];
  if (birthYear) lifespan.push(t('person:profile.birthYear', { year: birthYear }));
  if (person.isDeceased) {
    lifespan.push(t('person:profile.deathYear', { year: yearOf(person.death) ?? unknown }));
  }
  // On the profile, NONE_KNOWN is explained rather than hidden (localization-and-kinship-labels.md §3).
  const relationship =
    person.relationshipToCurrentUser == null
      ? null
      : person.relationshipToCurrentUser === 'NONE_KNOWN'
        ? t('person:profile.relationship.NONE_KNOWN')
        : kinshipLabel(tPerson, person.relationshipToCurrentUser, person.gender);

  const about: [string, string][] = [];
  if (person.middleNames) about.push([t('person:form.middleNames'), person.middleNames]);
  if (person.preferredName) {
    about.push([
      t('person:profile.name'),
      [person.firstName, person.lastName].filter(Boolean).join(' '),
    ]);
    about.push([t('person:form.preferredName'), person.preferredName]);
  }
  about.push([t('person:form.birth'), formatPartialDate(person.birth, language) ?? unknown]);
  if (person.isDeceased) {
    about.push([t('person:form.death'), formatPartialDate(person.death, language) ?? unknown]);
  }

  return (
    <div className="flex flex-1 flex-col gap-8">
      <header className="flex flex-col gap-4">
        <Link
          to={familyHomePath(familyId)}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {t('settings:back')}
        </Link>
        <div className="flex items-center gap-4">
          <Avatar displayName={displayNameOf(person)} size="lg" />
          <div className="flex min-w-0 flex-col gap-1">
            <h1 className="text-display break-words text-text">{displayNameOf(person)}</h1>
            {lifespan.length > 0 && (
              <p className="flex flex-wrap gap-x-4 text-body text-text-muted">
                {lifespan.map((part) => (
                  <span key={part}>{part}</span>
                ))}
              </p>
            )}
            {relationship && <p className="text-caption text-text-muted">{relationship}</p>}
          </div>
        </div>
        {canEditPerson(person, role) && (
          <Link
            to={`${personPath(familyId, person.id)}/edit`}
            className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
          >
            {t('person:profile.edit')}
          </Link>
        )}
        <ClaimAction familyId={familyId} person={person} />
      </header>

      {relativeAdded && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {t('person:relative.added', { name: relativeAdded.name, anchor: displayNameOf(person) })}
        </p>
      )}

      {person.status === 'ACTIVE' && (
        <section aria-labelledby="profile-family" className="flex flex-col gap-4">
          <h2 id="profile-family" className="text-section text-text">
            {t('person:profile.family')}
          </h2>
          <Relatives familyId={familyId} person={person} />
          {canAddRelatives(person, role) && (
            <AddRelativeMenu
              label={t('person:relative.menu')}
              groups={relativeChoiceGroups(tPerson, familyId, person.id, 'profile')}
            />
          )}
        </section>
      )}

      <section aria-labelledby="profile-about" className="flex flex-col gap-4">
        <h2 id="profile-about" className="text-section text-text">
          {t('person:profile.about')}
        </h2>
        <dl className="flex flex-col gap-3">
          {about.map(([label, value]) => (
            <div key={label} className="flex flex-col">
              <dt className="text-caption text-text-muted">{label}</dt>
              <dd className="text-body break-words text-text">{value}</dd>
            </div>
          ))}
          {person.biography && (
            <div className="flex flex-col">
              <dt className="text-caption text-text-muted">{t('person:form.biography')}</dt>
              <dd className="text-body break-words whitespace-pre-line text-text">
                {person.biography}
              </dd>
            </div>
          )}
        </dl>
      </section>
    </div>
  );
}

const SECTIONS = ['parents', 'partners', 'children', 'siblings'] as const;

/**
 * SCREEN-005 Family section: the Person's parents, partners, children and siblings from the tree
 * centred on them, each with what they are to the current User (localization-and-kinship-labels.md
 * §3, §3bis).
 */
function Relatives({ familyId, person }: { familyId: string; person: Person }) {
  const { t } = useTranslation('person');
  const tree = useFamilyTree(familyId, person.id);

  if (tree.isPending) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-12 w-full" />
      </div>
    );
  }
  if (tree.isError) {
    return <ErrorState error={tree.error} onRetry={() => void tree.refetch()} />;
  }
  const sections = familySections(tree.data, person.id);
  if (SECTIONS.every((section) => sections[section].length === 0)) {
    return <p className="text-body text-text-muted">{t('profile.noRelatives')}</p>;
  }
  return (
    <div className="flex flex-col gap-4">
      {SECTIONS.filter((section) => sections[section].length > 0).map((section) => (
        <div key={section} className="flex flex-col gap-2">
          <h3
            id={`profile-family-${section}`}
            className="text-caption font-semibold text-text-muted"
          >
            {t(`profile.relatives.${section}`)}
          </h3>
          <ul aria-labelledby={`profile-family-${section}`} className="flex flex-col gap-2">
            {sections[section].map((relative) => (
              <li key={relative.id}>
                <RelativeRow familyId={familyId} relative={relative} />
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

function RelativeRow({ familyId, relative }: { familyId: string; relative: TreeNode }) {
  const { t } = useTranslation('person');
  const name = relative.displayName ?? relative.firstName;
  const birthYear = yearOf(relative.birth);
  const deathYear = yearOf(relative.death);
  const years: string[] = [];
  if (birthYear) years.push(t('profile.birthYear', { year: birthYear }));
  if (relative.isDeceased && deathYear) years.push(t('profile.deathYear', { year: deathYear }));
  const relationship =
    relative.relationshipToCurrentUser == null
      ? null
      : kinshipLabel(t, relative.relationshipToCurrentUser, relative.gender);

  return (
    <Link
      to={personPath(familyId, relative.id)}
      className="flex items-center gap-3 rounded-xl border border-border bg-surface px-4 py-3 hover:border-primary"
    >
      <Avatar displayName={name} />
      <span className="flex min-w-0 flex-col">
        <span className="text-body font-semibold break-words text-text">{name}</span>
        {relationship && <span className="text-caption text-text-muted">{relationship}</span>}
        {years.length > 0 && (
          <span className="flex flex-wrap gap-x-3 text-caption text-text-muted">
            {years.map((part) => (
              <span key={part}>{part}</span>
            ))}
          </span>
        )}
      </span>
    </Link>
  );
}

/**
 * SCREEN-005 `This is me` on a Person that can be claimed, and unlink on the caller's own linked
 * Person. Correcting another member's link stays an ADMIN operation of the API.
 */
function ClaimAction({ familyId, person }: { familyId: string; person: Person }) {
  const { t, i18n } = useTranslation('person');
  const claim = useClaimPerson(familyId, person.id);
  const isMine = person.relationshipToCurrentUser === 'SELF';
  if (!isMine && !canClaimPerson(person)) return null;

  return (
    <div className="flex flex-col gap-2">
      <Button
        variant="secondary"
        className="sm:w-auto sm:self-start"
        disabled={claim.isPending}
        onClick={() => {
          claim.mutate({ claim: !isMine, version: person.version });
        }}
      >
        {t(isMine ? 'profile.unclaim' : 'profile.claim')}
      </Button>
      {claim.isError && (
        <p role="alert" className="text-body text-text">
          {errorMessage(i18n, claim.error)}
        </p>
      )}
      {claim.isSuccess && (
        <p role="status" className="text-caption text-text-muted">
          {t(isMine ? 'profile.claimed' : 'profile.unclaimed')}
        </p>
      )}
    </div>
  );
}

/** A Person that does not exist in this Family (404 `PERSON_NOT_FOUND`). */
function PersonNotFoundPage({ familyId }: { familyId: string }) {
  const { t } = useTranslation('person');
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <h1 className="text-display text-text">{t('notFound.title')}</h1>
      <p className="text-body text-text-muted">{t('notFound.body')}</p>
      <Link to={familyHomePath(familyId)} className={buttonClassName('secondary', 'sm:w-auto')}>
        {t('notFound.back')}
      </Link>
    </div>
  );
}

function ProfileSkeleton() {
  return (
    <div className="flex items-center gap-4">
      <Skeleton className="size-20 rounded-full" />
      <div className="flex flex-1 flex-col gap-2">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-32" />
      </div>
    </div>
  );
}
