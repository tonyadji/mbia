import {
  dateDraftError,
  EMPTY_DATE,
  fromPartialDate,
  sameDate,
  toPartialDate,
  today,
} from './PartialDateField';

describe('partial date of a form', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 8, 29, 23, 30));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('reads and writes the three precisions', () => {
    expect(fromPartialDate(null)).toEqual(EMPTY_DATE);
    expect(fromPartialDate({ precision: 'YEAR_ONLY', year: 1975 })).toEqual({
      precision: 'YEAR_ONLY',
      date: '',
      year: '1975',
    });
    expect(toPartialDate({ precision: 'YEAR_ONLY', date: '', year: ' 1975 ' })).toEqual({
      precision: 'YEAR_ONLY',
      year: 1975,
    });
    expect(toPartialDate({ precision: 'EXACT', date: '1962-03-12', year: '' })).toEqual({
      precision: 'EXACT',
      date: '1962-03-12',
    });
    expect(toPartialDate({ precision: 'UNKNOWN', date: '1962-03-12', year: '1975' })).toEqual({
      precision: 'UNKNOWN',
    });
  });

  it('compares what would be sent, not what was typed', () => {
    const loaded = fromPartialDate({ precision: 'YEAR_ONLY', year: 1975 });
    expect(sameDate(loaded, { ...loaded, date: '2000-01-01' })).toBe(true);
    expect(sameDate(loaded, { ...loaded, year: '1962' })).toBe(false);
    expect(sameDate(loaded, { ...loaded, precision: 'UNKNOWN' })).toBe(false);
  });

  it('takes today in the User’s calendar', () => {
    expect(today()).toBe('2026-09-29');
  });

  it('refuses a missing date or an invalid year, like a birth date', () => {
    const exact = { precision: 'EXACT', date: '', year: '' } as const;
    const year = { precision: 'YEAR_ONLY', date: '', year: '19755' } as const;
    for (const notFuture of [false, true]) {
      expect(dateDraftError(exact, { notFuture })).toBe('dateRequired');
      expect(dateDraftError(year, { notFuture })).toBe('invalidYear');
      expect(dateDraftError(EMPTY_DATE, { notFuture })).toBeNull();
    }
  });

  it.each([
    ['EXACT', '2026-09-29', '', null],
    ['EXACT', '2026-09-30', '', 'futureDate'],
    ['YEAR_ONLY', '', '2026', null],
    ['YEAR_ONLY', '', '2027', 'futureDate'],
  ] as const)('with notFuture, %s %s%s → %s', (precision, date, year, error) => {
    expect(dateDraftError({ precision, date, year }, { notFuture: true })).toBe(error);
    // A photo's taken date keeps accepting it (OQ-033).
    expect(dateDraftError({ precision, date, year }, { notFuture: false })).toBeNull();
  });
});
