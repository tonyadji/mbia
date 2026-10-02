package com.lehnade.mbia.family.api;

import com.lehnade.mbia.api.generated.MembersApi;
import com.lehnade.mbia.api.generated.model.KinshipCode;
import com.lehnade.mbia.api.generated.model.MemberResponse;
import com.lehnade.mbia.api.generated.model.MembershipRole;
import com.lehnade.mbia.api.generated.model.MembershipStatus;
import com.lehnade.mbia.api.generated.model.UpdateMemberRoleRequest;
import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.MemberView;
import com.lehnade.mbia.family.application.listfamilymembers.ListFamilyMembersUseCase;
import com.lehnade.mbia.family.application.removefamilymember.RemoveFamilyMemberCommand;
import com.lehnade.mbia.family.application.removefamilymember.RemoveFamilyMemberUseCase;
import com.lehnade.mbia.family.application.updatememberrole.UpdateMemberRoleCommand;
import com.lehnade.mbia.family.application.updatememberrole.UpdateMemberRoleUseCase;
import com.lehnade.mbia.shared.api.web.ETags;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MembersController implements MembersApi {

    private final ListFamilyMembersUseCase listFamilyMembers;
    private final UpdateMemberRoleUseCase updateMemberRole;
    private final RemoveFamilyMemberUseCase removeFamilyMember;

    MembersController(ListFamilyMembersUseCase listFamilyMembers, UpdateMemberRoleUseCase updateMemberRole,
            RemoveFamilyMemberUseCase removeFamilyMember) {
        this.listFamilyMembers = listFamilyMembers;
        this.updateMemberRole = updateMemberRole;
        this.removeFamilyMember = removeFamilyMember;
    }

    @Override
    public ResponseEntity<List<MemberResponse>> listFamilyMembers(UUID familyId) {
        return ResponseEntity.ok(listFamilyMembers.list(familyId).stream()
                .map(MembersController::toResponse)
                .toList());
    }

    @Override
    public ResponseEntity<MemberResponse> updateMemberRole(String ifMatch, UUID familyId, UUID memberId,
            UpdateMemberRoleRequest request) {
        MemberView member = updateMemberRole.update(new UpdateMemberRoleCommand(familyId, memberId,
                ETags.parseIfMatch(ifMatch), FamilyRole.valueOf(request.getRole().getValue())));
        return ResponseEntity.ok().eTag(ETags.of(member.version())).body(toResponse(member));
    }

    @Override
    public ResponseEntity<Void> removeFamilyMember(String ifMatch, UUID familyId, UUID memberId) {
        removeFamilyMember.remove(new RemoveFamilyMemberCommand(familyId, memberId, ETags.parseIfMatch(ifMatch)));
        return ResponseEntity.noContent().build();
    }

    /** The member's email is never returned (OQ-061). */
    private static MemberResponse toResponse(MemberView member) {
        return new MemberResponse(member.id(), member.userId(), MembershipRole.fromValue(member.role().name()),
                MembershipStatus.ACTIVE, member.joinedAt().atOffset(ZoneOffset.UTC), member.version())
                .displayName(member.displayName())
                .linkedPersonId(member.linkedPersonId())
                .linkedPersonDisplayName(member.linkedPersonDisplayName())
                .relationshipToCurrentUser(member.relationshipToCurrentUser() == null ? null
                        : KinshipCode.fromValue(member.relationshipToCurrentUser()));
    }
}
