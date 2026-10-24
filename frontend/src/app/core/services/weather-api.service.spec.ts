import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Forecast } from '../models/forecast';
import { Municipality } from '../models/municipality';
import { WeatherApiError, WeatherApiErrorCategory } from '../models/weather-api-error';
import { ForecastDto, MunicipalityDto } from './weather-api.dto';
import { WeatherApiService } from './weather-api.service';

const FORECAST_URL = '/api/municipios/03002/prediccion/dia-siguiente';

describe('WeatherApiService', () => {
  let service: WeatherApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WeatherApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  describe('searchMunicipalities', () => {
    it('translates the response into municipalities', () => {
      let result: Municipality[] | undefined;
      service.searchMunicipalities('Ag').subscribe((municipalities) => (result = municipalities));

      const body: MunicipalityDto[] = [
        { codigo: '03002', nombre: 'Agost' },
        { codigo: '04003', nombre: 'Adra' },
      ];
      httpTesting.expectOne((req) => req.url === '/api/municipios').flush(body);

      expect(result).toEqual([
        { code: '03002', name: 'Agost' },
        { code: '04003', name: 'Adra' },
      ]);
    });

    it('sends an accented prefix percent-encoded as UTF-8', () => {
      service.searchMunicipalities('Ávi').subscribe();

      const req = httpTesting.expectOne((r) => r.url === '/api/municipios');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('nombre')).toBe('Ávi');
      expect(req.request.urlWithParams).toBe('/api/municipios?nombre=%C3%81vi');
      req.flush([]);
    });
  });

  describe('getTomorrowForecast', () => {
    it('translates the response into a forecast', () => {
      let result: Forecast | undefined;
      service.getTomorrowForecast('03002', 'G_FAH').subscribe((forecast) => (result = forecast));

      const body: ForecastDto = {
        mediaTemperatura: 66.7,
        unidadTemperatura: 'G_FAH',
        probPrecipitacion: [
          { probabilidad: 0, periodo: '00-06' },
          { probabilidad: 5, periodo: '06-12' },
          { probabilidad: 20, periodo: '12-18' },
          { probabilidad: 10, periodo: '18-24' },
        ],
      };
      httpTesting.expectOne((req) => req.url === FORECAST_URL).flush(body);

      expect(result).toEqual({
        averageTemperature: 66.7,
        unit: 'G_FAH',
        precipitation: [
          { probability: 0, period: '00-06' },
          { probability: 5, period: '06-12' },
          { probability: 20, period: '12-18' },
          { probability: 10, period: '18-24' },
        ],
      });
    });

    it('sends unidad when a unit is given', () => {
      service.getTomorrowForecast('03002', 'G_FAH').subscribe();

      const req = httpTesting.expectOne((r) => r.url === FORECAST_URL);
      expect(req.request.urlWithParams).toBe(`${FORECAST_URL}?unidad=G_FAH`);
      req.flush(forecastBody());
    });

    it('does not send unidad when no unit is given', () => {
      service.getTomorrowForecast('03002').subscribe();

      const req = httpTesting.expectOne((r) => r.url === FORECAST_URL);
      expect(req.request.params.has('unidad')).toBe(false);
      expect(req.request.urlWithParams).toBe(FORECAST_URL);
      req.flush(forecastBody());
    });
  });

  describe('errors', () => {
    it.each<[number, string, WeatherApiErrorCategory]>([
      [400, 'Bad Request', 'invalid-request'],
      [404, 'Not Found', 'not-found'],
      [502, 'Bad Gateway', 'unavailable'],
      [504, 'Gateway Timeout', 'unavailable'],
      [503, 'Service Unavailable', 'rate-limited'],
      [500, 'Internal Server Error', 'unknown'],
    ])('maps HTTP %i to %s -> %s', (status, statusText, category) => {
      const error = captureForecastError((req) => req.flush(null, { status, statusText }));

      expect(error).toBeInstanceOf(WeatherApiError);
      expect(error?.category).toBe(category);
      expect(error?.status).toBe(status);
    });

    it('maps a network failure (status 0) to unavailable', () => {
      const error = captureForecastError((req) => req.error(new ProgressEvent('error')));

      expect(error).toBeInstanceOf(WeatherApiError);
      expect(error?.category).toBe('unavailable');
      expect(error?.status).toBe(0);
    });

    it('also converts errors from the municipality search', () => {
      let error: unknown;
      service.searchMunicipalities('Ag').subscribe({ error: (e: unknown) => (error = e) });

      httpTesting
        .expectOne((r) => r.url === '/api/municipios')
        .flush(null, { status: 503, statusText: 'Service Unavailable' });

      expect(error).toBeInstanceOf(WeatherApiError);
      expect((error as WeatherApiError).category).toBe('rate-limited');
    });
  });

  function captureForecastError(
    respond: (req: TestRequest) => void,
  ): WeatherApiError | undefined {
    let error: WeatherApiError | undefined;
    service.getTomorrowForecast('03002').subscribe({
      error: (e: unknown) => (error = e instanceof WeatherApiError ? e : undefined),
    });
    respond(httpTesting.expectOne((r) => r.url === FORECAST_URL));
    return error;
  }
});

function forecastBody(): ForecastDto {
  return { mediaTemperatura: 19.3, unidadTemperatura: 'G_CEL', probPrecipitacion: [] };
}
