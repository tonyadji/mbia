package com.lehnade.mbia.genealogy.api;

import com.lehnade.mbia.api.generated.TreeApi;
import com.lehnade.mbia.api.generated.model.KinshipCode;
import com.lehnade.mbia.api.generated.model.KinshipPathStep;
import com.lehnade.mbia.api.generated.model.KinshipResponse;
import com.lehnade.mbia.api.generated.model.TreeResponse;
import com.lehnade.mbia.genealogy.application.resolvekinship.ResolveKinshipUseCase;
import com.lehnade.mbia.genealogy.domain.Kinship;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tree and kinship reads of a Family. {@code getFamilyTree} (PR-22) answers like a route that does
 * not exist yet ({@code RESOURCE_NOT_FOUND}).
 */
@RestController
class TreeController implements TreeApi {

    private final ResolveKinshipUseCase resolveKinship;

    TreeController(ResolveKinshipUseCase resolveKinship) {
        this.resolveKinship = resolveKinship;
    }

    @Override
    public ResponseEntity<KinshipResponse> getKinship(UUID from, UUID to, UUID familyId) {
        Kinship kinship = resolveKinship.resolve(familyId, from, to);
        return ResponseEntity.ok(new KinshipResponse(from, to, KinshipCode.fromValue(kinship.code().name()),
                kinship.path().stream()
                        .map(step -> new KinshipPathStep(step.from().value(), step.to().value(),
                                KinshipPathStep.RelationEnum.fromValue(step.relation().name())))
                        .toList()));
    }

    @Override
    public ResponseEntity<TreeResponse> getFamilyTree(UUID familyId, UUID focusPersonId, Integer depth) {
        throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.");
    }
}
