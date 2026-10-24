import { WeatherApiErrorCategory } from '../../core/models/weather-api-error';

/** A Record forces a message for every category: adding one to the union breaks the build here. */
const MESSAGES: Record<WeatherApiErrorCategory, string> = {
  'not-found': 'No hay previsión disponible para este municipio.',
  'invalid-request': 'La petición no es válida. Prueba a elegir de nuevo el municipio.',
  unavailable:
    'No se ha podido contactar con el servicio meteorológico. Inténtalo de nuevo en unos minutos.',
  // Covers both causes: our own limit (429) and AEMET throttling the backend (503).
  'rate-limited':
    'Hay demasiadas peticiones en este momento. Espera unos segundos y vuelve a intentarlo.',
  unknown: 'Se ha producido un error inesperado al obtener la previsión.',
};

export function forecastErrorMessage(category: WeatherApiErrorCategory): string {
  return MESSAGES[category];
}

/** Repeating the same request cannot fix a missing municipality or a request the backend rejects. */
export function canRetryForecast(category: WeatherApiErrorCategory): boolean {
  return category !== 'not-found' && category !== 'invalid-request';
}
