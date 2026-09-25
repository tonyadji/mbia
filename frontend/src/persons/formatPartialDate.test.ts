import { formatPartialDate, yearOf } from './formatPartialDate';

describe('formatPartialDate', () => {
  it('formats an exact date in the active language', () => {
    const date = { precision: 'EXACT', date: '1954-03-12' } as const;
    expect(formatPartialDate(date, 'fr')).toBe('12 mars 1954');
    expect(formatPartialDate(date, 'en')).toBe('March 12, 1954');
  });

  it('shows only the year of a year-only date, and nothing for an unknown one', () => {
    expect(formatPartialDate({ precision: 'YEAR_ONLY', year: 1954 }, 'fr')).toBe('1954');
    expect(formatPartialDate({ precision: 'UNKNOWN' }, 'en')).toBeNull();
  });

  it('keeps early years as written', () => {
    expect(formatPartialDate({ precision: 'EXACT', date: '0045-01-02' }, 'en')).toMatch(/45$/);
  });
});

describe('yearOf', () => {
  it('takes the year of an exact or year-only date', () => {
    expect(yearOf({ precision: 'EXACT', date: '1954-03-12' })).toBe('1954');
    expect(yearOf({ precision: 'YEAR_ONLY', year: 2020 })).toBe('2020');
    expect(yearOf({ precision: 'UNKNOWN' })).toBeNull();
  });
});
