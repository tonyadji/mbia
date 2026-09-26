import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import type { components } from '../api/generated/schema';
import { buttonClassName } from '../components/Button';
import { ErrorState } from '../components/ErrorState';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import { formatDate } from '../i18n/formatDate';
import { DEFAULT_LANGUAGE, isSupportedLanguage } from '../i18n/language';
import { useMemory } from '../memories/useMemory';
import { familyHomePath } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';
import { personPath } from './PersonProfilePage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Memory = components['schemas']['MemoryResponse'];
type RelatedPerson = components['schemas']['RelatedPersonReference'];
type Role = components['schemas']['MembershipRole'];

export function memoryPath(familyId: string, memoryId: string) {
  return `/families/${familyId}/memories/${memoryId}`;
}

/** Navigation state set by Add Memory, for the success message. */
export interface MemoryPageState {
  published?: boolean;
}

const LINK_CLASS =
  'inline-flex min-h-12 items-center self-start text-body font-semibold text-primary underline-offset-4 hover:underline';

/**
 * SCREEN-013 — Memory, for any ACTIVE member: the story's title and full text as plain text with
 * line breaks kept (OQ-032), its Persons (an archived one marked, and a link only for the ADMIN,
 * OQ-035), who added it and when. An archived or unknown Memory is not found (OQ-037). Edit and
 * archive arrive with PR-33.
 */
export function MemoryPage() {
  const { familyId = '', memoryId = '' } = useParams();
  const isValid = UUID.test(familyId) && UUID.test(memoryId);
  const memory = useMemory(familyId, memoryId, { enabled: isValid });
  const family = useFamily(familyId, { enabled: isValid });

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
  if (memory.isPending || family.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  return <MemoryView familyId={familyId} memory={memory.data} role={family.data?.myRole} />;
}

function MemoryView({
  familyId,
  memory,
  role,
}: {
  familyId: string;
  memory: Memory;
  role: Role | undefined;
}) {
  const { t, i18n } = useTranslation('memory');
  const navigate = useNavigate();
  const location = useLocation();
  const published = (location.state as MemoryPageState | null)?.published === true;
  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  const actor = memory.createdBy.deleted
    ? t('screen.formerMember')
    : (memory.createdBy.displayName ?? t('screen.member'));
  // Back to where the User came from; a Memory opened directly goes back to the Family.
  const hasHistory = location.key !== 'default';

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
      {published && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {t('published', { title: memory.title ?? '' })}
        </p>
      )}
      <header className="flex flex-col gap-1">
        <h1 className="text-display break-words text-text">{memory.title}</h1>
        <p className="text-caption text-text-muted">
          {t('screen.addedBy', { actor, date: formatDate(new Date(memory.createdAt), language) })}
        </p>
      </header>
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
