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
import com.lehnade.mbia.family.application.getfamily.GetFamilyUseCase;
import com.lehnade.mbia.family.application.listmyfamilies.ListMyFamiliesUseCase;
import com.lehnade.mbia.family.application.updatefamily.UpdateFamilyCommand;
import com.lehnade.mbia.family.application.updatefamily.UpdateFamilyUseCase;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import com.lehnade.mbia.shared.api.web.ETags;
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
    private final GetFamilyUseCase getFamily;
    private final UpdateFamilyUseCase updateFamily;

    FamiliesController(CurrentUserAccessor currentUserAccessor, CreateFamilyUseCase createFamily,
            ListMyFamiliesUseCase listMyFamilies, GetFamilyUseCase getFamily, UpdateFamilyUseCase updateFamily) {
        this.currentUserAccessor = currentUserAccessor;
        this.createFamily = createFamily;
        this.listMyFamilies = listMyFamilies;
        this.getFamily = getFamily;
        this.updateFamily = updateFamily;
    }

    @Override
    public ResponseEntity<FamilyResponse> createFamily(CreateFamilyRequest request) {
        FamilyView family = createFamily.create(
                new CreateFamilyCommand(currentUserAccessor.currentUser().id(), request.getName()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(ETags.of(family.version()))
                .body(toResponse(family));
    }

    @Override
    public ResponseEntity<List<FamilySummary>> listMyFamilies() {
        return ResponseEntity.ok(listMyFamilies.list(currentUserAccessor.currentUser().id()).stream()
                .map(FamiliesController::toSummary)
                .toList());
    }

    @Override
    public ResponseEntity<FamilyResponse> getFamily(UUID familyId) {
        FamilyView family = getFamily.get(familyId);
        return ResponseEntity.ok().eTag(ETags.of(family.version())).body(toResponse(family));
    }

    @Override
    public ResponseEntity<FamilyResponse> updateFamily(String ifMatch, UUID familyId, UpdateFamilyRequest request) {
        FamilyView family = updateFamily.update(
                new UpdateFamilyCommand(familyId, ETags.parseIfMatch(ifMatch), request.getName()));
        return ResponseEntity.ok().eTag(ETags.of(family.version())).body(toResponse(family));
    }

    private static FamilyResponse toResponse(FamilyView family) {
        return new FamilyResponse(family.id(), family.name(), toRole(family), toStats(family), family.version(),
                toDateTime(family.createdAt()), toDateTime(family.updatedAt()))
                .myLinkedPersonId(family.myLinkedPersonId());
    }

    private static FamilySummary toSummary(FamilyView family) {
        return new FamilySummary(family.id(), family.name(), toRole(family), toStats(family))
                .myLinkedPersonId(family.myLinkedPersonId());
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
