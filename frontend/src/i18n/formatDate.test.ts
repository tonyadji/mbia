import { formatDate } from './formatDate';

describe('formatDate', () => {
  const date = new Date(1954, 2, 12);

  it('formats a date in French', () => {
    expect(formatDate(date, 'fr')).toBe('12 mars 1954');
  });

  it('formats a date in English', () => {
    expect(formatDate(date, 'en')).toBe('March 12, 1954');
  });
});
