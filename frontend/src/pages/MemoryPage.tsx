import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import type { components } from '../api/generated/schema';
import { useCurrentUser } from '../auth/useCurrentUser';
import { Button, buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { formatDate } from '../i18n/formatDate';
import { DEFAULT_LANGUAGE, isSupportedLanguage } from '../i18n/language';
import { ArchiveMemoryDialog } from '../memories/ArchiveMemoryDialog';
import { useArchiveMemory } from '../memories/useArchiveMemory';
import { useMemory } from '../memories/useMemory';
import { familyHomePath } from './FamilyHomePage';
import { familyMemoriesPath } from './FamilyMemoriesPage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { personPath } from './PersonProfilePage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Memory = components['schemas']['MemoryResponse'];
type RelatedPerson = components['schemas']['RelatedPersonReference'];
type Role = components['schemas']['MembershipRole'];

export function memoryPath(familyId: string, memoryId: string) {
  return `/families/${familyId}/memories/${memoryId}`;
}

export function editMemoryPath(familyId: string, memoryId: string) {
  return `${memoryPath(familyId, memoryId)}/edit`;
}

/** Navigation state set by Add Memory and Edit Memory, for the success message. */
export interface MemoryPageState {
  published?: boolean;
  saved?: boolean;
}

const LINK_CLASS =
  'inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline';

/**
 * The creator or an ADMIN may edit or archive a Memory, with a role that can write: a VIEWER stays
 * read-only, even on their own Memory (mvp.md §17, OQ-041). The backend checks it again.
 */
export function canChangeMemory(
  memory: Memory,
  role: Role | undefined,
  myUserId: string | undefined,
) {
  return role === 'ADMIN' || (role === 'CONTRIBUTOR' && memory.createdBy.userId === myUserId);
}

/**
 * Loads the Memory of the route, the caller's role and account, then renders `children`; shows the
 * loading, not-found and error states otherwise. An archived or unknown Memory is not found
 * (OQ-037).
 */
export function MemoryRoute({
  children,
}: {
  children: (props: {
    familyId: string;
    memory: Memory;
    role: Role | undefined;
    myUserId: string | undefined;
    reload: () => Promise<Memory | undefined>;
  }) => ReactNode;
}) {
  const { familyId = '', memoryId = '' } = useParams();
  const isValid = UUID.test(familyId) && UUID.test(memoryId);
  const memory = useMemory(familyId, memoryId, { enabled: isValid });
  const family = useFamily(familyId, { enabled: isValid });
  const me = useCurrentUser();

  if (!UUID.test(familyId)) return <FamilyNotFoundPage />;
  if (!isValid) return <MemoryNotFoundPage familyId={familyId} />;
  if (memory.error instanceof ApiError && memory.error.status === 404) {
    return memory.error.code === 'FAMILY_NOT_FOUND' ? (
      <FamilyNotFoundPage />
    ) : (
      <MemoryNotFoundPage familyId={familyId} />
    );
  }
  if (memory.isError) {
    return <ErrorState error={memory.error} onRetry={() => void memory.refetch()} />;
  }
  if (memory.isPending || family.isPending || me.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  return children({
    familyId,
    memory: memory.data,
    role: family.data?.myRole,
    myUserId: me.data?.id,
    reload: async () => (await memory.refetch()).data,
  });
}

/**
 * SCREEN-013 — Memory, for any ACTIVE member: the story's title and full text as plain text with
 * line breaks kept (OQ-032), its Persons (an archived one marked, and a link only for the ADMIN,
 * OQ-035), who added it and when. Its creator or an ADMIN may edit it (SCREEN-014) or archive it
 * after a confirmation (OQ-041).
 */
export function MemoryPage() {
  return (
    <MemoryRoute>
      {({ familyId, memory, role, myUserId, reload }) => (
        <MemoryView
          familyId={familyId}
          memory={memory}
          role={role}
          canChange={canChangeMemory(memory, role, myUserId)}
          reload={reload}
        />
      )}
    </MemoryRoute>
  );
}

function MemoryView({
  familyId,
  memory,
  role,
  canChange,
  reload,
}: {
  familyId: string;
  memory: Memory;
  role: Role | undefined;
  canChange: boolean;
  reload: () => Promise<Memory | undefined>;
}) {
  const { t, i18n } = useTranslation('memory');
  const navigate = useNavigate();
  const location = useLocation();
  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  const actor = memory.createdBy.deleted
    ? t('screen.formerMember')
    : (memory.createdBy.displayName ?? t('screen.member'));
  const state = location.state as MemoryPageState | null;
  const published = state?.published === true;
  const saved = state?.saved === true;
  // Back to where the User came from; a Memory opened directly goes back to the Family.
  const hasHistory = location.key !== 'default';
  const leave = () => {
    if (hasHistory) void navigate(-1);
    else void navigate(familyMemoriesPath(familyId), { replace: true });
  };

  return (
    <article className="flex flex-1 flex-col gap-6">
      {hasHistory ? (
        <button type="button" className={LINK_CLASS} onClick={() => void navigate(-1)}>
          {t('screen.back')}
        </button>
      ) : (
        <Link to={familyHomePath(familyId)} className={LINK_CLASS}>
          {t('screen.back')}
        </Link>
      )}
      {(published || saved) && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {t(published ? 'published' : 'saved', { title: memory.title ?? '' })}
        </p>
      )}
      <header className="flex flex-col gap-1">
        <h1 className="text-display break-words text-text">{memory.title}</h1>
        <p className="text-caption text-text-muted">
          {t('screen.addedBy', { actor, date: formatDate(new Date(memory.createdAt), language) })}
        </p>
      </header>
      {canChange && (
        <MemoryActions familyId={familyId} memory={memory} reload={reload} onArchived={leave} />
      )}
      {/* Plain text: rendered as a text node, never as HTML or Markdown (OQ-032). */}
      <p className="text-body break-words whitespace-pre-wrap text-text">{memory.content}</p>
      <section aria-labelledby="memory-persons" className="flex flex-col gap-2">
        <h2 id="memory-persons" className="text-section text-text">
          {t('screen.persons')}
        </h2>
        <ul aria-labelledby="memory-persons" className="flex flex-col gap-2">
          {memory.relatedPersons.map((person) => (
            <li key={person.id}>
              <RelatedPersonRow familyId={familyId} person={person} isAdmin={role === 'ADMIN'} />
            </li>
          ))}
        </ul>
      </section>
    </article>
  );
}

/** `Edit` (SCREEN-014) and `Archive`, confirmed in a Modal: the Memory disappears for everyone. */
function MemoryActions({
  familyId,
  memory,
  reload,
  onArchived,
}: {
  familyId: string;
  memory: Memory;
  reload: () => Promise<Memory | undefined>;
  onArchived: () => void;
}) {
  const { t } = useTranslation('memory');
  const [confirming, setConfirming] = useState(false);
  const archive = useArchiveMemory(familyId, memory.id);
  return (
    <div className="flex flex-col gap-3 sm:flex-row">
      <Link
        to={editMemoryPath(familyId, memory.id)}
        replace
        className={buttonClassName('secondary', 'sm:w-auto')}
      >
        {t('screen.edit')}
      </Link>
      <Button
        variant="secondary"
        className="sm:w-auto"
        onClick={() => {
          archive.reset();
          setConfirming(true);
        }}
      >
        {t('screen.archive')}
      </Button>
      {confirming && (
        <ArchiveMemoryDialog
          pending={archive.isPending}
          error={archive.error}
          onCancel={() => {
            setConfirming(false);
          }}
          onReload={async () => {
            await reload();
            archive.reset();
          }}
          onConfirm={() => {
            archive.mutate({ version: memory.version }, { onSuccess: onArchived });
          }}
        />
      )}
    </div>
  );
}

/** An ACTIVE Person links to their profile; an archived one is marked, and a link only for the ADMIN. */
function RelatedPersonRow({
  familyId,
  person,
  isAdmin,
}: {
  familyId: string;
  person: RelatedPerson;
  isAdmin: boolean;
}) {
  const { t } = useTranslation('memory');
  const active = person.status === 'ACTIVE';
  const name =
    active || isAdmin ? (
      <Link
        to={personPath(familyId, person.id)}
        className="inline-flex min-h-12 items-center text-body font-semibold break-words text-primary underline-offset-4 hover:underline"
      >
        {person.displayName}
      </Link>
    ) : (
      <span className="text-body font-semibold break-words text-text">{person.displayName}</span>
    );
  return (
    <div className="flex flex-col">
      {name}
      {person.status !== 'ACTIVE' && (
        <span className="text-caption text-text-muted">{t(`screen.status.${person.status}`)}</span>
      )}
    </div>
  );
}

/** A Memory that does not exist, or no longer, in this Family (404 `MEMORY_NOT_FOUND`). */
function MemoryNotFoundPage({ familyId }: { familyId: string }) {
  const { t } = useTranslation('memory');
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <h1 className="text-display text-text">{t('screen.notFound.title')}</h1>
      <p className="text-body text-text-muted">{t('screen.notFound.body')}</p>
      <Link to={familyHomePath(familyId)} className={buttonClassName('secondary', 'sm:w-auto')}>
        {t('screen.notFound.back')}
      </Link>
    </div>
  );
}
