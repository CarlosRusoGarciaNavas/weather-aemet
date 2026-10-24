import { TemperatureUnit } from './temperature-unit';

export interface PrecipitationProbability {
  probability: number;
  period: string;
}

export interface Forecast {
  averageTemperature: number;
  unit: TemperatureUnit;
  precipitation: PrecipitationProbability[];
}
