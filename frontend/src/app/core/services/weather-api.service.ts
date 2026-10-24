import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, throwError } from 'rxjs';

import { Forecast } from '../models/forecast';
import { Municipality } from '../models/municipality';
import { TemperatureUnit } from '../models/temperature-unit';
import { WeatherApiError } from '../models/weather-api-error';
import { ForecastDto, MunicipalityDto } from './weather-api.dto';

const MUNICIPALITIES_URL = '/api/municipios';

@Injectable({ providedIn: 'root' })
export class WeatherApiService {
  private readonly http = inject(HttpClient);

  searchMunicipalities(prefix: string): Observable<Municipality[]> {
    // HttpParams encodes the value, so accented prefixes such as "Ávi" reach the backend intact.
    const params = new HttpParams().set('nombre', prefix);
    return this.http.get<MunicipalityDto[]>(MUNICIPALITIES_URL, { params }).pipe(
      catchError(toWeatherApiError),
      map((dtos) => dtos.map(toMunicipality)),
    );
  }

  /** Without a unit the backend applies its default (G_CEL), so the parameter is only sent when given. */
  getTomorrowForecast(code: string, unit?: TemperatureUnit): Observable<Forecast> {
    const url = `${MUNICIPALITIES_URL}/${encodeURIComponent(code)}/prediccion/dia-siguiente`;
    const params = unit ? new HttpParams().set('unidad', unit) : new HttpParams();
    return this.http.get<ForecastDto>(url, { params }).pipe(
      catchError(toWeatherApiError),
      map(toForecast),
    );
  }
}

function toMunicipality(dto: MunicipalityDto): Municipality {
  return { code: dto.codigo, name: dto.nombre };
}

function toForecast(dto: ForecastDto): Forecast {
  return {
    averageTemperature: dto.mediaTemperatura,
    unit: dto.unidadTemperatura,
    precipitation: dto.probPrecipitacion.map((p) => ({
      probability: p.probabilidad,
      period: p.periodo,
    })),
  };
}

/** Only HTTP failures become WeatherApiError; anything else is a bug and propagates unchanged. */
function toWeatherApiError(error: unknown): Observable<never> {
  return throwError(() =>
    error instanceof HttpErrorResponse ? new WeatherApiError(error.status) : error,
  );
}
