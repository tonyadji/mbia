package com.lehnade.mbia.genealogy.api;

import com.lehnade.mbia.api.generated.RelationshipsApi;
import com.lehnade.mbia.api.generated.model.ArchivedRelationshipResponse;
import com.lehnade.mbia.api.generated.model.CreateRelationshipRequest;
import com.lehnade.mbia.api.generated.model.RelationshipResponse;
import com.lehnade.mbia.api.generated.model.RelationshipStatus;
import com.lehnade.mbia.api.generated.model.RelationshipType;
import com.lehnade.mbia.api.generated.model.RelationshipWarning;
import com.lehnade.mbia.api.generated.model.RelationshipWarningCode;
import com.lehnade.mbia.genealogy.application.createrelationship.CreateRelationshipCommand;
import com.lehnade.mbia.genealogy.application.createrelationship.CreateRelationshipUseCase;
import com.lehnade.mbia.genealogy.application.createrelationship.CreatedRelationship;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.shared.api.web.ETags;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit relationships of a Family. Operations delivered by later Phase 2 PRs answer like a
 * route that does not exist yet ({@code RESOURCE_NOT_FOUND}).
 */
@RestController
class RelationshipsController implements RelationshipsApi {

    private final CreateRelationshipUseCase createRelationship;

    RelationshipsController(CreateRelationshipUseCase createRelationship) {
        this.createRelationship = createRelationship;
    }

    @Override
    public ResponseEntity<RelationshipResponse> createRelationship(UUID familyId, CreateRelationshipRequest request) {
        CreatedRelationship created = createRelationship.create(new CreateRelationshipCommand(familyId,
                com.lehnade.mbia.genealogy.domain.RelationshipType.valueOf(request.getType().name()),
                request.getSourcePersonId(), request.getTargetPersonId(),
                Boolean.TRUE.equals(request.getConfirmWarnings())));
        FamilyRelationship relationship = created.relationship();
        List<RelationshipWarning> warnings = created.warnings().stream()
                .map(warning -> new RelationshipWarning(RelationshipWarningCode.fromValue(warning.code().name()))
                        .context(Map.of("parentBirthYear", warning.parentBirthYear(),
                                "childBirthYear", warning.childBirthYear())))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(ETags.of(relationship.version()))
                .body(new RelationshipResponse(relationship.id().value(), relationship.familyId(),
                        RelationshipType.fromValue(relationship.type().name()), relationship.source().value(),
                        relationship.target().value(), RelationshipStatus.fromValue(relationship.status().name()),
                        warnings, relationship.version(), relationship.createdAt().atOffset(ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<Void> archiveRelationship(String ifMatch, UUID familyId, UUID relationshipId) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<RelationshipResponse> restoreRelationship(String ifMatch, UUID familyId,
            UUID relationshipId) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<List<ArchivedRelationshipResponse>> listArchivedPersonRelationships(UUID familyId,
            UUID personId) {
        throw notAvailableYet();
    }

    private static DomainException notAvailableYet() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.");
    }
}
