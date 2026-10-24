import { Component, computed, input } from '@angular/core';

import { Forecast } from '../../../core/models/forecast';
import { TemperatureUnit } from '../../../core/models/temperature-unit';

/** Created once: building a NumberFormat is far more expensive than using it. */
const TEMPERATURE_FORMAT = new Intl.NumberFormat('es-ES', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

const UNIT_SYMBOLS: Record<TemperatureUnit, string> = { G_CEL: '°C', G_FAH: '°F' };

/** Presentation only: shows tomorrow's forecast it is given. */
@Component({
  selector: 'app-forecast-card',
  styleUrl: './forecast-card.css',
  templateUrl: './forecast-card.html',
})
export class ForecastCard {
  readonly forecast = input.required<Forecast>();

  protected readonly temperature = computed(() => {
    const { averageTemperature, unit } = this.forecast();
    return `${TEMPERATURE_FORMAT.format(averageTemperature)} ${UNIT_SYMBOLS[unit]}`;
  });
}
