import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Link } from 'react-router';
import { formatRelativeTime } from '../i18n/formatRelativeTime';
import { DEFAULT_LANGUAGE, isSupportedLanguage } from '../i18n/language';
import { memoryPath } from '../pages/MemoryPage';
import { personPath } from '../pages/PersonProfilePage';
import { useFamilyActivities, type FamilyActivity } from './useFamilyActivities';

const TYPES = [
  'PERSON_CREATED',
  'PERSON_ARCHIVED',
  'PERSON_RESTORED',
  'PERSON_MERGED',
  'RELATIONSHIP_CREATED',
  'RELATIONSHIP_ARCHIVED',
  'MEMORY_CREATED',
  'INVITATION_ACCEPTED',
  'MEMBER_LEFT',
  'MEMBER_REMOVED',
] as const;

type ActivityType = (typeof TYPES)[number];

type KnownActivity = FamilyActivity & { type: ActivityType };

/** A type this version does not know is not shown rather than shown as a raw code. */
function isKnown(item: FamilyActivity): item is KnownActivity {
  return (TYPES as readonly string[]).includes(item.type);
}

/**
 * SCREEN-002 recent activity (mvp.md §20, OQ-054): the 10 most recent lines, grouped by the server,
 * each with who, what and when. A line leads to its Person or Memory only when it holds one item
 * that is still ACTIVE; relationships and members lead nowhere. Names are those recorded at the
 * time of the action. Without any activity, or when it cannot be read, the section is not shown.
 */
export function RecentActivity({ familyId }: { familyId: string }) {
  const { t, i18n } = useTranslation('family');
  const activities = useFamilyActivities(familyId);
  const items = (activities.data ?? []).filter(isKnown);
  if (items.length === 0) return null;

  const language = isSupportedLanguage(i18n.resolvedLanguage)
    ? i18n.resolvedLanguage
    : DEFAULT_LANGUAGE;
  return (
    <section aria-labelledby="recent-activity" className="flex flex-col gap-3">
      <h2 id="recent-activity" className="text-section text-text">
        {t('home.activity.title')}
      </h2>
      <ul className="flex flex-col divide-y divide-border rounded-xl border border-border bg-surface">
        {items.map((item) => {
          const text = describe(item, t);
          const to = linkOf(familyId, item);
          return (
            <li key={item.id} className="flex flex-col gap-0.5 px-4 py-3">
              {to === undefined ? (
                <p className="text-body break-words text-text">{text}</p>
              ) : (
                <Link
                  to={to}
                  className="inline-flex min-h-11 items-center self-start text-body break-words font-semibold text-primary underline-offset-4 hover:underline"
                >
                  {text}
                </Link>
              )}
              <time dateTime={item.occurredAt} className="text-caption text-text-muted">
                {formatRelativeTime(new Date(item.occurredAt), language)}
              </time>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function linkOf(familyId: string, item: FamilyActivity): string | undefined {
  if (item.count !== 1 || item.resourceActive !== true || item.resourceId == null) {
    return undefined;
  }
  if (item.resourceType === 'PERSON') return personPath(familyId, item.resourceId);
  if (item.resourceType === 'MEMORY') return memoryPath(familyId, item.resourceId);
  return undefined;
}

function describe(item: KnownActivity, t: TFunction<'family'>): string {
  const actor = item.actor.deleted
    ? t('home.activity.formerMember')
    : (item.actor.displayName ?? t('home.activity.member'));
  if (item.count > 1) {
    return t(`home.activity.groups.${item.type}`, { actor, count: item.count });
  }
  const data = item.data ?? {};
  const name = (key: string) => {
    const value = data[key];
    return typeof value === 'string' && value !== '' ? value : t('home.activity.someone');
  };
  switch (item.type) {
    case 'PERSON_MERGED':
      return t('home.activity.lines.PERSON_MERGED', {
        actor,
        name: name('personDisplayName'),
        merged: name('mergedPersonDisplayName'),
      });
    case 'RELATIONSHIP_CREATED':
    case 'RELATIONSHIP_ARCHIVED':
      return t(`home.activity.lines.${item.type}`, {
        actor,
        source: name('sourcePersonDisplayName'),
        target: name('targetPersonDisplayName'),
      });
    case 'MEMORY_CREATED':
      return typeof data.memoryTitle === 'string' && data.memoryTitle !== ''
        ? t('home.activity.lines.MEMORY_CREATED', { actor, title: data.memoryTitle })
        : t('home.activity.lines.MEMORY_CREATED_untitled', { actor });
    case 'INVITATION_ACCEPTED':
    case 'MEMBER_LEFT': {
      // The member is the actor: their name at the time, else their account's.
      const value = data.memberDisplayName;
      const member = typeof value === 'string' && value !== '' ? value : actor;
      return t(`home.activity.lines.${item.type}`, { member });
    }
    case 'MEMBER_REMOVED':
      return t('home.activity.lines.MEMBER_REMOVED', { actor, member: name('memberDisplayName') });
    default:
      return t(`home.activity.lines.${item.type}`, { actor, name: name('personDisplayName') });
  }
}
