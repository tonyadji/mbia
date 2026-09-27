import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { Avatar } from '../components/Avatar';
import { BottomSheet } from '../components/BottomSheet';
import { Button, buttonClassName } from '../components/Button';
import { lifeYears } from '../components/PersonCard';
import { Skeleton } from '../components/Skeleton';
import { usePersonMemoryCount } from '../memories/usePersonMemoryCount';
import { AddRelativeMenu } from '../persons/AddRelativeMenu';
import { familySections } from '../persons/familySections';
import { kinshipLabel, type PathPerson } from '../persons/kinship';
import { relativeChoiceGroups } from '../persons/relatives';
import { useFamilyTree, type TreeNode } from '../persons/useFamilyTree';
import { personPath } from '../pages/PersonProfilePage';
import { KinshipPath } from './KinshipPath';

/**
 * SCREEN-COMPONENT-001 — Person Quick View (family-tree-ux.md §8): photo (initials in Phase 2),
 * name, years, what the Person is to the current User with "See how", child count, then view
 * profile, center the tree and add relatives when allowed. The Memory count is read only when the
 * Quick View opens (OQ-038).
 */
export function PersonQuickView({
  familyId,
  person,
  isFocus,
  canAdd,
  myPersonId,
  known,
  onCenter,
  onClose,
}: {
  familyId: string;
  person: TreeNode;
  isFocus: boolean;
  canAdd: boolean;
  myPersonId: string | null;
  known: Record<string, PathPerson>;
  onCenter: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation('tree');
  const { t: tPerson } = useTranslation('person');
  const name = person.displayName ?? person.firstName;
  const years = lifeYears(person);
  const code = person.relationshipToCurrentUser;
  const relationship = code == null ? null : kinshipLabel(tPerson, code, person.gender);
  const explainable =
    myPersonId !== null && code != null && code !== 'SELF' && code !== 'NONE_KNOWN';

  return (
    <BottomSheet title={name} onClose={onClose}>
      <div className="flex items-center gap-4">
        <Avatar displayName={name} photoUrl={person.profilePictureUrl} size="lg" alt={name} />
        <div className="flex min-w-0 flex-col gap-1">
          {years && <p className="text-body text-text-muted">{years}</p>}
          {relationship && (
            <p className="self-start rounded-full bg-background px-3 text-caption font-semibold text-primary">
              {relationship}
            </p>
          )}
          <ChildCount familyId={familyId} personId={person.id} />
          <MemoryCount familyId={familyId} personId={person.id} />
        </div>
      </div>
      {explainable && (
        <KinshipPath
          familyId={familyId}
          fromPersonId={myPersonId}
          personId={person.id}
          known={known}
        />
      )}
      <Link to={personPath(familyId, person.id)} className={buttonClassName('primary')}>
        {t('quickView.viewProfile')}
      </Link>
      {!isFocus && (
        <Button variant="secondary" onClick={onCenter}>
          {t('quickView.center', { name })}
        </Button>
      )}
      {canAdd && (
        <AddRelativeMenu
          label={t('quickView.add')}
          groups={relativeChoiceGroups(tPerson, familyId, person.id, 'tree')}
        />
      )}
    </BottomSheet>
  );
}

/** The Person's children, counted on the tree centred on them (also ready for recentering). */
function ChildCount({ familyId, personId }: { familyId: string; personId: string }) {
  const { t } = useTranslation('tree');
  const tree = useFamilyTree(familyId, personId);
  if (tree.isPending) return <Skeleton className="h-4 w-20" />;
  if (tree.isError) return null;
  const count = familySections(tree.data, personId).children.length;
  return <p className="text-caption text-text-muted">{t('quickView.children', { count })}</p>;
}

/** The Person's ACTIVE Memories, counted from a one-item page (OQ-038). */
function MemoryCount({ familyId, personId }: { familyId: string; personId: string }) {
  const { t } = useTranslation('tree');
  const count = usePersonMemoryCount(familyId, personId);
  if (count.isPending) return <Skeleton className="h-4 w-20" />;
  if (count.isError) return null;
  return (
    <p className="text-caption text-text-muted">{t('quickView.memories', { count: count.data })}</p>
  );
}
