import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { BottomSheet } from '../components/BottomSheet';
import { ErrorState } from '../components/ErrorState';
import { NavigationBar } from '../components/NavigationBar';
import { Skeleton } from '../components/Skeleton';
import { useFamily } from '../families/useFamily';
import type { PathPerson } from '../persons/kinship';
import { addRelativePath, relativeChoiceGroups } from '../persons/relatives';
import { useFamilyTree, type TreeNode } from '../persons/useFamilyTree';
import { clearLastFocus, readLastFocus, writeLastFocus } from '../tree/lastFocus';
import { PersonQuickView } from '../tree/PersonQuickView';
import { SiblingsList } from '../tree/SiblingsList';
import { TreeCanvas } from '../tree/TreeCanvas';
import { layoutTree, type PlacedAdd } from '../tree/treeLayout';
import type { FamilyTreeState } from '../tree/treePath';
import { FamilyEmptyState, familyHomePath, type Family } from './FamilyHomePage';
import { FamilyNotFoundPage } from './FamilyNotFoundPage';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** SCREEN-003 — Family Tree, centred on `?focus=` or on the default focus (family-tree-ux.md §6). */
export function FamilyTreePage() {
  const { familyId = '' } = useParams();
  const isValidId = UUID.test(familyId);
  const family = useFamily(familyId, { enabled: isValidId });

  if (!isValidId || (family.error instanceof ApiError && family.error.status === 404)) {
    return <FamilyNotFoundPage />;
  }
  return (
    <div className="flex flex-1 flex-col gap-4 pb-20">
      {family.isError ? (
        <ErrorState error={family.error} onRetry={() => void family.refetch()} />
      ) : family.isPending ? (
        <TreeSkeleton />
      ) : (
        <FamilyTree family={family.data} />
      )}
      <NavigationBar familyId={familyId} />
    </div>
  );
}

