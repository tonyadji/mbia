import {
  forgetInvitation,
  invitationPath,
  pendingInvitation,
  rememberInvitation,
} from './pendingInvitation';

describe('pending invitation (OQ-050)', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('is kept until forgotten, and forgetting another token keeps it', () => {
    rememberInvitation('first');
    rememberInvitation('second');
    forgetInvitation('first');
    expect(pendingInvitation()).toBe('second');

    forgetInvitation('second');
    expect(pendingInvitation()).toBeNull();
  });

  it('keeps nothing, without failing, when the storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(() => {
      rememberInvitation('token');
      forgetInvitation('token');
    }).not.toThrow();
    expect(pendingInvitation()).toBeNull();
  });

  it('builds the invitation path, joining after sign-in when asked', () => {
    expect(invitationPath('a-b_c')).toBe('/invitations/a-b_c');
    expect(invitationPath('a-b_c', { join: true })).toBe('/invitations/a-b_c?join=1');
  });
});
