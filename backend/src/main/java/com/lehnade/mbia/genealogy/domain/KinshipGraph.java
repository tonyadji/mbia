package com.lehnade.mbia.genealogy.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The explicit relationships a kinship path may use, and the breadth-first search of
 * genealogy.md §9. Among the shortest paths it prefers those made only of {@code PARENT} /
 * {@code CHILD} steps, then the one whose sequence of Person ids comes first in
 * {@link PersonId#ORDER}, so that results never depend on the order of the edges
 * (person-relationships-collaboration.md §10).
 */
public final class KinshipGraph {

    /** An explicit relationship, as stored: {@code PARENT_OF} goes from the parent to the child. */
    public record Edge(RelationshipType type, PersonId source, PersonId target) {

        public Edge {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    private record Neighbour(PersonId person, KinshipRelation relation) {}

    /** A path from the reference Person: {@code persons} excludes the reference Person itself. */
    private record Path(List<PersonId> persons, List<KinshipRelation> relations) {

        static final Path EMPTY = new Path(List.of(), List.of());

        Path then(Neighbour next) {
            List<PersonId> nextPersons = new ArrayList<>(persons);
            nextPersons.add(next.person());
            List<KinshipRelation> nextRelations = new ArrayList<>(relations);
            nextRelations.add(next.relation());
            return new Path(nextPersons, nextRelations);
        }

        boolean onlyParentAndChild() {
            return !relations.contains(KinshipRelation.PARTNER);
        }

        /** Paths of equal length: first by Person ids, then PARENT/CHILD before PARTNER on a same pair. */
        boolean precedes(Path other) {
            for (int i = 0; i < persons.size(); i++) {
                int byPerson = PersonId.ORDER.compare(persons.get(i), other.persons.get(i));
                if (byPerson != 0) {
                    return byPerson < 0;
                }
            }
            for (int i = 0; i < relations.size(); i++) {
                int byRelation = relations.get(i).compareTo(other.relations.get(i));
                if (byRelation != 0) {
                    return byRelation < 0;
                }
            }
            return false;
        }
    }

    private final Map<PersonId, List<Neighbour>> neighbours = new HashMap<>();

    private KinshipGraph(Collection<Edge> edges) {
        for (Edge edge : edges) {
            switch (edge.type()) {
                case PARENT_OF -> {
                    link(edge.target(), edge.source(), KinshipRelation.PARENT);
                    link(edge.source(), edge.target(), KinshipRelation.CHILD);
                }
                case PARTNER_OF -> {
                    link(edge.source(), edge.target(), KinshipRelation.PARTNER);
                    link(edge.target(), edge.source(), KinshipRelation.PARTNER);
                }
            }
        }
    }

    public static KinshipGraph of(Collection<Edge> edges) {
        return new KinshipGraph(edges);
    }

    /**
     * @param targetGender the gender of {@code to}, which genders the code
     * @return what {@code to} is to {@code from}, with the path from {@code from} to {@code to}
     */
    public Kinship kinship(PersonId from, PersonId to, Gender targetGender) {
        return shortestPath(from, to)
                .map(path -> new Kinship(KinshipPatterns.codeOf(path, targetGender), path))
                .orElseGet(Kinship::noneKnown);
    }

    /** @return the preferred shortest path from {@code from} to {@code to}, empty when none exists */
    public Optional<List<KinshipPathStep>> shortestPath(PersonId from, PersonId to) {
        if (from.equals(to)) {
            return Optional.of(List.of());
        }
        Map<PersonId, Integer> distance = new HashMap<>();
        // Best shortest path to each reached Person: overall, and made only of PARENT/CHILD steps.
        Map<PersonId, Path> bestAny = new HashMap<>();
        Map<PersonId, Path> bestParentChild = new HashMap<>();
        distance.put(from, 0);
        bestAny.put(from, Path.EMPTY);
        bestParentChild.put(from, Path.EMPTY);

        Set<PersonId> layer = Set.of(from);
        int depth = 0;
        while (!layer.isEmpty() && !distance.containsKey(to)) {
            Set<PersonId> nextLayer = new LinkedHashSet<>();
            for (PersonId person : layer) {
                for (Neighbour neighbour : neighbours.getOrDefault(person, List.of())) {
                    PersonId next = neighbour.person();
                    Integer known = distance.putIfAbsent(next, depth + 1);
                    if (known != null && known != depth + 1) {
                        continue;
                    }
                    nextLayer.add(next);
                    keepBest(bestAny, next, bestAny.get(person).then(neighbour));
                    Path parentChild = bestParentChild.get(person);
                    if (parentChild != null && neighbour.relation() != KinshipRelation.PARTNER) {
                        keepBest(bestParentChild, next, parentChild.then(neighbour));
                    }
                }
            }
            layer = nextLayer;
            depth++;
        }
        Path best = bestParentChild.getOrDefault(to, bestAny.get(to));
        return Optional.ofNullable(best).map(path -> steps(from, path));
    }

    private void link(PersonId from, PersonId to, KinshipRelation relation) {
        neighbours.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new Neighbour(to, relation));
    }

    private static void keepBest(Map<PersonId, Path> best, PersonId person, Path candidate) {
        Path current = best.get(person);
        if (current == null || candidate.precedes(current)) {
            best.put(person, candidate);
        }
    }

    private static List<KinshipPathStep> steps(PersonId from, Path path) {
        List<KinshipPathStep> steps = new ArrayList<>();
        PersonId previous = from;
        for (int i = 0; i < path.persons().size(); i++) {
            steps.add(new KinshipPathStep(previous, path.persons().get(i), path.relations().get(i)));
            previous = path.persons().get(i);
        }
        return List.copyOf(steps);
    }
}
