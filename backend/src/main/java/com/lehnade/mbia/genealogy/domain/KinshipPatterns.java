package com.lehnade.mbia.genealogy.domain;

import static com.lehnade.mbia.genealogy.domain.KinshipRelation.CHILD;
import static com.lehnade.mbia.genealogy.domain.KinshipRelation.PARENT;
import static com.lehnade.mbia.genealogy.domain.KinshipRelation.PARTNER;

import java.util.List;
import java.util.Map;

/**
 * The supported concise patterns of genealogy.md §9 and their labels, gendered by the target
 * Person (localization-and-kinship-labels.md §2). Any other path is {@code RELATED}: no
 * Nth-degree cousin, in-law or step relation is invented.
 */
public final class KinshipPatterns {

    /** Neutral, male and female code of each supported pattern. */
    private record Codes(KinshipCode neutral, KinshipCode male, KinshipCode female) {

        KinshipCode forGender(Gender gender) {
            return switch (gender) {
                case MALE -> male;
                case FEMALE -> female;
                case OTHER, UNKNOWN -> neutral;
            };
        }
    }

    private static final Map<List<KinshipRelation>, Codes> PATTERNS = Map.of(
            List.of(), same(KinshipCode.SELF),
            List.of(PARENT), new Codes(KinshipCode.PARENT, KinshipCode.FATHER, KinshipCode.MOTHER),
            List.of(CHILD), new Codes(KinshipCode.CHILD, KinshipCode.SON, KinshipCode.DAUGHTER),
            List.of(PARENT, PARENT),
            new Codes(KinshipCode.GRANDPARENT, KinshipCode.GRANDFATHER, KinshipCode.GRANDMOTHER),
            List.of(CHILD, CHILD),
            new Codes(KinshipCode.GRANDCHILD, KinshipCode.GRANDSON, KinshipCode.GRANDDAUGHTER),
            List.of(PARENT, CHILD), new Codes(KinshipCode.SIBLING, KinshipCode.BROTHER, KinshipCode.SISTER),
            List.of(PARENT, PARENT, CHILD),
            new Codes(KinshipCode.PARENT_SIBLING, KinshipCode.UNCLE, KinshipCode.AUNT),
            List.of(PARENT, CHILD, CHILD),
            new Codes(KinshipCode.SIBLING_CHILD, KinshipCode.NEPHEW, KinshipCode.NIECE),
            List.of(PARENT, PARENT, CHILD, CHILD), same(KinshipCode.FIRST_COUSIN),
            List.of(PARTNER), same(KinshipCode.PARTNER));

    private KinshipPatterns() {}

    /** @return the code of an existing path, whose last Person has {@code targetGender} */
    public static KinshipCode codeOf(List<KinshipPathStep> path, Gender targetGender) {
        List<KinshipRelation> relations = path.stream().map(KinshipPathStep::relation).toList();
        Codes codes = PATTERNS.get(relations);
        return codes == null ? KinshipCode.RELATED : codes.forGender(targetGender);
    }

    private static Codes same(KinshipCode code) {
        return new Codes(code, code, code);
    }
}
