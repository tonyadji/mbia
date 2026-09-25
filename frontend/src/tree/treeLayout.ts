import { familySections } from '../persons/familySections';
import type { FamilyTree, TreeNode } from '../persons/useFamilyTree';

/** Sizes of the drawing, in CSS pixels at zoom 1. */
export const CARD = { width: 104, height: 128 } as const;
export const FOCUS_CARD = { width: 124, height: 164 } as const;
export const ADD_SIZE = 48;
const GAP = 16;
const GROUP_GAP = 40;
const ROW_GAP = 72;
const CHIP = { height: 32, maxWidth: 176 } as const;
const ROW_CENTERS = [
  FOCUS_CARD.height / 2,
  FOCUS_CARD.height * 1.5 + ROW_GAP,
  FOCUS_CARD.height * 2.5 + ROW_GAP * 2,
] as const;

export type CardRole = 'parent' | 'focus' | 'partner' | 'child';

/** A Person card, placed by its centre. */
export interface PlacedCard {
  node: TreeNode;
  role: CardRole;
  x: number;
  y: number;
  width: number;
  height: number;
  /** "↑" on a parent whose own parents exist, "↓" on a child whose own children exist. */
  indicator: 'up' | 'down' | null;
}

/** An "Add…" affordance, placed by its centre. */
export interface PlacedAdd {
  relation: 'parent' | 'partner' | 'child';
  x: number;
  y: number;
}

/** The children of the focus with the same other parent (`null`: unknown or not a partner). */
export interface ChildGroup {
  otherParentId: string | null;
  children: TreeNode[];
}

export type Segment = readonly [x1: number, y1: number, x2: number, y2: number];

export interface TreeLayout {
  focus: TreeNode;
  parents: TreeNode[];
  /** Partners left to right, as drawn in row 2. */
  partners: TreeNode[];
  childGroups: ChildGroup[];
  siblings: TreeNode[];
  cards: PlacedCard[];
  adds: PlacedAdd[];
  lines: Segment[];
  /**
   * Where the `Siblings (n)` chip goes: above the focus, right of the line from the parents (its
   * left edge and vertical centre); null without siblings.
   */
  siblingsChip: { left: number; y: number } | null;
  bounds: { minX: number; maxX: number; height: number };
}

/** Left edges of items of the given widths, placed side by side and centred on `center`. */
function centredLefts(widths: number[], gaps: number[], center: number): number[] {
  const total = widths.reduce((sum, w) => sum + w, 0) + gaps.reduce((sum, g) => sum + g, 0);
  const lefts: number[] = [];
  let left = center - total / 2;
  widths.forEach((width, index) => {
    lefts.push(left);
    left += width + (gaps[index] ?? 0);
  });
  return lefts;
}

/**
 * The fixed three-row layout of family-tree-ux.md §6.1 around the focus of a depth-1 tree: parents,
 * then the focus with its partners (first on the right, next on the left, alternating, by
 * relationship creation), then the children grouped by their other parent (one group per partner,
 * left to right as in row 2, OQ-019, then the others). Siblings are not drawn: they only feed the
 * chip. The focus is centred on x = 0. `canAdd` shows the "Add…" affordances.
 */
