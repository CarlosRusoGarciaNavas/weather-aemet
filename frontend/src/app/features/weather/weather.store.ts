import { inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, map, Observable, of, startWith, Subject, switchMap } from 'rxjs';

import { Forecast } from '../../core/models/forecast';
import { Municipality } from '../../core/models/municipality';
import { TemperatureUnit } from '../../core/models/temperature-unit';
import { WeatherApiError, WeatherApiErrorCategory } from '../../core/models/weather-api-error';
import { WeatherApiService } from '../../core/services/weather-api.service';
import { LastSelectionStorage } from '../../core/storage/last-selection.storage';

export type ForecastState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'loaded'; forecast: Forecast }
  | { status: 'error'; category: WeatherApiErrorCategory };

interface ForecastRequest {
  code: string;
  unit: TemperatureUnit;
}

/** State of the weather page. Provided by the page component, so it lives and dies with it. */
@Injectable()
export class WeatherStore {
  private readonly api = inject(WeatherApiService);
  private readonly storage = inject(LastSelectionStorage);

  private readonly _municipality = signal<Municipality | null>(null);
  private readonly _unit = signal<TemperatureUnit>('G_CEL');
  private readonly _forecastState = signal<ForecastState>({ status: 'idle' });

  readonly municipality = this._municipality.asReadonly();
  readonly unit = this._unit.asReadonly();
  readonly forecastState = this._forecastState.asReadonly();

  private readonly requests = new Subject<ForecastRequest>();

  constructor() {
    // switchMap unsubscribes from the previous request, so a slow old answer cannot overwrite a newer one.
    this.requests
      .pipe(
        switchMap((request) => this.fetch(request)),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this._forecastState.set(state));

    // After subscribing: a request emitted before would be lost.
    this.restoreLastSelection();
  }

  selectMunicipality(municipality: Municipality): void {
    this._municipality.set(municipality);
    this.saveAndLoad();
  }

  selectUnit(unit: TemperatureUnit): void {
    this._unit.set(unit);
    this.saveAndLoad();
  }

  /** Repeats the current request (e.g. retry after an error). Nothing changed, so nothing is saved. */
  reload(): void {
    const municipality = this._municipality();
    if (municipality !== null) {
      this.requests.next({ code: municipality.code, unit: this._unit() });
    }
  }

  /** catchError lives inside switchMap: an error completes only this request, not the whole stream. */
  private fetch({ code, unit }: ForecastRequest): Observable<ForecastState> {
    return this.api.getTomorrowForecast(code, unit).pipe(
      map((forecast): ForecastState => ({ status: 'loaded', forecast })),
      catchError((error: unknown) =>
        of<ForecastState>({
          status: 'error',
          category: error instanceof WeatherApiError ? error.category : 'unknown',
        }),
      ),
      startWith<ForecastState>({ status: 'loading' }),
    );
  }

  private restoreLastSelection(): void {
    const saved = this.storage.get();
    if (saved === null) {
      return;
    }
    this._municipality.set({ code: saved.code, name: saved.name });
    this._unit.set(saved.unit);
    this.reload();
  }

  /** Without a municipality there is nothing to save (the storage needs code and name) or to load. */
  private saveAndLoad(): void {
    const municipality = this._municipality();
    if (municipality === null) {
      return;
    }
    this.storage.save({ code: municipality.code, name: municipality.name, unit: this._unit() });
    this.reload();
  }
}
