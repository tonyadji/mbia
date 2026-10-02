import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import type { Language } from '../i18n/language';
import { invitationsQueryKey } from './useFamilyInvitations';

type InvitationRole = components['schemas']['InvitationRole'];

export type NewInvitation =
  | { channel: 'LINK'; role: InvitationRole; personId: string | null }
  /** Mbia sends the link to `email`, in `locale`: the inviter's current language (mvp.md §18). */
  | {
      channel: 'EMAIL';
      role: InvitationRole;
      personId: string | null;
      email: string;
      locale: Language;
    };

/**
 * Creates an invitation, for a Person of the tree when `personId` is given (mvp.md §18, OQ-050).
 * The response holds the link, returned only this once, and for `EMAIL` whether the email was
 * sent (OQ-055).
 */
export function useCreateInvitation(familyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (invitation: NewInvitation) => {
      const { data } = await apiClient.POST('/families/{familyId}/invitations', {
        params: { path: { familyId } },
        body: invitation,
      });
      if (data === undefined) {
        throw new Error('POST /families/{familyId}/invitations returned no body');
      }
      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: invitationsQueryKey(familyId) });
    },
  });
}
