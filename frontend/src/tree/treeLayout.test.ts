import type { FamilyTree, TreeNode } from '../persons/useFamilyTree';
import { layoutTree, type TreeLayout } from './treeLayout';

function node(id: string, extra: Partial<TreeNode> = {}): TreeNode {
  return {
    id,
    familyId: 'family',
    firstName: id,
    displayName: id,
    gender: 'UNKNOWN',
    birth: { precision: 'UNKNOWN' },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    version: 0,
    hasMoreParents: false,
    hasMoreChildren: false,
    ...extra,
  };
}

let nextEdge = 0;
function parentOf(parent: string, child: string) {
  return {
    relationshipId: `r${String(nextEdge++)}`,
    type: 'PARENT_OF' as const,
    sourcePersonId: parent,
    targetPersonId: child,
    version: 0,
  };
}
function partnerOf(a: string, b: string) {
  return {
    relationshipId: `r${String(nextEdge++)}`,
    type: 'PARTNER_OF' as const,
    sourcePersonId: a,
    targetPersonId: b,
    version: 0,
  };
}

function tree(
  ids: string[],
  edges: FamilyTree['edges'],
  extra: Record<string, Partial<TreeNode>> = {},
) {
  return {
    focusPersonId: ids[0] ?? null,
    nodes: ids.map((id) => node(id, extra[id])),
    edges,
  } satisfies FamilyTree;
}

function layout(value: FamilyTree, canAdd = false): TreeLayout {
  const result = layoutTree(value, { canAdd });
  if (result === null) throw new Error('no layout');
  return result;
}

const ids = (nodes: TreeNode[]) => nodes.map((n) => n.id);
const cardOf = (result: TreeLayout, id: string) => {
  const found = result.cards.find((c) => c.node.id === id);
  if (!found) throw new Error(`no card ${id}`);
  return found;
};

