export type WeatherApiErrorCategory =
  | 'invalid-request'
  | 'not-found'
  | 'unavailable'
  | 'rate-limited'
  | 'unknown';

export class WeatherApiError extends Error {
  readonly category: WeatherApiErrorCategory;

  /** status 0 means the request never got an HTTP answer (network failure, backend down). */
  constructor(readonly status: number) {
    const category = categoryForStatus(status);
    super(`Weather API request failed: ${category} (HTTP ${status})`);
    this.name = 'WeatherApiError';
    this.category = category;
  }
}

/** Mirrors the backend: 502 = provider down, 503 = provider rate limit. 504 = a proxy timed out. */
function categoryForStatus(status: number): WeatherApiErrorCategory {
  switch (status) {
    case 400:
      return 'invalid-request';
    case 404:
      return 'not-found';
    case 0:
    case 502:
    case 504:
      return 'unavailable';
    case 503:
      return 'rate-limited';
    default:
      return 'unknown';
  }
}
