import { useTranslation } from 'react-i18next';
import type { TreeNode } from '../persons/useFamilyTree';
import { yearOf } from '../persons/formatPartialDate';
import { Avatar } from './Avatar';

/**
 * Birth and death years of a Person, as in the mockup: "1948 – 2019", "1975 –" while alive, "†"
 * standing for an unknown death year.
 */
export function lifeYears(person: Pick<TreeNode, 'birth' | 'death' | 'isDeceased'>): string | null {
  const birth = yearOf(person.birth);
  const death = person.isDeceased ? (yearOf(person.death) ?? '†') : null;
  if (birth) return `${birth} –${death ? ` ${death}` : ''}`;
  if (death) return death === '†' ? death : `† ${death}`;
  return null;
}

/**
 * A tree card (family-tree-ux.md §7, design-guidelines.md §6): avatar, name, birth and death years
 * only. The focus is a little larger; "↑" / "↓" say that the tree continues beyond this card.
 */
export function PersonCard({
  person,
  isFocus = false,
  indicator = null,
  onSelect,
}: {
  person: TreeNode;
  isFocus?: boolean;
  indicator?: 'up' | 'down' | null;
  onSelect: () => void;
}) {
  const { t } = useTranslation('tree');
  const name = person.displayName ?? person.firstName;
  const years = lifeYears(person);

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={isFocus ? 'true' : undefined}
      className={`relative flex size-full flex-col items-center justify-center gap-1 rounded-2xl border bg-surface px-2 py-2 text-center transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${isFocus ? 'border-2 border-primary' : 'border-border'}`}
    >
      <Avatar
        displayName={name}
        photoUrl={person.profilePictureUrl}
        size={isFocus ? 'lg' : 'card'}
      />
      <span className="line-clamp-2 max-w-full text-caption font-semibold break-words text-text">
        {name}
      </span>
      {years && <span className="text-caption text-text-muted">{years}</span>}
      {indicator === 'up' && (
        <span className="absolute -top-3 inline-flex rounded-full border border-border bg-surface px-2 py-0.5 text-text-muted">
          <Arrow direction="up" />
          <span className="sr-only">{t('card.moreParents')}</span>
        </span>
      )}
      {indicator === 'down' && (
        <span className="absolute -bottom-3 inline-flex rounded-full border border-border bg-surface px-2 py-0.5 text-text-muted">
          <Arrow direction="down" />
          <span className="sr-only">{t('card.moreChildren')}</span>
        </span>
      )}
    </button>
  );
}

function Arrow({ direction }: { direction: 'up' | 'down' }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={`size-4 ${direction === 'down' ? 'rotate-180' : ''}`}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 19V5M6 11l6-6 6 6" />
    </svg>
  );
}
