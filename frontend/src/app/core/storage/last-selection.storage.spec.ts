import { TestBed } from '@angular/core/testing';

import { LastSelection, LastSelectionStorage } from './last-selection.storage';

const STORAGE_KEY = 'weather.last-selection.v1';

describe('LastSelectionStorage', () => {
  let storage: LastSelectionStorage;

  beforeEach(() => {
    localStorage.clear();
    storage = TestBed.inject(LastSelectionStorage);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('returns null when nothing is saved', () => {
    expect(storage.get()).toBeNull();
  });

  it('saves and reads back a selection', () => {
    const selection: LastSelection = { code: '03002', name: 'Agost', unit: 'G_FAH' };

    storage.save(selection);

    expect(storage.get()).toEqual(selection);
  });

  it('stores only the known fields', () => {
    const withExtra = { code: '03002', name: 'Agost', unit: 'G_CEL' as const, extra: 'x' };

    storage.save(withExtra);

    expect(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '')).toEqual({
      code: '03002',
      name: 'Agost',
      unit: 'G_CEL',
    });
  });

  it('returns null for corrupt JSON', () => {
    localStorage.setItem(STORAGE_KEY, '{not json');

    expect(storage.get()).toBeNull();
  });

  it.each([
    ['missing code', { name: 'Agost', unit: 'G_CEL' }],
    ['missing name', { code: '03002', unit: 'G_CEL' }],
    ['missing unit', { code: '03002', name: 'Agost' }],
    ['empty code', { code: '', name: 'Agost', unit: 'G_CEL' }],
    ['code of the wrong type', { code: 3002, name: 'Agost', unit: 'G_CEL' }],
    ['a code that lost its leading zero', { code: '3002', name: 'Agost', unit: 'G_CEL' }],
    ['a code with six digits', { code: '030021', name: 'Agost', unit: 'G_CEL' }],
    ['a code with letters', { code: 'id03002', name: 'Agost', unit: 'G_CEL' }],
    ['empty name', { code: '03002', name: '', unit: 'G_CEL' }],
    ['a JSON array', [{ code: '03002', name: 'Agost', unit: 'G_CEL' }]],
    ['JSON null', null],
  ])('returns null for %s', (_label, stored) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));

    expect(storage.get()).toBeNull();
  });

  it('returns null for an unknown unit', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ code: '03002', name: 'Agost', unit: 'K' }));

    expect(storage.get()).toBeNull();
  });

  it('returns null when localStorage throws on read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(storage.get()).toBeNull();
  });

  it('does not throw when localStorage throws on write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('full', 'QuotaExceededError');
    });

    expect(() => storage.save({ code: '03002', name: 'Agost', unit: 'G_CEL' })).not.toThrow();
  });
});
