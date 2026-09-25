import type { FamilyTree, TreeNode } from './useFamilyTree';

/** The relatives of a Person, grouped as in the profile Family section (SCREEN-005). */
export interface FamilySections {
  parents: TreeNode[];
  partners: TreeNode[];
  children: TreeNode[];
  siblings: TreeNode[];
}

/**
 * Groups the tree around `personId`: parents and children from `PARENT_OF`, partners from
 * `PARTNER_OF` in relationship creation order (family-tree-ux.md §6.1), siblings as the other
 * children of the parents (half-siblings included). Other groups keep the order of the nodes
 * (birth, then creation, OQ-015). Empty when the tree is not centred on `personId`.
 */
export function familySections(tree: FamilyTree, personId: string): FamilySections {
  const sections: FamilySections = { parents: [], partners: [], children: [], siblings: [] };
  if (tree.focusPersonId !== personId) return sections;

  const byId = new Map(tree.nodes.map((node) => [node.id, node]));
  const parentIds = new Set<string>();
  const childIds = new Set<string>();
  for (const edge of tree.edges) {
    if (edge.type !== 'PARENT_OF') continue;
    if (edge.targetPersonId === personId) parentIds.add(edge.sourcePersonId);
    if (edge.sourcePersonId === personId) childIds.add(edge.targetPersonId);
  }
  const siblingIds = new Set<string>();
  for (const edge of tree.edges) {
    if (
      edge.type === 'PARENT_OF' &&
      parentIds.has(edge.sourcePersonId) &&
      edge.targetPersonId !== personId
    ) {
      siblingIds.add(edge.targetPersonId);
    }
  }

  for (const edge of tree.edges) {
    if (edge.type !== 'PARTNER_OF') continue;
    const otherId =
      edge.sourcePersonId === personId
        ? edge.targetPersonId
        : edge.targetPersonId === personId
          ? edge.sourcePersonId
          : undefined;
    const partner = otherId === undefined ? undefined : byId.get(otherId);
    if (partner) sections.partners.push(partner);
  }
  for (const node of tree.nodes) {
    if (parentIds.has(node.id)) sections.parents.push(node);
    else if (childIds.has(node.id)) sections.children.push(node);
    else if (siblingIds.has(node.id)) sections.siblings.push(node);
  }
  return sections;
}