export function layoutTree(tree: FamilyTree, { canAdd }: { canAdd: boolean }): TreeLayout | null {
  const focusId = tree.focusPersonId;
  const focus = tree.nodes.find((node) => node.id === focusId);
  if (focusId == null || focus === undefined) return null;
  const sections = familySections(tree, focusId);
  const parentIds = new Set(sections.parents.map((node) => node.id));
  const childIds = new Set(sections.children.map((node) => node.id));
  const byCreation = sections.partners.filter(
    (node, index, all) =>
      !parentIds.has(node.id) &&
      !childIds.has(node.id) &&
      all.findIndex((other) => other.id === node.id) === index,
  );

  const cards: PlacedCard[] = [];
  const adds: PlacedAdd[] = [];
  const lines: Segment[] = [];
  const card = (node: TreeNode, role: CardRole, x: number, y: number): PlacedCard => {
    const size = role === 'focus' ? FOCUS_CARD : CARD;
    const placed: PlacedCard = {
      node,
      role,
      x,
      y,
      width: size.width,
      height: size.height,
      indicator:
        role === 'parent' && node.hasMoreParents
          ? 'up'
          : role === 'child' && node.hasMoreChildren
            ? 'down'
            : null,
    };
    cards.push(placed);
    return placed;
  };

  // Row 2: the focus, partners alternating right then left.
  const [, row2, row3] = ROW_CENTERS;
  const focusCard = card(focus, 'focus', 0, row2);
  const right: TreeNode[] = [];
  const left: TreeNode[] = [];
  byCreation.forEach((node, index) => (index % 2 === 0 ? right : left).push(node));
  const partnerCards = new Map<string, PlacedCard>();
  const edgeOf = (x: number, width: number, side: 1 | -1) => x + (side * width) / 2;
  for (const [side, nodes] of [
    [1, right],
    [-1, left],
  ] as const) {
    let edge = edgeOf(0, FOCUS_CARD.width, side);
    for (const node of nodes) {
      const x = edge + side * (GAP + CARD.width / 2);
      partnerCards.set(node.id, card(node, 'partner', x, row2));
      edge = edgeOf(x, CARD.width, side);
      lines.push([0, row2, x, row2]);
    }
    if (side === 1 && canAdd)
      adds.push({ relation: 'partner', x: edge + GAP + ADD_SIZE / 2, y: row2 });
  }
  const partners = [...left].reverse().concat(right);

  // Row 1: all parents side by side, then the "Add a parent" slot.
  const [row1] = ROW_CENTERS;
  const parentWidths: number[] = sections.parents.map(() => CARD.width);
  const showParentSlot = canAdd && sections.parents.length < 2;
  if (showParentSlot) parentWidths.push(ADD_SIZE);
  const parentLefts = centredLefts(
    parentWidths,
    parentWidths.slice(1).map(() => GAP),
    0,
  );
  const parentCards = sections.parents.map((node, index) =>
    card(node, 'parent', (parentLefts[index] ?? 0) + CARD.width / 2, row1),
  );
  if (showParentSlot) {
    adds.push({ relation: 'parent', x: (parentLefts.at(-1) ?? 0) + ADD_SIZE / 2, y: row1 });
  }
  const partnerEdges = tree.edges.filter((edge) => edge.type === 'PARTNER_OF');
  const arePartners = (a: string, b: string) =>
    partnerEdges.some(
      (edge) =>
        (edge.sourcePersonId === a && edge.targetPersonId === b) ||
        (edge.sourcePersonId === b && edge.targetPersonId === a),
    );
  parentCards.forEach((a, index) => {
    for (const b of parentCards.slice(index + 1)) {
      if (arePartners(a.node.id, b.node.id)) lines.push([a.x, row1, b.x, row1]);
    }
  });
  if (parentCards.length > 0) {
    const barY = row1 + CARD.height / 2 + 24;
    const xs = parentCards.map((placed) => placed.x).concat(0);
    for (const placed of parentCards)
      lines.push([placed.x, row1 + CARD.height / 2, placed.x, barY]);
    lines.push([Math.min(...xs), barY, Math.max(...xs), barY]);
    lines.push([0, barY, 0, row2 - FOCUS_CARD.height / 2]);
  }

  // Row 3: one group per partner (left to right), then the children with another parent.
  const parentsOfChild = new Map<string, Set<string>>();
  for (const edge of tree.edges) {
    if (edge.type !== 'PARENT_OF' || !childIds.has(edge.targetPersonId)) continue;
    const set = parentsOfChild.get(edge.targetPersonId) ?? new Set<string>();
    set.add(edge.sourcePersonId);
    parentsOfChild.set(edge.targetPersonId, set);
  }
  const childGroups: ChildGroup[] = partners.map((partner) => ({
    otherParentId: partner.id,
    children: [],
  }));
  const others: ChildGroup = { otherParentId: null, children: [] };
  for (const child of sections.children) {
    const parents = parentsOfChild.get(child.id);
    const group = childGroups.find(
      (candidate) => candidate.otherParentId && parents?.has(candidate.otherParentId),
    );
    (group ?? others).children.push(child);
  }
  childGroups.push(others);
  const groups = childGroups.filter((group) => group.children.length > 0);

  const widths: number[] = [];
  const gaps: number[] = [];
  groups.forEach((group, groupIndex) => {
    group.children.forEach((_, index) => {
      widths.push(CARD.width);
      gaps.push(index === group.children.length - 1 ? GROUP_GAP : GAP);
    });
    if (groupIndex === groups.length - 1) gaps[gaps.length - 1] = GAP;
  });
  if (canAdd) widths.push(ADD_SIZE);
  const lefts = centredLefts(widths, gaps.slice(0, widths.length - 1), 0);
  let slot = 0;
  const topOfRow3 = row3 - CARD.height / 2;
  groups.forEach((group, groupIndex) => {
    const placed = group.children.map((child) =>
      card(child, 'child', (lefts[slot++] ?? 0) + CARD.width / 2, row3),
    );
    const first = placed[0]?.x ?? 0;
    const last = placed.at(-1)?.x ?? 0;
    const partnerCard = group.otherParentId ? partnerCards.get(group.otherParentId) : undefined;
    // A couple's children hang from the gap next to the partner; the others from the focus.
    const anchorX = partnerCard
      ? partnerCard.x - Math.sign(partnerCard.x) * (CARD.width / 2 + GAP / 2)
      : 0;
    const anchorY = partnerCard ? row2 : row2 + FOCUS_CARD.height / 2;
    const elbowY = row2 + FOCUS_CARD.height / 2 + 16 + groupIndex * 10;
    const barY = topOfRow3 - 20;
    const middle = (first + last) / 2;
    lines.push([anchorX, anchorY, anchorX, elbowY]);
    lines.push([anchorX, elbowY, middle, elbowY]);
    lines.push([middle, elbowY, middle, barY]);
    lines.push([first, barY, last, barY]);
    for (const child of placed) lines.push([child.x, barY, child.x, topOfRow3]);
  });
  if (canAdd) adds.push({ relation: 'child', x: (lefts.at(-1) ?? 0) + ADD_SIZE / 2, y: row3 });

  const siblingsChip =
    sections.siblings.length > 0
      ? { left: 12, y: focusCard.y - FOCUS_CARD.height / 2 - CHIP.height / 2 - 8 }
      : null;
  const xs = [
    ...cards.flatMap((placed) => [placed.x - placed.width / 2, placed.x + placed.width / 2]),
    ...adds.flatMap((add) => [add.x - ADD_SIZE / 2, add.x + ADD_SIZE / 2]),
    ...(siblingsChip ? [siblingsChip.left + CHIP.maxWidth] : []),
  ];
  return {
    focus,
    parents: sections.parents,
    partners,
    childGroups: groups,
    siblings: sections.siblings,
    cards,
    adds,
    lines,
    siblingsChip,
    bounds: {
      minX: Math.min(...xs),
      maxX: Math.max(...xs),
      height: row3 + FOCUS_CARD.height / 2,
    },
  };
}
