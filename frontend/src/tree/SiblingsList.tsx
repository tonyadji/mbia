import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { BottomSheet } from '../components/BottomSheet';
import { lifeYears } from '../components/PersonCard';
import { kinshipLabel } from '../persons/kinship';
import type { TreeNode } from '../persons/useFamilyTree';

/**
 * SCREEN-COMPONENT-002 — the siblings of the focus, opened from the `Siblings (n)` chip; selecting
 * one recenters the tree on them (family-tree-ux.md §6.1).
 */
export function SiblingsList({
  siblings,
  onSelect,
  onClose,
}: {
  siblings: TreeNode[];
  onSelect: (sibling: TreeNode) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation('person');

  return (
    <BottomSheet title={t('profile.relatives.siblings')} onClose={onClose}>
      <ul className="flex flex-col gap-2">
        {siblings.map((sibling) => {
          const name = sibling.displayName ?? sibling.firstName;
          const years = lifeYears(sibling);
          const code = sibling.relationshipToCurrentUser;
          const relationship = code == null ? null : kinshipLabel(t, code, sibling.gender);
          return (
            <li key={sibling.id}>
              <button
                type="button"
                onClick={() => {
                  onSelect(sibling);
                }}
                className="flex w-full items-center gap-3 rounded-xl border border-border bg-surface px-4 py-3 text-left hover:border-primary focus-visible:outline-2 focus-visible:outline-primary"
              >
                <Avatar displayName={name} photoUrl={sibling.profilePictureUrl} />
                <span className="flex min-w-0 flex-col">
                  <span className="text-body font-semibold break-words text-text">{name}</span>
                  {relationship && (
                    <span className="text-caption text-text-muted">{relationship}</span>
                  )}
                  {years && <span className="text-caption text-text-muted">{years}</span>}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </BottomSheet>
  );
}
