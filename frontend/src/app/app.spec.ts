import { TestBed } from '@angular/core/testing';

import { App } from './app';
import { WeatherApiService } from './core/services/weather-api.service';
import { LastSelectionStorage } from './core/storage/last-selection.storage';

describe('App', () => {
  it('renders the weather page without asking for anything when nothing is saved', () => {
    const api = { getTomorrowForecast: vi.fn(), searchMunicipalities: vi.fn() };
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: WeatherApiService, useValue: api },
        { provide: LastSelectionStorage, useValue: { get: () => null, save: vi.fn() } },
      ],
    });

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('app-weather-page h1')?.textContent).toBe('Previsión meteorológica');
    expect(element.querySelector('app-municipality-search input')).not.toBeNull();
    expect(element.querySelector('app-unit-selector fieldset')).not.toBeNull();
    expect(api.getTomorrowForecast).not.toHaveBeenCalled();
  });
});