describe('layoutTree (family-tree-ux.md §6.1)', () => {
  it('has no layout for an empty Family', () => {
    expect(layoutTree({ focusPersonId: null, nodes: [], edges: [] }, { canAdd: true })).toBeNull();
  });

  it('centres the focus in row 2, larger than the other cards', () => {
    const result = layout(tree(['me', 'mum'], [parentOf('mum', 'me')]));
    const focus = cardOf(result, 'me');
    expect(focus.x).toBe(0);
    expect(focus.role).toBe('focus');
    expect(focus.width).toBeGreaterThan(cardOf(result, 'mum').width);
    expect(cardOf(result, 'mum').y).toBeLessThan(focus.y);
  });

  it('shows more than two parents in the same row, joining only the parents who are partners', () => {
    const result = layout(
      tree(
        ['me', 'a', 'b', 'c'],
        [parentOf('a', 'me'), parentOf('b', 'me'), parentOf('c', 'me'), partnerOf('a', 'b')],
      ),
    );
    expect(ids(result.parents)).toEqual(['a', 'b', 'c']);
    const [a, b, c] = [cardOf(result, 'a'), cardOf(result, 'b'), cardOf(result, 'c')];
    expect(new Set([a.y, b.y, c.y]).size).toBe(1);
    expect(a.x).toBeLessThan(b.x);
    expect(b.x).toBeLessThan(c.x);
    const horizontal = result.lines.filter(([, y1, , y2]) => y1 === a.y && y2 === a.y);
    expect(horizontal).toEqual([[a.x, a.y, b.x, b.y]]);
  });

  it('offers an "Add a parent" slot only when allowed and with fewer than two parents', () => {
    const one = tree(['me', 'mum'], [parentOf('mum', 'me')]);
    expect(layout(one, true).adds.map((a) => a.relation)).toContain('parent');
    expect(layout(one, false).adds).toEqual([]);
    const two = tree(['me', 'mum', 'dad'], [parentOf('mum', 'me'), parentOf('dad', 'me')]);
    expect(layout(two, true).adds.map((a) => a.relation)).not.toContain('parent');
  });

  it('places partners first on the right, next on the left, alternating by creation', () => {
    const result = layout(
      tree(
        ['me', 'p1', 'p2', 'p3'],
        [partnerOf('me', 'p1'), partnerOf('p2', 'me'), partnerOf('me', 'p3')],
      ),
    );
    const [p1, p2, p3] = [cardOf(result, 'p1').x, cardOf(result, 'p2').x, cardOf(result, 'p3').x];
    expect(p1).toBeGreaterThan(0);
    expect(p2).toBeLessThan(0);
    expect(p3).toBeGreaterThan(p1);
    expect(ids(result.partners)).toEqual(['p2', 'p1', 'p3']);
  });

  it('groups children by their other parent, left to right as the partners, others last', () => {
    const result = layout(
      tree(
        ['me', 'p1', 'p2', 'c1', 'c2', 'c3', 'c4', 'c5'],
        [
          partnerOf('me', 'p1'),
          partnerOf('me', 'p2'),
          parentOf('me', 'c1'),
          parentOf('p1', 'c1'),
          parentOf('me', 'c2'),
          parentOf('me', 'c3'),
          parentOf('p2', 'c3'),
          parentOf('me', 'c4'),
          parentOf('p1', 'c4'),
          parentOf('me', 'c5'),
          parentOf('p2', 'c5'),
        ],
      ),
    );
    // p2 is on the left, p1 on the right: groups follow that order, node order within a group.
    expect(result.childGroups.map((g) => [g.otherParentId, ids(g.children)])).toEqual([
      ['p2', ['c3', 'c5']],
      ['p1', ['c1', 'c4']],
      [null, ['c2']],
    ]);
    const xs = ['c3', 'c5', 'c1', 'c4', 'c2'].map((id) => cardOf(result, id).x);
    expect(xs).toEqual([...xs].sort((a, b) => a - b));
  });

  it('keeps siblings out of the rows, for the chip only', () => {
    const result = layout(
      tree(['me', 'mum', 'sis'], [parentOf('mum', 'me'), parentOf('mum', 'sis')]),
    );
    expect(ids(result.siblings)).toEqual(['sis']);
    expect(result.cards.map((c) => c.node.id)).not.toContain('sis');
    expect(result.siblingsChip).not.toBeNull();
    expect(layout(tree(['me'], [])).siblingsChip).toBeNull();
  });

  it('shows continuation indicators on parents and children only', () => {
    const result = layout(
      tree(
        ['me', 'mum', 'kid', 'p'],
        [parentOf('mum', 'me'), parentOf('me', 'kid'), partnerOf('me', 'p')],
        {
          me: { hasMoreParents: true, hasMoreChildren: true },
          mum: { hasMoreParents: true, hasMoreChildren: true },
          kid: { hasMoreParents: true, hasMoreChildren: true },
          p: { hasMoreParents: true, hasMoreChildren: true },
        },
      ),
    );
    expect(cardOf(result, 'mum').indicator).toBe('up');
    expect(cardOf(result, 'kid').indicator).toBe('down');
    expect(cardOf(result, 'me').indicator).toBeNull();
    expect(cardOf(result, 'p').indicator).toBeNull();
  });

  it('offers partner and child affordances only when allowed', () => {
    expect(
      layout(tree(['me'], []), true)
        .adds.map((a) => a.relation)
        .sort(),
    ).toEqual(['child', 'parent', 'partner']);
    expect(layout(tree(['me'], []), false).adds).toEqual([]);
  });

  it('never overlaps two cards of a row', () => {
    const edges = [
      ...['a', 'b', 'c'].map((p) => parentOf(p, 'me')),
      ...['p1', 'p2', 'p3'].map((p) => partnerOf('me', p)),
      ...['k1', 'k2', 'k3', 'k4'].map((k) => parentOf('me', k)),
      parentOf('p1', 'k1'),
      parentOf('p2', 'k2'),
    ];
    const result = layout(
      tree(['me', 'a', 'b', 'c', 'p1', 'p2', 'p3', 'k1', 'k2', 'k3', 'k4'], edges),
      true,
    );
    const rows = new Map<number, typeof result.cards>();
    for (const c of result.cards) rows.set(c.y, [...(rows.get(c.y) ?? []), c]);
    for (const row of rows.values()) {
      const sorted = [...row].sort((l, r) => l.x - r.x);
      sorted.slice(1).forEach((card, index) => {
        const previous = sorted[index];
        expect(previous).toBeDefined();
        if (previous) {
          expect(card.x - card.width / 2).toBeGreaterThanOrEqual(previous.x + previous.width / 2);
        }
      });
    }
    expect(result.bounds.minX).toBeLessThan(0);
    expect(result.bounds.maxX).toBeGreaterThan(0);
  });
});
