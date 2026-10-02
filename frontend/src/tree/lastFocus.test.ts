import { clearLastFocus, readLastFocus, writeLastFocus } from './lastFocus';

describe('last focused Person (family-tree-ux.md §6)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('is remembered per Family', () => {
    writeLastFocus('family-a', 'person-1');
    writeLastFocus('family-b', 'person-2');
    expect(readLastFocus('family-a')).toBe('person-1');
    expect(readLastFocus('family-b')).toBe('person-2');
    clearLastFocus('family-a');
    expect(readLastFocus('family-a')).toBeUndefined();
  });

  it('does not fail when browser storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => {
      writeLastFocus('family-a', 'person-1');
    }).not.toThrow();
    expect(readLastFocus('family-a')).toBeUndefined();
  });
});
