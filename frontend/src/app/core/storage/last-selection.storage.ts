import { Injectable } from '@angular/core';

import { TEMPERATURE_UNITS, TemperatureUnit } from '../models/temperature-unit';

export interface LastSelection {
  code: string;
  name: string;
  unit: TemperatureUnit;
}

/** Versioned so that a future change of shape can use a new key instead of misreading the old one. */
const STORAGE_KEY = 'weather.last-selection.v1';

/** AEMET municipality code without the "id" prefix: province (2 digits) + municipality (3 digits). */
const MUNICIPALITY_CODE = /^\d{5}$/;

/** A preference, not critical data: any storage failure degrades to "nothing saved" and never throws. */
@Injectable({ providedIn: 'root' })
export class LastSelectionStorage {
  get(): LastSelection | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw === null) {
        return null;
      }
      const value: unknown = JSON.parse(raw);
      return isLastSelection(value) ? value : null;
    } catch {
      // Corrupt JSON or storage blocked by the browser (e.g. SecurityError).
      return null;
    }
  }

  save(selection: LastSelection): void {
    try {
      // Only the known fields, so callers cannot persist extra properties by accident.
      const { code, name, unit } = selection;
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ code, name, unit }));
    } catch {
      // Quota exceeded or storage blocked: losing the preference is acceptable.
    }
  }
}

function isLastSelection(value: unknown): value is LastSelection {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const { code, name, unit } = value as Record<string, unknown>;
  return (
    typeof code === 'string' &&
    MUNICIPALITY_CODE.test(code) &&
    typeof name === 'string' &&
    name !== '' &&
    TEMPERATURE_UNITS.some((known) => known === unit)
  );
}
