package com.lehnade.mbia.family.api;

import com.lehnade.mbia.api.generated.FamiliesApi;
import com.lehnade.mbia.api.generated.model.CreateFamilyRequest;
import com.lehnade.mbia.api.generated.model.FamilyResponse;
import com.lehnade.mbia.api.generated.model.FamilyStats;
import com.lehnade.mbia.api.generated.model.FamilySummary;
import com.lehnade.mbia.api.generated.model.MembershipRole;
import com.lehnade.mbia.api.generated.model.UpdateFamilyRequest;
import com.lehnade.mbia.family.application.FamilyView;
import com.lehnade.mbia.family.application.createfamily.CreateFamilyCommand;
import com.lehnade.mbia.family.application.createfamily.CreateFamilyUseCase;
import com.lehnade.mbia.family.application.listmyfamilies.ListMyFamiliesUseCase;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FamiliesController implements FamiliesApi {

    private final CurrentUserAccessor currentUserAccessor;
    private final CreateFamilyUseCase createFamily;
    private final ListMyFamiliesUseCase listMyFamilies;

    FamiliesController(CurrentUserAccessor currentUserAccessor, CreateFamilyUseCase createFamily,
            ListMyFamiliesUseCase listMyFamilies) {
        this.currentUserAccessor = currentUserAccessor;
        this.createFamily = createFamily;
        this.listMyFamilies = listMyFamilies;
    }

    @Override
    public ResponseEntity<FamilyResponse> createFamily(CreateFamilyRequest request) {
        FamilyView family = createFamily.create(
                new CreateFamilyCommand(currentUserAccessor.currentUser().id(), request.getName()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(Long.toString(family.version()))
                .body(toResponse(family));
    }

    @Override
    public ResponseEntity<List<FamilySummary>> listMyFamilies() {
        return ResponseEntity.ok(listMyFamilies.list(currentUserAccessor.currentUser().id()).stream()
                .map(FamiliesController::toSummary)
                .toList());
    }

    /** Not implemented yet: delivered by PR-14 (family access guard). */
    @Override
    public ResponseEntity<FamilyResponse> getFamily(UUID familyId) {
        throw notImplementedYet();
    }

    /** Not implemented yet: delivered by PR-14 (family access guard). */
    @Override
    public ResponseEntity<FamilyResponse> updateFamily(String ifMatch, UUID familyId, UpdateFamilyRequest request) {
        throw notImplementedYet();
    }

    /** The same 404 as a path without any handler, until PR-14. */
    private static DomainException notImplementedYet() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "No resource exists at this path.");
    }

    private static FamilyResponse toResponse(FamilyView family) {
        return new FamilyResponse(family.id(), family.name(), toRole(family), toStats(family), family.version(),
                toDateTime(family.createdAt()), toDateTime(family.updatedAt()));
    }

    private static FamilySummary toSummary(FamilyView family) {
        return new FamilySummary(family.id(), family.name(), toRole(family), toStats(family));
    }

    private static MembershipRole toRole(FamilyView family) {
        return MembershipRole.fromValue(family.myRole().name());
    }

    private static FamilyStats toStats(FamilyView family) {
        return new FamilyStats(
                Math.toIntExact(family.stats().personCount()),
                Math.toIntExact(family.stats().memoryCount()),
                Math.toIntExact(family.stats().activeMemberCount()));
    }

    private static OffsetDateTime toDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
