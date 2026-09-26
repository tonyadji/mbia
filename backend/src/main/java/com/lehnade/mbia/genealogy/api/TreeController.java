package com.lehnade.mbia.genealogy.api;

import com.lehnade.mbia.api.generated.TreeApi;
import com.lehnade.mbia.api.generated.model.KinshipCode;
import com.lehnade.mbia.api.generated.model.KinshipPathStep;
import com.lehnade.mbia.api.generated.model.KinshipResponse;
import com.lehnade.mbia.api.generated.model.Gender;
import com.lehnade.mbia.api.generated.model.PersonStatus;
import com.lehnade.mbia.api.generated.model.RelationshipType;
import com.lehnade.mbia.api.generated.model.TreeEdge;
import com.lehnade.mbia.api.generated.model.TreeNode;
import com.lehnade.mbia.api.generated.model.TreeResponse;
import com.lehnade.mbia.genealogy.application.ProfilePictureUrls;
import com.lehnade.mbia.genealogy.application.getfamilytree.FamilyTreeView;
import com.lehnade.mbia.genealogy.application.getfamilytree.GetFamilyTreeUseCase;
import com.lehnade.mbia.genealogy.application.resolvekinship.ResolveKinshipUseCase;
import com.lehnade.mbia.genealogy.domain.FamilyTree;
import com.lehnade.mbia.genealogy.domain.Kinship;
import com.lehnade.mbia.genealogy.domain.Person;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Tree and kinship reads of a Family. */
@RestController
class TreeController implements TreeApi {

    private final ResolveKinshipUseCase resolveKinship;
    private final GetFamilyTreeUseCase getFamilyTree;
    private final ProfilePictureUrls profilePictureUrls;

    TreeController(ResolveKinshipUseCase resolveKinship, GetFamilyTreeUseCase getFamilyTree,
            ProfilePictureUrls profilePictureUrls) {
        this.resolveKinship = resolveKinship;
        this.getFamilyTree = getFamilyTree;
        this.profilePictureUrls = profilePictureUrls;
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
        FamilyTreeView view = getFamilyTree.get(familyId, focusPersonId, depth);
        return ResponseEntity.ok(view.tree()
                .map(tree -> new TreeResponse(tree.focus().value(),
                        tree.nodes().stream().map(node -> toApi(node, view)).toList(),
                        tree.edges().stream().map(TreeController::toApi).toList()))
                .orElseGet(() -> new TreeResponse(null, List.of(), List.of())));
    }

    private TreeNode toApi(FamilyTree.Node node, FamilyTreeView view) {
        Person person = node.person();
        PersonDetails details = person.details();
        com.lehnade.mbia.genealogy.domain.KinshipCode relationship =
                view.relationshipToCurrentUser().get(person.id());
        return new TreeNode(person.id().value(), person.familyId(), details.firstName(),
                Gender.fromValue(details.gender().name()), PersonApiMapping.toApi(details.birth()),
                details.deceased(), PersonApiMapping.toApi(details.death()),
                PersonStatus.fromValue(person.status().name()), person.version(), node.hasMoreParents(),
                node.hasMoreChildren())
                .middleNames(details.middleNames())
                .lastName(details.lastName())
                .preferredName(details.preferredName())
                .displayName(details.displayName())
                .profilePictureUrl(profilePictureUrls.of(person))
                .linkedUserId(person.linkedUserId().orElse(null))
                .relationshipToCurrentUser(relationship == null ? null : KinshipCode.fromValue(relationship.name()));
    }

    private static TreeEdge toApi(FamilyTree.Edge edge) {
        return new TreeEdge(edge.id().value(), RelationshipType.fromValue(edge.type().name()),
                edge.source().value(), edge.target().value(), edge.version());
    }
}
