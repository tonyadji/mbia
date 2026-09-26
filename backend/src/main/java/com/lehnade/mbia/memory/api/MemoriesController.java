package com.lehnade.mbia.memory.api;

import com.lehnade.mbia.api.generated.MemoriesApi;
import com.lehnade.mbia.api.generated.model.ActivityActor;
import com.lehnade.mbia.api.generated.model.CreatePhotoMemoryRequest;
import com.lehnade.mbia.api.generated.model.CreateStoryMemoryRequest;
import com.lehnade.mbia.api.generated.model.MemoryPage;
import com.lehnade.mbia.api.generated.model.MemoryResponse;
import com.lehnade.mbia.api.generated.model.MemoryStatus;
import com.lehnade.mbia.api.generated.model.MemoryType;
import com.lehnade.mbia.api.generated.model.PageMeta;
import com.lehnade.mbia.api.generated.model.PersonStatus;
import com.lehnade.mbia.api.generated.model.RelatedPersonReference;
import com.lehnade.mbia.api.generated.model.UpdateMemoryRequest;
import com.lehnade.mbia.memory.application.MemoryPageView;
import com.lehnade.mbia.memory.application.MemoryView;
import com.lehnade.mbia.memory.application.archivememory.ArchiveMemoryCommand;
import com.lehnade.mbia.memory.application.archivememory.ArchiveMemoryUseCase;
import com.lehnade.mbia.memory.application.createstorymemory.CreateStoryMemoryCommand;
import com.lehnade.mbia.memory.application.createstorymemory.CreateStoryMemoryUseCase;
import com.lehnade.mbia.memory.application.getmemory.GetMemoryUseCase;
import com.lehnade.mbia.memory.application.listfamilymemories.ListFamilyMemoriesCommand;
import com.lehnade.mbia.memory.application.listfamilymemories.ListFamilyMemoriesUseCase;
import com.lehnade.mbia.memory.application.listpersonmemories.ListPersonMemoriesCommand;
import com.lehnade.mbia.memory.application.listpersonmemories.ListPersonMemoriesUseCase;
import com.lehnade.mbia.memory.application.updatememory.UpdateMemoryCommand;
import com.lehnade.mbia.memory.application.updatememory.UpdateMemoryUseCase;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.shared.api.web.ETags;
import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Memories of a Family. Only stories exist in this iteration (Phase 3 plan §3.1): photo Memories
 * wait for OQ-042 and answer like a route that does not exist yet ({@code RESOURCE_NOT_FOUND}).
 */
@RestController
class MemoriesController implements MemoriesApi {

    private final CreateStoryMemoryUseCase createStoryMemory;
    private final GetMemoryUseCase getMemory;
    private final ListFamilyMemoriesUseCase listFamilyMemories;
    private final ListPersonMemoriesUseCase listPersonMemories;
    private final UpdateMemoryUseCase updateMemory;
    private final ArchiveMemoryUseCase archiveMemory;

    MemoriesController(CreateStoryMemoryUseCase createStoryMemory, GetMemoryUseCase getMemory,
            ListFamilyMemoriesUseCase listFamilyMemories, ListPersonMemoriesUseCase listPersonMemories,
            UpdateMemoryUseCase updateMemory, ArchiveMemoryUseCase archiveMemory) {
        this.createStoryMemory = createStoryMemory;
        this.getMemory = getMemory;
        this.listFamilyMemories = listFamilyMemories;
        this.listPersonMemories = listPersonMemories;
        this.updateMemory = updateMemory;
        this.archiveMemory = archiveMemory;
    }

    @Override
    public ResponseEntity<MemoryResponse> createStoryMemory(UUID familyId, CreateStoryMemoryRequest request) {
        MemoryView memory = createStoryMemory.create(new CreateStoryMemoryCommand(familyId, request.getTitle(),
                request.getContent(), request.getRelatedPersonIds()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(ETags.of(memory.memory().version()))
                .body(toResponse(memory));
    }

    @Override
    public ResponseEntity<MemoryResponse> getMemory(UUID familyId, UUID memoryId) {
        MemoryView memory = getMemory.get(familyId, memoryId);
        return ResponseEntity.ok().eTag(ETags.of(memory.memory().version())).body(toResponse(memory));
    }

    @Override
    public ResponseEntity<MemoryResponse> createPhotoMemory(UUID familyId, CreatePhotoMemoryRequest request) {
        throw notAvailableYet();
    }

    @Override
    public ResponseEntity<MemoryPage> listFamilyMemories(UUID familyId, MemoryType type, Integer page,
            Integer size) {
        return ResponseEntity.ok(toPage(listFamilyMemories.list(new ListFamilyMemoriesCommand(familyId,
                Optional.ofNullable(type).map(MemoryType::name), page, size))));
    }

    @Override
    public ResponseEntity<MemoryPage> listPersonMemories(UUID familyId, UUID personId, Integer page,
            Integer size) {
        return ResponseEntity.ok(toPage(listPersonMemories.list(new ListPersonMemoriesCommand(familyId, personId,
                page, size))));
    }

    /** An absent or {@code null} field keeps its value (OQ-008). */
    @Override
    public ResponseEntity<MemoryResponse> updateMemory(String ifMatch, UUID familyId, UUID memoryId,
            UpdateMemoryRequest request) {
        Set<String> photoFields = new HashSet<>();
        if (request.getCaption() != null) {
            photoFields.add("caption");
        }
        if (request.getTakenAt() != null) {
            photoFields.add("takenAt");
        }
        MemoryView memory = updateMemory.update(new UpdateMemoryCommand(familyId, memoryId,
                ETags.parseIfMatch(ifMatch), Optional.ofNullable(request.getTitle()),
                Optional.ofNullable(request.getContent()), Optional.ofNullable(request.getRelatedPersonIds()),
                photoFields));
        return ResponseEntity.ok().eTag(ETags.of(memory.memory().version())).body(toResponse(memory));
    }

    @Override
    public ResponseEntity<Void> archiveMemory(String ifMatch, UUID familyId, UUID memoryId) {
        archiveMemory.archive(new ArchiveMemoryCommand(familyId, memoryId, ETags.parseIfMatch(ifMatch)));
        return ResponseEntity.noContent().build();
    }

    private static MemoryPage toPage(MemoryPageView memories) {
        return new MemoryPage(memories.items().stream().map(MemoriesController::toResponse).toList(),
                new PageMeta(memories.page(), memories.size(), memories.totalElements(), memories.totalPages()));
    }

    private static MemoryResponse toResponse(MemoryView view) {
        Memory memory = view.memory();
        return new MemoryResponse(memory.id().value(), memory.familyId(), MemoryType.valueOf(memory.type().name()),
                MemoryStatus.valueOf(memory.status().name()),
                view.relatedPersons().stream()
                        .map(person -> new RelatedPersonReference(person.id(), person.displayName(),
                                PersonStatus.valueOf(person.status().name())))
                        .toList(),
                new ActivityActor(view.createdBy().userId(), view.createdBy().displayName(),
                        view.createdBy().deleted()),
                toDateTime(memory.createdAt()), toDateTime(memory.updatedAt()), memory.version())
                .title(memory.title())
                .content(memory.content());
    }

    private static OffsetDateTime toDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static DomainException notAvailableYet() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.");
    }
}
