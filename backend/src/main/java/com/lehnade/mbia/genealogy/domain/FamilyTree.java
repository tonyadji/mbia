package com.lehnade.mbia.genealogy.domain;

import java.util.List;
import java.util.Objects;

/**
 * The local graph around a focused Person (family-tree-ux.md §6, openapi {@code getFamilyTree}):
 * ACTIVE Persons only, and the ACTIVE relationships among them. Nodes start with the focus, then
 * follow birth, creation and id; edges follow relationship creation, then id (OQ-015).
 */
public record FamilyTree(PersonId focus, List<Node> nodes, List<Edge> edges) {

    /**
     * @param hasMoreParents the Person has ACTIVE parents that are not nodes of this tree
     * @param hasMoreChildren the Person has ACTIVE children that are not nodes of this tree
     */
    public record Node(Person person, boolean hasMoreParents, boolean hasMoreChildren) {

        public Node {
            Objects.requireNonNull(person, "person");
        }
    }

    /** An explicit relationship, as stored: {@code PARENT_OF} goes from the parent to the child. */
    public record Edge(RelationshipId id, RelationshipType type, PersonId source, PersonId target, long version) {

        public Edge {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    public FamilyTree {
        Objects.requireNonNull(focus, "focus");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
