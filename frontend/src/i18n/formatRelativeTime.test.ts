import { formatRelativeTime } from './formatRelativeTime';

describe('formatRelativeTime', () => {
  const now = new Date('2026-09-27T12:00:00Z');
  const ago = (seconds: number) => new Date(now.getTime() - seconds * 1000);

  it.each([
    [10, 'maintenant', 'now'],
    [5 * 60, 'il y a 5 minutes', '5 minutes ago'],
    [2 * 3600 + 59 * 60, 'il y a 2 heures', '2 hours ago'],
    [30 * 3600, 'hier', 'yesterday'],
    [3 * 24 * 3600, 'il y a 3 jours', '3 days ago'],
    [15 * 24 * 3600, 'il y a 2 semaines', '2 weeks ago'],
    [400 * 24 * 3600, "l'année dernière", 'last year'],
  ])('%i seconds ago', (seconds, fr, en) => {
    expect(formatRelativeTime(ago(seconds), 'fr', now).replace('’', "'")).toBe(fr);
    expect(formatRelativeTime(ago(seconds), 'en', now)).toBe(en);
  });

  it('never speaks of the future when the clocks differ', () => {
    expect(formatRelativeTime(ago(-30), 'en', now)).toBe('now');
  });
});
