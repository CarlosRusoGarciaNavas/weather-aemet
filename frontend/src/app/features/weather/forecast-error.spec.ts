import { WeatherApiErrorCategory } from '../../core/models/weather-api-error';
import { canRetryForecast, forecastErrorMessage } from './forecast-error';

describe('forecastErrorMessage', () => {
  it.each<[WeatherApiErrorCategory, string]>([
    ['not-found', 'No hay previsión disponible para este municipio.'],
    ['invalid-request', 'La petición no es válida. Prueba a elegir de nuevo el municipio.'],
    [
      'unavailable',
      'No se ha podido contactar con el servicio meteorológico. Inténtalo de nuevo en unos minutos.',
    ],
    [
      'rate-limited',
      'Hay demasiadas peticiones en este momento. Espera unos segundos y vuelve a intentarlo.',
    ],
    ['unknown', 'Se ha producido un error inesperado al obtener la previsión.'],
  ])('describes %s in Spanish', (category, message) => {
    expect(forecastErrorMessage(category)).toBe(message);
  });
});

describe('canRetryForecast', () => {
  it.each<[WeatherApiErrorCategory, boolean]>([
    ['not-found', false],
    ['invalid-request', false],
    ['unavailable', true],
    ['rate-limited', true],
    ['unknown', true],
  ])('%s -> %s', (category, retry) => {
    expect(canRetryForecast(category)).toBe(retry);
  });
});
