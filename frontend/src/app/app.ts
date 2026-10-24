import { Component } from '@angular/core';

import { WeatherPage } from './features/weather/weather-page/weather-page';

@Component({
  imports: [WeatherPage],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {}
