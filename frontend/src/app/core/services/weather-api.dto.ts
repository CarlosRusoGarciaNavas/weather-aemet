import { TemperatureUnit } from '../models/temperature-unit';

// Field names are fixed by the API contract, hence in Spanish. They must not leak past this folder.

export interface MunicipalityDto {
  codigo: string;
  nombre: string;
}

export interface PrecipitationDto {
  probabilidad: number;
  periodo: string;
}

export interface ForecastDto {
  mediaTemperatura: number;
  unidadTemperatura: TemperatureUnit;
  probPrecipitacion: PrecipitationDto[];
}
