import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';

import { Forecast } from '../../../core/models/forecast';
import { Municipality } from '../../../core/models/municipality';
import { TemperatureUnit } from '../../../core/models/temperature-unit';
import { WeatherApiError } from '../../../core/models/weather-api-error';
import { WeatherApiService } from '../../../core/services/weather-api.service';
import { LastSelection, LastSelectionStorage } from '../../../core/storage/last-selection.storage';
import { WeatherPage } from './weather-page';

const AGOST = { code: '03002', name: 'Agost' };
const ADRA = { code: '04003', name: 'Adra' };

/** Each call returns a Subject the test controls, so it decides when (and how) each request answers. */
class FakeWeatherApi {
  readonly searches: Subject<Municipality[]>[] = [];
  readonly forecasts: { code: string; unit?: TemperatureUnit; response: Subject<Forecast> }[] = [];

  searchMunicipalities(): Observable<Municipality[]> {
    const response = new Subject<Municipality[]>();
    this.searches.push(response);
    return response;
  }

  getTomorrowForecast(code: string, unit?: TemperatureUnit): Observable<Forecast> {
    const response = new Subject<Forecast>();
    this.forecasts.push({ code, unit, response });
    return response;
  }
}

class FakeLastSelectionStorage {
  saved: LastSelection | null = null;

  get(): LastSelection | null {
    return this.saved;
  }

  save(selection: LastSelection): void {
    this.saved = selection;
  }
}

function forecast(averageTemperature: number, unit: TemperatureUnit = 'G_CEL'): Forecast {
  return { averageTemperature, unit, precipitation: [{ period: '00-06', probability: 10 }] };
}

describe('WeatherPage', () => {
  let api: FakeWeatherApi;
  let storage: FakeLastSelectionStorage;
  let fixture: ComponentFixture<WeatherPage>;
  let element: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    api = new FakeWeatherApi();
    storage = new FakeLastSelectionStorage();
    TestBed.configureTestingModule({
      imports: [WeatherPage],
      providers: [
        { provide: WeatherApiService, useValue: api },
        { provide: LastSelectionStorage, useValue: storage },
      ],
    });
  });

  afterEach(() => vi.useRealTimers());

  /** Created lazily so a test can prepare the storage before the store reads it. */
  function createPage(): void {
    fixture = TestBed.createComponent(WeatherPage);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  function searchField(): HTMLInputElement {
    return element.querySelector('app-municipality-search input')!;
  }

  function chooseMunicipality(municipality: Municipality): void {
    searchField().value = municipality.name.slice(0, 2);
    searchField().dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(250);
    api.searches.at(-1)!.next([municipality]);
    fixture.detectChanges();
    element
      .querySelector('[role="option"]')!
      .dispatchEvent(new MouseEvent('mousedown', { cancelable: true }));
    fixture.detectChanges();
  }

  function answerForecast(callIndex: number, value: Forecast): void {
    api.forecasts[callIndex].response.next(value);
    api.forecasts[callIndex].response.complete();
    fixture.detectChanges();
  }

  function failForecast(callIndex: number, status: number): void {
    api.forecasts[callIndex].response.error(new WeatherApiError(status));
    fixture.detectChanges();
  }

  function unitRadio(unit: TemperatureUnit): HTMLInputElement {
    return element.querySelector(`app-unit-selector input[value="${unit}"]`)!;
  }

  /** Scoped to the forecast section: the search field has its own role="status" region. */
  function forecastSection(): HTMLElement {
    return element.querySelector('.forecast')!;
  }

  function loadingMessage(): string | undefined {
    return forecastSection().querySelector('[role="status"]')?.textContent?.trim();
  }

  function alertMessage(): string | undefined {
    return forecastSection().querySelector('[role="alert"]')?.textContent?.trim();
  }

  function retryButton(): HTMLButtonElement | null {
    return forecastSection().querySelector('button');
  }

  function shownTemperature(): string | undefined {
    return forecastSection().querySelector('app-forecast-card .value')?.textContent?.trim();
  }

  it('shows a hint and asks for nothing until a municipality is chosen', () => {
    createPage();

    expect(element.querySelector('h1')?.textContent).toBe('Previsión meteorológica');
    expect(forecastSection().querySelector('.hint')?.textContent).toContain('Elige un municipio');
    expect(api.forecasts).toHaveLength(0);
  });

  it('goes through loading and loaded when a municipality is chosen', () => {
    createPage();

    chooseMunicipality(AGOST);

    expect(api.forecasts[0]).toMatchObject({ code: '03002', unit: 'G_CEL' });
    expect(loadingMessage()).toBe('Cargando la previsión…');
    expect(shownTemperature()).toBeUndefined();

    answerForecast(0, forecast(17.5));

    expect(loadingMessage()).toBeUndefined();
    expect(forecastSection().querySelector('h2')?.textContent).toBe('Mañana en Agost');
    expect(shownTemperature()).toBe('17,5 °C');
  });

  it('asks again with the new unit when the unit changes', () => {
    createPage();
    chooseMunicipality(AGOST);
    answerForecast(0, forecast(17.5));

    unitRadio('G_FAH').click();
    fixture.detectChanges();

    expect(api.forecasts[1]).toMatchObject({ code: '03002', unit: 'G_FAH' });
    expect(loadingMessage()).toBe('Cargando la previsión…');

    answerForecast(1, forecast(63.5, 'G_FAH'));

    expect(shownTemperature()).toBe('63,5 °F');
    expect(unitRadio('G_FAH').checked).toBe(true);
    expect(unitRadio('G_CEL').checked).toBe(false);
  });

  it('shows the error message and succeeds when retrying', () => {
    createPage();
    chooseMunicipality(AGOST);

    failForecast(0, 502);

    expect(alertMessage()).toBe(
      'No se ha podido contactar con el servicio meteorológico. Inténtalo de nuevo en unos minutos.',
    );
    expect(shownTemperature()).toBeUndefined();

    retryButton()!.click();
    fixture.detectChanges();

    expect(api.forecasts[1]).toMatchObject({ code: '03002', unit: 'G_CEL' });
    expect(alertMessage()).toBeUndefined();
    expect(loadingMessage()).toBe('Cargando la previsión…');

    answerForecast(1, forecast(17.5));

    expect(shownTemperature()).toBe('17,5 °C');
  });

  it('does not offer a retry when the municipality has no forecast', () => {
    createPage();
    chooseMunicipality(AGOST);

    failForecast(0, 404);

    expect(alertMessage()).toBe('No hay previsión disponible para este municipio.');
    expect(retryButton()).toBeNull();
  });

  it('restores the saved selection on start: name in the field and its forecast', () => {
    storage.saved = { ...ADRA, unit: 'G_FAH' };

    createPage();

    expect(searchField().value).toBe('Adra');
    expect(unitRadio('G_FAH').checked).toBe(true);
    expect(api.forecasts).toHaveLength(1);
    expect(api.forecasts[0]).toMatchObject({ code: '04003', unit: 'G_FAH' });
    expect(loadingMessage()).toBe('Cargando la previsión…');

    answerForecast(0, forecast(60.1, 'G_FAH'));

    expect(forecastSection().querySelector('h2')?.textContent).toBe('Mañana en Adra');
    expect(shownTemperature()).toBe('60,1 °F');
  });
});
