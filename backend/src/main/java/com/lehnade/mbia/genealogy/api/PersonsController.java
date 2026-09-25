package com.lehnade.mbia.genealogy.api;

import com.lehnade.mbia.api.generated.PersonsApi;
import com.lehnade.mbia.api.generated.model.CreatePersonRequest;
import com.lehnade.mbia.api.generated.model.DatePrecision;
import com.lehnade.mbia.api.generated.model.Gender;
import com.lehnade.mbia.api.generated.model.KinshipCode;
import com.lehnade.mbia.api.generated.model.MergePersonRequest;
import com.lehnade.mbia.api.generated.model.PartialDate;
import com.lehnade.mbia.api.generated.model.PersonHistoryPage;
import com.lehnade.mbia.api.generated.model.PersonPage;
import com.lehnade.mbia.api.generated.model.PersonResponse;
import com.lehnade.mbia.api.generated.model.PersonStatus;
import com.lehnade.mbia.api.generated.model.UpdatePersonRequest;
import com.lehnade.mbia.genealogy.application.PersonView;
import com.lehnade.mbia.genealogy.application.createperson.CreatePersonCommand;
import com.lehnade.mbia.genealogy.application.createperson.CreatePersonUseCase;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.shared.api.web.ETags;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persons of a Family. Operations delivered by later Phase 2 PRs answer like a route that does
 * not exist yet ({@code RESOURCE_NOT_FOUND}).
 */
@RestController
class PersonsController implements PersonsApi {

    private final CreatePersonUseCase createPerson;

    PersonsController(CreatePersonUseCase createPerson) {
        this.createPerson = createPerson;
    }

    /** {@code profileMediaAssetId} is ignored: Persons have no photo in Phase 2 (OQ-005). */
    @Override
    public ResponseEntity<PersonResponse> createPerson(UUID familyId, CreatePersonRequest request) {
        PersonDetails details = new PersonDetails(request.getFirstName(), request.getMiddleNames(),
                request.getLastName(), request.getPreferredName(), toDomain(request.getGender()),
                toDomain(request.getBirth()), Boolean.TRUE.equals(request.getIsDeceased()),
                toDomain(request.getDeath()), request.getBiography());
        PersonView person = createPerson.create(new CreatePersonCommand(familyId, details,
                Boolean.TRUE.equals(request.getLinkToCurrentUser()),
                Boolean.TRUE.equals(request.getConfirmPossibleDuplicate())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(ETags.of(person.person().version()))
                .body(toResponse(person));
    }

    @Override
    public ResponseEntity<PersonResponse> getPerson(UUID familyId, UUID personId) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonPage> searchPersons(UUID familyId, String status, String search, Integer page,
            Integer size) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonResponse> updatePerson(String ifMatch, UUID familyId, UUID personId,
            UpdatePersonRequest request) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonResponse> claimPerson(String ifMatch, UUID familyId, UUID personId) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonResponse> unclaimPerson(String ifMatch, UUID familyId, UUID personId) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonResponse> archivePerson(String ifMatch, UUID familyId, UUID personId) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonResponse> restorePerson(String ifMatch, UUID familyId, UUID personId) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonResponse> mergePerson(UUID familyId, UUID personId, MergePersonRequest request) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<PersonHistoryPage> getPersonHistory(UUID familyId, UUID personId, Integer page,
            Integer size) {
        throw notAvailableYet();
    }

    private static DomainException notAvailableYet() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.");
    }

    private static PersonResponse toResponse(PersonView view) {
        Person person = view.person();
        PersonDetails details = person.details();
        return new PersonResponse(person.id().value(), person.familyId(), details.firstName(),
                Gender.fromValue(details.gender().name()), toApi(details.birth()), details.deceased(),
                toApi(details.death()), PersonStatus.fromValue(person.status().name()), person.version(),
                details.biography(), toDateTime(person.createdAt()), toDateTime(person.updatedAt()))
                .middleNames(details.middleNames())
                .lastName(details.lastName())
                .preferredName(details.preferredName())
                .displayName(details.displayName())
                .profilePictureUrl(null)
                .linkedUserId(person.linkedUserId().orElse(null))
                .relationshipToCurrentUser(view.relationshipToCurrentUser().map(KinshipCode::fromValue).orElse(null))
                .mergedIntoPersonId(null);
    }

    private static com.lehnade.mbia.genealogy.domain.Gender toDomain(Gender gender) {
        return gender == null ? null : com.lehnade.mbia.genealogy.domain.Gender.valueOf(gender.name());
    }

    private static com.lehnade.mbia.genealogy.domain.PartialDate toDomain(PartialDate date) {
        if (date == null) {
            return null;
        }
        return com.lehnade.mbia.genealogy.domain.PartialDate.of(
                date.getPrecision() == null ? null
                        : com.lehnade.mbia.genealogy.domain.DatePrecision.valueOf(date.getPrecision().name()),
                date.getDate(), date.getYear());
    }

    private static PartialDate toApi(com.lehnade.mbia.genealogy.domain.PartialDate date) {
        return new PartialDate(DatePrecision.fromValue(date.precision().name()))
                .date(date.date())
                .year(date.year());
    }

    private static OffsetDateTime toDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
