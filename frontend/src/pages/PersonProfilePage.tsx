import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
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
import { ArchivePersonDialog } from '../persons/ArchivePersonDialog';
import { MergePersonDialog } from '../persons/MergePersonDialog';
import { PersonHistory } from '../persons/PersonHistory';
import { formatDate } from '../i18n/formatDate';
import { formatPartialDate, yearOf } from '../persons/formatPartialDate';
import { familySections, sectionLink, type TreeEdge } from '../persons/familySections';
import { genderForm, kinshipLabel } from '../persons/kinship';
import { RemoveRelationshipDialog } from '../persons/RemoveRelationshipDialog';
import {
  useArchivedRelationships,
  type ArchivedRelationship,
} from '../persons/useArchivedRelationships';
import { useArchivePerson } from '../persons/useArchivePerson';
import { useMergePerson } from '../persons/useMergePerson';
import { useArchiveRelationship } from '../persons/useArchiveRelationship';
import { useRestoreRelationship } from '../persons/useRestoreRelationship';
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
  /** `existing` when a Person already in the Family was linked, rather than a new one added. */
  relativeAdded?: { name: string; existing?: boolean };
  /** The duplicate just merged into this Person (SCREEN-COMPONENT-004). */
  merged?: { name: string };
  /** The story just published about this Person (SCREEN-006). */
  memoryPublished?: { title: string };
}

export function displayNameOf(person: Pick<Person, 'displayName' | 'firstName'>) {
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

/**
 * Whether the caller may remove a link of this Person: ADMIN or CONTRIBUTOR, ACTIVE Person
 * (mvp.md §13). Restoring a removed link is for the ADMIN only.
 */
export function canRemoveLinks(person: Person, role: Role | undefined) {
  return person.status === 'ACTIVE' && (role === 'ADMIN' || role === 'CONTRIBUTOR');
}

/** Whether the caller may add relatives to this Person (ADMIN or CONTRIBUTOR, ACTIVE Person). */
export function canAddRelatives(person: Person, role: Role | undefined) {
  return person.status === 'ACTIVE' && (role === 'ADMIN' || role === 'CONTRIBUTOR');
}

/**
 * Whether the caller may archive this Person: ADMIN, ACTIVE Person linked to no User (mvp.md §13,
 * person-relationships-collaboration.md §5). The backend enforces the same rule.
 */
export function canArchivePerson(person: Person, role: Role | undefined) {
  return role === 'ADMIN' && person.status === 'ACTIVE' && person.linkedUserId == null;
}

/** Whether the caller may merge this Person, a duplicate, into another: ADMIN, ACTIVE Person (mvp.md §12). */
export function canMergePerson(person: Person, role: Role | undefined) {
  return role === 'ADMIN' && person.status === 'ACTIVE';
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
 * SCREEN-005 — Person profile: header, Family (ACTIVE Persons only), the ADMIN's Removed links,
 * About and History; no Memory or photo in this phase (Phase 2 plan §3.1, §3.2). An ARCHIVED Person
 * shows a notice and no mutation action except, for the ADMIN, `Restore`; a MERGED Person, a notice
 * leading to the kept profile.
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
  const { t, i18n } = useTranslation(['person', 'settings', 'memory']);
  const { t: tPerson } = useTranslation('person');
  const profileState = useLocation().state as PersonProfileState | null;
  const relativeAdded = profileState?.relativeAdded;
  const merged = profileState?.merged;
  const memoryPublished = profileState?.memoryPublished;
  const [notice, setNotice] = useState<LinkNotice | null>(null);
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
          className="inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
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
        <ArchiveAction familyId={familyId} person={person} role={role} />
        <MergeAction familyId={familyId} person={person} role={role} />
      </header>

      {memoryPublished && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {t('memory:published', { title: memoryPublished.title })}
        </p>
      )}
      {merged && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {t('person:merge.done', { name: merged.name, kept: displayNameOf(person) })}
        </p>
      )}

      {notice ? (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {notice.kind === 'removed'
            ? t('person:removeLink.done', { name: notice.name })
            : t(
                notice.withWarnings
                  ? 'person:removedLinks.restoredWithWarnings'
                  : 'person:removedLinks.restored',
              )}
        </p>
      ) : (
        relativeAdded && (
          <p
            role="status"
            className="rounded-xl border border-border bg-surface px-4 py-3 text-body"
          >
            {t(relativeAdded.existing ? 'person:relative.linked' : 'person:relative.added', {
              name: relativeAdded.name,
              anchor: displayNameOf(person),
            })}
          </p>
        )
      )}

      {person.status === 'ACTIVE' && (
        <section aria-labelledby="profile-family" className="flex flex-col gap-4">
          <h2 id="profile-family" className="text-section text-text">
            {t('person:profile.family')}
          </h2>
          <Relatives
            familyId={familyId}
            person={person}
            canRemove={canRemoveLinks(person, role)}
            onNotice={setNotice}
          />
          {canAddRelatives(person, role) && (
            <AddRelativeMenu
              label={t('person:relative.menu')}
              groups={relativeChoiceGroups(tPerson, familyId, person.id, 'profile')}
            />
          )}
        </section>
      )}

      {person.status === 'ACTIVE' && role === 'ADMIN' && (
        <RemovedLinks familyId={familyId} person={person} onNotice={setNotice} />
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

      <PersonHistory familyId={familyId} personId={person.id} />
    </div>
  );
}

