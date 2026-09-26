package com.lehnade.mbia.genealogy.api;

import com.lehnade.mbia.api.generated.RelationshipsApi;
import com.lehnade.mbia.api.generated.model.ArchivedRelationshipResponse;
import com.lehnade.mbia.api.generated.model.CreateRelationshipRequest;
import com.lehnade.mbia.api.generated.model.Gender;
import com.lehnade.mbia.api.generated.model.PersonStatus;
import com.lehnade.mbia.api.generated.model.PersonSummary;
import com.lehnade.mbia.api.generated.model.RelationshipResponse;
import com.lehnade.mbia.api.generated.model.RelationshipStatus;
import com.lehnade.mbia.api.generated.model.RelationshipType;
import com.lehnade.mbia.api.generated.model.RelationshipWarning;
import com.lehnade.mbia.api.generated.model.RelationshipWarningCode;
import com.lehnade.mbia.genealogy.application.ProfilePictureUrls;
import com.lehnade.mbia.genealogy.application.archiverelationship.ArchiveRelationshipCommand;
import com.lehnade.mbia.genealogy.application.archiverelationship.ArchiveRelationshipUseCase;
import com.lehnade.mbia.genealogy.application.createrelationship.CreateRelationshipCommand;
import com.lehnade.mbia.genealogy.application.createrelationship.CreateRelationshipUseCase;
import com.lehnade.mbia.genealogy.application.createrelationship.CreatedRelationship;
import com.lehnade.mbia.genealogy.application.listarchivedpersonrelationships.ListArchivedPersonRelationshipsUseCase;
import com.lehnade.mbia.genealogy.application.restorerelationship.RestoreRelationshipCommand;
import com.lehnade.mbia.genealogy.application.restorerelationship.RestoreRelationshipUseCase;
import com.lehnade.mbia.genealogy.application.restorerelationship.RestoredRelationship;
import com.lehnade.mbia.genealogy.domain.ArchivedRelationship;
import com.lehnade.mbia.genealogy.domain.FamilyRelationship;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.shared.api.web.ETags;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Explicit relationships of a Family: creation, removal, restoration and the removed ones of a Person. */
@RestController
class RelationshipsController implements RelationshipsApi {

    private final CreateRelationshipUseCase createRelationship;
    private final ArchiveRelationshipUseCase archiveRelationship;
    private final RestoreRelationshipUseCase restoreRelationship;
    private final ListArchivedPersonRelationshipsUseCase listArchivedPersonRelationships;
    private final ProfilePictureUrls profilePictureUrls;

    RelationshipsController(CreateRelationshipUseCase createRelationship,
            ArchiveRelationshipUseCase archiveRelationship, RestoreRelationshipUseCase restoreRelationship,
            ListArchivedPersonRelationshipsUseCase listArchivedPersonRelationships,
            ProfilePictureUrls profilePictureUrls) {
        this.createRelationship = createRelationship;
        this.archiveRelationship = archiveRelationship;
        this.restoreRelationship = restoreRelationship;
        this.listArchivedPersonRelationships = listArchivedPersonRelationships;
        this.profilePictureUrls = profilePictureUrls;
    }

    @Override
    public ResponseEntity<RelationshipResponse> createRelationship(UUID familyId, CreateRelationshipRequest request) {
        CreatedRelationship created = createRelationship.create(new CreateRelationshipCommand(familyId,
                com.lehnade.mbia.genealogy.domain.RelationshipType.valueOf(request.getType().name()),
                request.getSourcePersonId(), request.getTargetPersonId(),
                Boolean.TRUE.equals(request.getConfirmWarnings())));
        FamilyRelationship relationship = created.relationship();
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(ETags.of(relationship.version()))
                .body(toResponse(relationship, created.warnings()));
    }

    @Override
    public ResponseEntity<Void> archiveRelationship(String ifMatch, UUID familyId, UUID relationshipId) {
        archiveRelationship.archive(
                new ArchiveRelationshipCommand(familyId, relationshipId, ETags.parseIfMatch(ifMatch)));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RelationshipResponse> restoreRelationship(String ifMatch, UUID familyId,
            UUID relationshipId) {
        RestoredRelationship restored = restoreRelationship.restore(
                new RestoreRelationshipCommand(familyId, relationshipId, ETags.parseIfMatch(ifMatch)));
        FamilyRelationship relationship = restored.relationship();
        return ResponseEntity.ok()
                .eTag(ETags.of(relationship.version()))
                .body(toResponse(relationship, restored.warnings()));
    }

    @Override
    public ResponseEntity<List<ArchivedRelationshipResponse>> listArchivedPersonRelationships(UUID familyId,
            UUID personId) {
        return ResponseEntity.ok(listArchivedPersonRelationships.list(familyId, personId).stream()
                .map(this::toResponse)
                .toList());
    }

    private static RelationshipResponse toResponse(FamilyRelationship relationship,
            List<com.lehnade.mbia.genealogy.domain.RelationshipWarning> domainWarnings) {
        List<RelationshipWarning> warnings = domainWarnings.stream()
                .map(warning -> new RelationshipWarning(RelationshipWarningCode.fromValue(warning.code().name()))
                        .context(Map.of("parentBirthYear", warning.parentBirthYear(),
                                "childBirthYear", warning.childBirthYear())))
                .toList();
        return new RelationshipResponse(relationship.id().value(), relationship.familyId(),
                RelationshipType.fromValue(relationship.type().name()), relationship.source().value(),
                relationship.target().value(), RelationshipStatus.fromValue(relationship.status().name()),
                warnings, relationship.version(), relationship.createdAt().atOffset(ZoneOffset.UTC));
    }

    private ArchivedRelationshipResponse toResponse(ArchivedRelationship archived) {
        FamilyRelationship relationship = archived.relationship();
        return new ArchivedRelationshipResponse(relationship.id().value(),
                RelationshipType.fromValue(relationship.type().name()), relationship.source().value(),
                relationship.target().value(), relationship.version(),
                relationship.archivedAt().atOffset(ZoneOffset.UTC), toSummary(archived.relatedPerson()));
    }

    private PersonSummary toSummary(Person person) {
        PersonDetails details = person.details();
        return new PersonSummary(person.id().value(), person.familyId(), details.firstName(),
                Gender.fromValue(details.gender().name()), PersonApiMapping.toApi(details.birth()),
                details.deceased(), PersonApiMapping.toApi(details.death()),
                PersonStatus.fromValue(person.status().name()), person.version())
                .middleNames(details.middleNames())
                .lastName(details.lastName())
                .preferredName(details.preferredName())
                .displayName(details.displayName())
                .profilePictureUrl(profilePictureUrls.of(person))
                .linkedUserId(person.linkedUserId().orElse(null));
    }
}
