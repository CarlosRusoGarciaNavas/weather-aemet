import { Component, inject } from '@angular/core';

import { ForecastCard } from '../forecast-card/forecast-card';
import { canRetryForecast, forecastErrorMessage } from '../forecast-error';
import { MunicipalitySearch } from '../municipality-search/municipality-search';
import { UnitSelector } from '../unit-selector/unit-selector';
import { WeatherStore } from '../weather.store';

/** The only component that knows the store: the others receive data and emit events. */
@Component({
  selector: 'app-weather-page',
  imports: [ForecastCard, MunicipalitySearch, UnitSelector],
  providers: [WeatherStore],
  styleUrl: './weather-page.css',
  templateUrl: './weather-page.html',
})
export class WeatherPage {
  protected readonly store = inject(WeatherStore);
  protected readonly errorMessage = forecastErrorMessage;
  protected readonly canRetry = canRetryForecast;
}