const SECTIONS = ['parents', 'partners', 'children', 'siblings'] as const;

/** The feedback of the last removal or restoration of a link (screens.md, Global UI rules). */
type LinkNotice = { kind: 'removed'; name: string } | { kind: 'restored'; withWarnings: boolean };

/** A link the User asked to remove, waiting for the confirmation (SCREEN-COMPONENT-003). */
interface PendingRemoval {
  link: TreeEdge;
  name: string;
}

/**
 * SCREEN-005 Family section: the Person's parents, partners, children and siblings from the tree
 * centred on them, each with what they are to the current User (localization-and-kinship-labels.md
 * §3, §3bis). With `canRemove`, parents, partners and children offer `Remove link`; siblings do not.
 */
function Relatives({
  familyId,
  person,
  canRemove,
  onNotice,
}: {
  familyId: string;
  person: Person;
  canRemove: boolean;
  onNotice: (notice: LinkNotice | null) => void;
}) {
  const { t } = useTranslation('person');
  const tree = useFamilyTree(familyId, person.id);
  const archive = useArchiveRelationship(familyId);
  const [pending, setPending] = useState<PendingRemoval | null>(null);

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
            {sections[section].map((relative) => {
              const link = canRemove
                ? sectionLink(tree.data, section, person.id, relative.id)
                : undefined;
              const name = relative.displayName ?? relative.firstName;
              return (
                <li key={relative.id} className="flex flex-col">
                  <RelativeRow familyId={familyId} relative={relative} />
                  {link && (
                    <button
                      type="button"
                      aria-label={t('removeLink.actionFor', { name })}
                      onClick={() => {
                        archive.reset();
                        setPending({ link, name });
                      }}
                      className="inline-flex min-h-12 items-center self-end rounded-full px-4 text-caption font-semibold text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    >
                      {t('removeLink.action')}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      ))}
      {pending && (
        <RemoveRelationshipDialog
          pending={archive.isPending}
          error={archive.error}
          onCancel={() => {
            setPending(null);
          }}
          onConfirm={() => {
            archive.mutate(
              { relationshipId: pending.link.relationshipId, version: pending.link.version },
              {
                onSuccess: () => {
                  onNotice({ kind: 'removed', name: pending.name });
                  setPending(null);
                },
              },
            );
          }}
        />
      )}
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

/** Restore refusals explained for this action; other errors use their general message. */
const RESTORE_REFUSALS = [
  'PERSON_NOT_ACTIVE',
  'RELATIONSHIP_ALREADY_EXISTS',
  'RELATIONSHIP_CREATES_CYCLE',
] as const;

type RestoreRefusal = (typeof RESTORE_REFUSALS)[number];

function isRestoreRefusal(code: string | null): code is RestoreRefusal {
  return RESTORE_REFUSALS.some((refusal) => refusal === code);
}

/**
 * SCREEN-005 Removed links (ADMIN only): the removed relationships of the Person, most recent first,
 * collapsed, hidden when there is nothing to restore (mvp.md §13, genealogy.md §11bis). Each shows
 * the other Person, the relationship as a path sentence (localization-and-kinship-labels.md §4), the
 * removal date and `Restore`; a refused restore is explained.
 */
function RemovedLinks({
  familyId,
  person,
  onNotice,
}: {
  familyId: string;
  person: Person;
  onNotice: (notice: LinkNotice | null) => void;
}) {
  const { t } = useTranslation('person');
  const archived = useArchivedRelationships(familyId, person.id);
  const restore = useRestoreRelationship(familyId);

  // A secondary, collapsed area: nothing is shown while loading or when it cannot be loaded.
  if (!archived.isSuccess || archived.data.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby="profile-removed-links" className="flex flex-col gap-4">
      <details className="flex flex-col gap-4 rounded-xl border border-border bg-surface px-4 py-3">
        <summary className="min-h-12 cursor-pointer content-center">
          <h2 id="profile-removed-links" className="inline text-section text-text">
            {t('removedLinks.title')}
          </h2>
        </summary>
        <ul aria-labelledby="profile-removed-links" className="mt-2 flex flex-col gap-4">
          {archived.data.map((relationship) => (
            <li key={relationship.id}>
              <RemovedLinkRow
                familyId={familyId}
                person={person}
                relationship={relationship}
                restoring={
                  restore.isPending && restore.variables.relationshipId === relationship.id
                }
                error={restore.variables?.relationshipId === relationship.id ? restore.error : null}
                onRestore={() => {
                  onNotice(null);
                  restore.mutate(
                    { relationshipId: relationship.id, version: relationship.version },
                    {
                      onSuccess: (restored) => {
                        onNotice({ kind: 'restored', withWarnings: restored.warnings.length > 0 });
                      },
                    },
                  );
                }}
              />
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}

function RemovedLinkRow({
  familyId,
  person,
  relationship,
  restoring,
  error,
  onRestore,
}: {
  familyId: string;
  person: Person;
  relationship: ArchivedRelationship;
  restoring: boolean;
  error: unknown;
  onRestore: () => void;
}) {
  const { t, i18n } = useTranslation('person');
  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  const related = relationship.relatedPerson;
  const name = related.displayName ?? related.firstName;
  // What the other Person is to this one: the `to` of a path step from this Person (§4).
  const relation =
    relationship.type === 'PARTNER_OF'
      ? 'PARTNER'
      : relationship.sourcePersonId === related.id
        ? 'PARENT'
        : 'CHILD';
  const sentence = t(`kinship.step.${relation}.${genderForm(related.gender)}`, {
    to: name,
    from: displayNameOf(person),
  });
  const refusal =
    error instanceof ApiError && isRestoreRefusal(error.code)
      ? t(`removedLinks.refused.${error.code}`, { name })
      : error != null
        ? errorMessage(i18n, error)
        : null;

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <Avatar displayName={name} />
        <div className="flex min-w-0 flex-col">
          <Link
            to={personPath(familyId, related.id)}
            className="flex min-h-12 items-center text-body font-semibold break-words text-text underline-offset-4 hover:underline"
          >
            {name}
          </Link>
          {related.status !== 'ACTIVE' && (
            <span className="text-caption text-text-muted">
              {t(`removedLinks.status.${related.status}`)}
            </span>
          )}
          <span className="text-caption text-text">{sentence}</span>
          <span className="text-caption text-text-muted">
            {t('removedLinks.removedOn', {
              date: formatDate(new Date(relationship.archivedAt), language),
            })}
          </span>
        </div>
      </div>
      <Button
        variant="secondary"
        className="sm:w-auto sm:self-start"
        disabled={restoring}
        aria-label={t('removedLinks.restoreFor', { name })}
        onClick={onRestore}
      >
        {t('removedLinks.restore')}
      </Button>
      {refusal && (
        <p role="alert" className="text-body text-text">
          {refusal}
        </p>
      )}
    </div>
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

/**
 * SCREEN-005 archive / restore (mvp.md §13): on an ACTIVE Person linked to no one, the ADMIN's
 * `Archive` after a confirmation; on an ARCHIVED Person, the "Archived" notice for every member and
 * `Restore` for the ADMIN.
 */
function ArchiveAction({
  familyId,
  person,
  role,
}: {
  familyId: string;
  person: Person;
  role: Role | undefined;
}) {
  const { t, i18n } = useTranslation('person');
  const mutation = useArchivePerson(familyId, person.id);
  const [confirming, setConfirming] = useState(false);
  const name = displayNameOf(person);

  if (person.status === 'ARCHIVED') {
    return (
      <div className="flex flex-col gap-3">
        <p
          role="note"
          className="rounded-xl border border-border bg-surface px-4 py-3 text-body font-semibold text-text"
        >
          {t('archived.notice')}
        </p>
        {mutation.isSuccess && mutation.variables.archive && (
          <p role="status" className="text-caption text-text-muted">
            {t('archive.done', { name })}
          </p>
        )}
        {role === 'ADMIN' && (
          <Button
            variant="secondary"
            className="sm:w-auto sm:self-start"
            disabled={mutation.isPending}
            onClick={() => {
              mutation.mutate({ archive: false, version: person.version });
            }}
          >
            {t('archived.restore')}
          </Button>
        )}
        {mutation.isError && !mutation.variables.archive && (
          <p role="alert" className="text-body text-text">
            {errorMessage(i18n, mutation.error)}
          </p>
        )}
      </div>
    );
  }

  const restored = mutation.isSuccess && !mutation.variables.archive && (
    <p role="status" className="text-caption text-text-muted">
      {t('archived.restored', { name })}
    </p>
  );
  if (!canArchivePerson(person, role)) return restored || null;
  return (
    <div className="flex flex-col gap-2">
      {restored}
      <Button
        variant="secondary"
        className="sm:w-auto sm:self-start"
        onClick={() => {
          mutation.reset();
          setConfirming(true);
        }}
      >
        {t('archive.action')}
      </Button>
      {confirming && (
        <ArchivePersonDialog
          name={name}
          pending={mutation.isPending}
          error={mutation.error}
          onCancel={() => {
            setConfirming(false);
          }}
          onConfirm={() => {
            mutation.mutate(
              { archive: true, version: person.version },
              {
                onSuccess: () => {
                  setConfirming(false);
                },
              },
            );
          }}
        />
      )}
    </div>
  );
}

/**
 * SCREEN-005 merge of a duplicate (SCREEN-COMPONENT-004): the ADMIN's `Merge with another profile`
 * on an ACTIVE Person; on a MERGED Person, a notice with the way to the kept profile.
 */
function MergeAction({
  familyId,
  person,
  role,
}: {
  familyId: string;
  person: Person;
  role: Role | undefined;
}) {
  const { t } = useTranslation('person');
  const navigate = useNavigate();
  const mutation = useMergePerson(familyId, person.id);
  const [merging, setMerging] = useState(false);

  if (person.status === 'MERGED') {
    return (
      <div className="flex flex-col gap-3">
        <p
          role="note"
          className="rounded-xl border border-border bg-surface px-4 py-3 text-body font-semibold text-text"
        >
          {t('merged.notice')}
        </p>
        {person.mergedIntoPersonId && (
          <Link
            to={personPath(familyId, person.mergedIntoPersonId)}
            className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
          >
            {t('merged.open')}
          </Link>
        )}
      </div>
    );
  }
  if (!canMergePerson(person, role)) return null;
  return (
    <>
      <Button
        variant="secondary"
        className="sm:w-auto sm:self-start"
        onClick={() => {
          mutation.reset();
          setMerging(true);
        }}
      >
        {t('merge.action')}
      </Button>
      {merging && (
        <MergePersonDialog
          familyId={familyId}
          source={person}
          pending={mutation.isPending}
          error={mutation.error}
          onCancel={() => {
            setMerging(false);
          }}
          onConfirm={(target) => {
            mutation.mutate(
              {
                targetPersonId: target.id,
                sourceVersion: person.version,
                targetVersion: target.version,
              },
              {
                onSuccess: (kept) => {
                  // The profile page stays mounted for the kept Person: close the dialog first.
                  setMerging(false);
                  const state: PersonProfileState = { merged: { name: displayNameOf(person) } };
                  void navigate(personPath(familyId, kept.id), { state });
                },
              },
            );
          }}
        />
      )}
    </>
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
