import { initialOf } from './Avatar';

describe('initialOf', () => {
  it('takes the first letter of the display name', () => {
    expect(initialOf('élodie', 'x@mbia.local')).toBe('É');
  });

  it('falls back to the email when there is no display name', () => {
    expect(initialOf(null, 'alice@mbia.local')).toBe('A');
    expect(initialOf('   ', 'bob@mbia.local')).toBe('B');
  });
});
