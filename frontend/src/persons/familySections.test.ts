import { familySections, sectionLink } from './familySections';
import type { FamilyTree, TreeNode } from './useFamilyTree';

function node(id: string): TreeNode {
  return {
    id,
    familyId: 'f',
    firstName: id,
    gender: 'UNKNOWN',
    birth: { precision: 'UNKNOWN' },
    isDeceased: false,
    death: { precision: 'UNKNOWN' },
    status: 'ACTIVE',
    version: 0,
    hasMoreParents: false,
    hasMoreChildren: false,
  };
}

function edge(type: 'PARENT_OF' | 'PARTNER_OF', source: string, target: string) {
  return {
    relationshipId: `${source}-${target}`,
    type,
    sourcePersonId: source,
    targetPersonId: target,
    version: 0,
  };
}

const ids = (nodes: TreeNode[]) => nodes.map((n) => n.id);

describe('familySections', () => {
  // Nodes in server order (OQ-015): focus, then birth, creation, id.
  const tree: FamilyTree = {
    focusPersonId: 'tony',
    nodes: ['tony', 'andre', 'marie', 'awa', 'samuel', 'ines', 'chloe', 'eric', 'alex'].map(node),
    edges: [
      edge('PARENT_OF', 'marie', 'tony'),
      edge('PARENT_OF', 'andre', 'tony'),
      edge('PARTNER_OF', 'andre', 'marie'),
      edge('PARENT_OF', 'marie', 'awa'),
      edge('PARENT_OF', 'andre', 'awa'),
      edge('PARENT_OF', 'andre', 'samuel'),
      edge('PARTNER_OF', 'chloe', 'tony'),
      edge('PARTNER_OF', 'ines', 'tony'),
      edge('PARENT_OF', 'tony', 'eric'),
      edge('PARENT_OF', 'chloe', 'eric'),
      edge('PARENT_OF', 'tony', 'alex'),
    ],
  };

  it('groups the relatives of the focus', () => {
    const sections = familySections(tree, 'tony');

    expect(ids(sections.parents)).toEqual(['andre', 'marie']);
    expect(ids(sections.children)).toEqual(['eric', 'alex']);
    expect(ids(sections.siblings)).toEqual(['awa', 'samuel']);
  });

  it('orders partners by relationship creation, whatever the node order', () => {
    expect(ids(familySections(tree, 'tony').partners)).toEqual(['chloe', 'ines']);
  });

  it('counts a half-sibling through one shared parent', () => {
    expect(ids(familySections(tree, 'tony').siblings)).toContain('samuel');
  });

  it('is empty when the tree is centred on another Person', () => {
    const sections = familySections(tree, 'marie');

    expect([sections.parents, sections.partners, sections.children, sections.siblings]).toEqual([
      [],
      [],
      [],
      [],
    ]);
  });
});

describe('sectionLink', () => {
  const tree: FamilyTree = {
    focusPersonId: 'tony',
    nodes: ['tony', 'marie', 'awa', 'chloe', 'eric'].map(node),
    edges: [
      edge('PARENT_OF', 'marie', 'tony'),
      edge('PARENT_OF', 'marie', 'awa'),
      edge('PARTNER_OF', 'chloe', 'tony'),
      edge('PARENT_OF', 'tony', 'eric'),
    ],
  };

  it('is the relationship that makes the relative a parent, partner or child', () => {
    expect(sectionLink(tree, 'parents', 'tony', 'marie')?.relationshipId).toBe('marie-tony');
    expect(sectionLink(tree, 'partners', 'tony', 'chloe')?.relationshipId).toBe('chloe-tony');
    expect(sectionLink(tree, 'children', 'tony', 'eric')?.relationshipId).toBe('tony-eric');
  });

  it('keeps the direction of PARENT_OF', () => {
    expect(sectionLink(tree, 'children', 'tony', 'marie')).toBeUndefined();
    expect(sectionLink(tree, 'parents', 'tony', 'eric')).toBeUndefined();
  });

  it('has no relationship for a sibling', () => {
    expect(sectionLink(tree, 'siblings', 'tony', 'awa')).toBeUndefined();
  });
});
