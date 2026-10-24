import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';

import { Forecast } from '../../core/models/forecast';
import { TemperatureUnit } from '../../core/models/temperature-unit';
import { WeatherApiError } from '../../core/models/weather-api-error';
import { WeatherApiService } from '../../core/services/weather-api.service';
import { LastSelection, LastSelectionStorage } from '../../core/storage/last-selection.storage';
import { WeatherStore } from './weather.store';

const AGOST = { code: '03002', name: 'Agost' };
const ADRA = { code: '04003', name: 'Adra' };

/** Each call returns a Subject the test controls, so it decides when (and whether) each request answers. */
class FakeWeatherApi {
  readonly calls: { code: string; unit?: TemperatureUnit; response: Subject<Forecast> }[] = [];

  getTomorrowForecast(code: string, unit?: TemperatureUnit): Observable<Forecast> {
    const response = new Subject<Forecast>();
    this.calls.push({ code, unit, response });
    return response;
  }

  answer(callIndex: number, forecast: Forecast): void {
    this.calls[callIndex].response.next(forecast);
    this.calls[callIndex].response.complete();
  }
}

class FakeLastSelectionStorage {
  saved: LastSelection | null = null;
  readonly save = vi.fn((selection: LastSelection) => (this.saved = selection));

  get(): LastSelection | null {
    return this.saved;
  }
}

describe('WeatherStore', () => {
  let api: FakeWeatherApi;
  let storage: FakeLastSelectionStorage;

  beforeEach(() => {
    api = new FakeWeatherApi();
    storage = new FakeLastSelectionStorage();
    TestBed.configureTestingModule({
      providers: [
        WeatherStore,
        { provide: WeatherApiService, useValue: api },
        { provide: LastSelectionStorage, useValue: storage },
      ],
    });
  });

  /** Created lazily so each test can prepare the storage before the constructor reads it. */
  function createStore(): WeatherStore {
    return TestBed.inject(WeatherStore);
  }

  it('starts idle with Celsius and does nothing when nothing is saved', () => {
    const store = createStore();

    expect(store.municipality()).toBeNull();
    expect(store.unit()).toBe('G_CEL');
    expect(store.forecastState()).toEqual({ status: 'idle' });
    expect(api.calls).toHaveLength(0);
    expect(storage.save).not.toHaveBeenCalled();
  });

  it('goes through loading and loaded when a municipality is selected', () => {
    const store = createStore();

    store.selectMunicipality(AGOST);

    expect(store.municipality()).toEqual(AGOST);
    expect(store.forecastState()).toEqual({ status: 'loading' });
    expect(api.calls[0]).toMatchObject({ code: '03002', unit: 'G_CEL' });

    api.answer(0, forecast(19.3));

    expect(store.forecastState()).toEqual({ status: 'loaded', forecast: forecast(19.3) });
  });

  it('saves the selection on every change', () => {
    const store = createStore();

    store.selectMunicipality(AGOST);
    store.selectUnit('G_FAH');

    expect(storage.save).toHaveBeenNthCalledWith(1, { ...AGOST, unit: 'G_CEL' });
    expect(storage.save).toHaveBeenNthCalledWith(2, { ...AGOST, unit: 'G_FAH' });
  });

  it('exposes the category of a WeatherApiError', () => {
    const store = createStore();
    store.selectMunicipality(AGOST);

    api.calls[0].response.error(new WeatherApiError(404));

    expect(store.forecastState()).toEqual({ status: 'error', category: 'not-found' });
  });

  it('treats any other error as unknown', () => {
    const store = createStore();
    store.selectMunicipality(AGOST);

    api.calls[0].response.error(new TypeError('boom'));

    expect(store.forecastState()).toEqual({ status: 'error', category: 'unknown' });
  });

  it('does not let a slow old response overwrite a newer one', () => {
    const store = createStore();
    store.selectMunicipality(AGOST);
    store.selectMunicipality(ADRA);

    api.answer(1, forecast(25));
    api.answer(0, forecast(10));

    expect(api.calls[0].response.observed).toBe(false);
    expect(store.forecastState()).toEqual({ status: 'loaded', forecast: forecast(25) });
  });

  it('keeps loading after an error', () => {
    const store = createStore();
    store.selectMunicipality(AGOST);
    api.calls[0].response.error(new WeatherApiError(502));

    store.selectMunicipality(ADRA);
    expect(store.forecastState()).toEqual({ status: 'loading' });
    api.answer(1, forecast(25));

    expect(store.forecastState()).toEqual({ status: 'loaded', forecast: forecast(25) });
  });

  it('requests again with the new unit when the unit changes', () => {
    const store = createStore();
    store.selectMunicipality(AGOST);
    api.answer(0, forecast(19.3));

    store.selectUnit('G_FAH');

    expect(store.unit()).toBe('G_FAH');
    expect(store.forecastState()).toEqual({ status: 'loading' });
    expect(api.calls[1]).toMatchObject({ code: '03002', unit: 'G_FAH' });
  });

  it('only remembers the unit when no municipality is selected yet', () => {
    const store = createStore();

    store.selectUnit('G_FAH');

    expect(store.unit()).toBe('G_FAH');
    expect(store.forecastState()).toEqual({ status: 'idle' });
    expect(api.calls).toHaveLength(0);
    expect(storage.save).not.toHaveBeenCalled();
  });

  it('retries the same request after an error and succeeds, without saving again', () => {
    const store = createStore();
    store.selectMunicipality(AGOST);
    store.selectUnit('G_FAH');
    api.calls[1].response.error(new WeatherApiError(502));
    storage.save.mockClear();

    store.reload();

    expect(store.forecastState()).toEqual({ status: 'loading' });
    expect(api.calls[2]).toMatchObject({ code: '03002', unit: 'G_FAH' });
    api.answer(2, forecast(66.7));
    expect(store.forecastState()).toEqual({ status: 'loaded', forecast: forecast(66.7) });
    expect(storage.save).not.toHaveBeenCalled();
  });

  it('does nothing on reload when no municipality is selected', () => {
    const store = createStore();

    store.reload();

    expect(store.forecastState()).toEqual({ status: 'idle' });
    expect(api.calls).toHaveLength(0);
    expect(storage.save).not.toHaveBeenCalled();
  });

  it('restores the saved selection and loads its forecast on start', () => {
    storage.saved = { ...ADRA, unit: 'G_FAH' };

    const store = createStore();

    expect(store.municipality()).toEqual(ADRA);
    expect(store.unit()).toBe('G_FAH');
    expect(store.forecastState()).toEqual({ status: 'loading' });
    expect(api.calls[0]).toMatchObject({ code: '04003', unit: 'G_FAH' });
    expect(storage.save).not.toHaveBeenCalled();
  });
});

function forecast(averageTemperature: number): Forecast {
  return {
    averageTemperature,
    unit: 'G_CEL',
    precipitation: [{ probability: 20, period: '12-18' }],
  };
}