function FamilyTree({ family }: { family: Family }) {
  const { t } = useTranslation(['tree', 'person']);
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const relativeAdded = (useLocation().state as FamilyTreeState | null)?.relativeAdded;
  const urlFocus = params.get('focus');
  const requestedInUrl = urlFocus !== null && UUID.test(urlFocus) ? urlFocus : undefined;
  // Rule 1 before rule 2 (OQ-018): the remembered focus only when the User has no linked Person.
  const [storedFocus, setStoredFocus] = useState(() =>
    family.myLinkedPersonId == null ? readLastFocus(family.id) : undefined,
  );
  const requested = requestedInUrl ?? storedFocus;
  const tree = useFamilyTree(family.id, requested, { keepPrevious: true });
  const canAdd = family.myRole === 'ADMIN' || family.myRole === 'CONTRIBUTOR';
  const layout = useMemo(
    () => (tree.data ? layoutTree(tree.data, { canAdd }) : null),
    [tree.data, canAdd],
  );
  const [selected, setSelected] = useState<TreeNode | null>(null);
  const [siblingsOpen, setSiblingsOpen] = useState(false);
  const [adding, setAdding] = useState<'parent' | 'child' | null>(null);

  const actualFocus = tree.isPlaceholderData ? undefined : tree.data?.focusPersonId;
  useEffect(() => {
    if (actualFocus) writeLastFocus(family.id, actualFocus);
  }, [family.id, actualFocus]);

  // A focus that no longer exists: back to the default focus.
  const notFound = tree.error instanceof ApiError && tree.error.code === 'PERSON_NOT_FOUND';
  if (notFound && !requestedInUrl && storedFocus !== undefined) setStoredFocus(undefined);
  useEffect(() => {
    if (!notFound) return;
    if (requestedInUrl) setParams({}, { replace: true });
    else clearLastFocus(family.id);
  }, [notFound, requestedInUrl, family.id, setParams]);

  const known = useMemo(() => {
    const persons: Record<string, PathPerson> = {};
    for (const node of tree.data?.nodes ?? []) {
      persons[node.id] = { displayName: node.displayName ?? node.firstName, gender: node.gender };
    }
    return persons;
  }, [tree.data]);

  function recenter(node: TreeNode) {
    setSelected(null);
    setSiblingsOpen(false);
    setParams({ focus: node.id });
  }

  function add(relation: PlacedAdd['relation']) {
    if (!layout) return;
    if (relation === 'partner') {
      void navigate(addRelativePath(family.id, layout.focus.id, 'PARTNER', 'tree'));
    } else {
      setAdding(relation);
    }
  }

  let content;
  if (tree.isError && !notFound) {
    content = <ErrorState error={tree.error} onRetry={() => void tree.refetch()} />;
  } else if (tree.isPending || notFound) {
    content = <TreeSkeleton />;
  } else if (layout === null) {
    content = <FamilyEmptyState family={family} />;
  } else {
    content = (
      <div
        aria-busy={tree.isPlaceholderData}
        className={`flex min-h-0 flex-1 flex-col transition-opacity ${tree.isPlaceholderData ? 'opacity-70' : ''}`}
      >
        <TreeCanvas
          layout={layout}
          onSelect={setSelected}
          onAdd={add}
          onSiblings={() => {
            setSiblingsOpen(true);
          }}
        />
      </div>
    );
  }

  return (
    <>
      <header className="flex flex-col gap-2">
        <Link
          to={familyHomePath(family.id)}
          className="self-start text-body font-semibold text-primary underline-offset-4 hover:underline"
        >
          {family.name}
        </Link>
        <h1 className="text-display text-text">{t('tree:title')}</h1>
      </header>
      {relativeAdded && (
        <p role="status" className="rounded-xl border border-border bg-surface px-4 py-3 text-body">
          {t('person:relative.added', relativeAdded)}
        </p>
      )}
      {content}
      {selected && layout && (
        <PersonQuickView
          familyId={family.id}
          person={selected}
          isFocus={selected.id === layout.focus.id}
          canAdd={canAdd && selected.status === 'ACTIVE'}
          myPersonId={family.myLinkedPersonId ?? null}
          known={known}
          onCenter={() => {
            recenter(selected);
          }}
          onClose={() => {
            setSelected(null);
          }}
        />
      )}
      {siblingsOpen && layout && (
        <SiblingsList
          siblings={layout.siblings}
          onSelect={recenter}
          onClose={() => {
            setSiblingsOpen(false);
          }}
        />
      )}
      {adding && layout && (
        <AddChoices
          familyId={family.id}
          anchor={layout.focus}
          relation={adding}
          onClose={() => {
            setAdding(null);
          }}
        />
      )}
    </>
  );
}

/** The gendered choices of an "Add a parent" / "Add a child" slot (family-tree-ux.md §9.1). */
function AddChoices({
  familyId,
  anchor,
  relation,
  onClose,
}: {
  familyId: string;
  anchor: TreeNode;
  relation: 'parent' | 'child';
  onClose: () => void;
}) {
  const { t } = useTranslation('person');
  const [group] = relativeChoiceGroups(t, familyId, anchor.id, 'tree', relation);
  return (
    <BottomSheet title={group?.heading ?? ''} onClose={onClose}>
      <ul className="flex flex-col gap-2">
        {group?.choices.map((choice) => (
          <li key={choice.to}>
            <Link
              to={choice.to}
              className="flex min-h-12 items-center rounded-xl border border-border px-4 text-body font-semibold text-primary hover:border-primary focus-visible:outline-2 focus-visible:outline-primary"
            >
              {choice.label}
            </Link>
          </li>
        ))}
      </ul>
    </BottomSheet>
  );
}

/** Skeleton Person cards in the three rows (SCREEN-003 loading). */
function TreeSkeleton() {
  const { t } = useTranslation('tree');
  return (
    <div aria-busy="true" className="flex flex-col items-center gap-12 py-6">
      <p className="sr-only">{t('loading')}</p>
      {[2, 1, 3].map((count, row) => (
        <div key={row} className="flex gap-4">
          {Array.from({ length: count }, (_, index) => (
            <Skeleton key={index} className={row === 1 ? 'h-44 w-32' : 'h-36 w-24'} />
          ))}
        </div>
      ))}
    </div>
  );
}
