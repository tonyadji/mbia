package com.lehnade.mbia.invitation.application;

import com.lehnade.mbia.genealogy.application.InvitablePersons;
import com.lehnade.mbia.invitation.domain.Invitation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Invitations with their inviter and their Person, read in one query each for a whole list. */
@Service
public class InvitationViews {

    private final InvitationActors actors;
    private final InvitablePersons persons;

    public InvitationViews(InvitationActors actors, InvitablePersons persons) {
        this.actors = actors;
        this.persons = persons;
    }

    public InvitationView of(Invitation invitation) {
        return of(invitation.familyId(), List.of(invitation)).getFirst();
    }

    /** @param invitations invitations of this Family */
    public List<InvitationView> of(UUID familyId, List<Invitation> invitations) {
        Map<UUID, InvitationActors.Actor> inviters = actors.actors(
                invitations.stream().map(Invitation::invitedBy).distinct().toList());
        Map<UUID, String> names = persons.activeDisplayNames(familyId,
                invitations.stream().flatMap(invitation -> invitation.personId().stream()).toList());
        return invitations.stream()
                .map(invitation -> new InvitationView(invitation, inviters.get(invitation.invitedBy()),
                        invitation.personId().map(names::get).orElse(null)))
                .toList();
    }

    /**
     * The audited fields of an invitation (data-model.md §17): never its token, hash, link or
     * email.
     */
    public static Map<String, Object> auditValues(Invitation invitation) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("channel", invitation.channel().name());
        values.put("role", invitation.role().name());
        invitation.personId().ifPresent(personId -> values.put("personId", personId.toString()));
        values.put("status", invitation.status().name());
        values.put("expiresAt", invitation.expiresAt().toString());
        return values;
    }
}
