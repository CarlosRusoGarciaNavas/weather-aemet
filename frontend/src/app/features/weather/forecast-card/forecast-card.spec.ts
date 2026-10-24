import { TestBed } from '@angular/core/testing';

import { Forecast } from '../../../core/models/forecast';
import { ForecastCard } from './forecast-card';

function render(forecast: Forecast): HTMLElement {
  const fixture = TestBed.createComponent(ForecastCard);
  fixture.componentRef.setInput('forecast', forecast);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function temperature(element: HTMLElement): string {
  return element.querySelector('.value')!.textContent!.trim();
}

function rows(element: HTMLElement): string[][] {
  return Array.from(element.querySelectorAll('tbody tr')).map((row) =>
    Array.from(row.children).map((cell) => cell.textContent!.trim()),
  );
}

describe('ForecastCard', () => {
  it('shows the average temperature in Spanish format with one decimal', () => {
    expect(temperature(render({ averageTemperature: 17.5, unit: 'G_CEL', precipitation: [] }))).toBe(
      '17,5 °C',
    );
    expect(temperature(render({ averageTemperature: 18, unit: 'G_CEL', precipitation: [] }))).toBe(
      '18,0 °C',
    );
    expect(temperature(render({ averageTemperature: -2.3, unit: 'G_CEL', precipitation: [] }))).toBe(
      '-2,3 °C',
    );
  });

  it('uses the symbol of the unit in the forecast', () => {
    expect(temperature(render({ averageTemperature: 63.5, unit: 'G_FAH', precipitation: [] }))).toBe(
      '63,5 °F',
    );
  });

  it('lists the precipitation periods in the order received, keeping a real 0', () => {
    const element = render({
      averageTemperature: 17.5,
      unit: 'G_CEL',
      precipitation: [
        { period: '12-18', probability: 40 },
        { period: '00-06', probability: 0 },
        { period: '18-24', probability: 100 },
      ],
    });

    expect(element.querySelector('table > caption')?.textContent).toBe(
      'Probabilidad de precipitación',
    );
    expect(rows(element)).toEqual([
      ['12-18 h', '40 %'],
      ['00-06 h', '0 %'],
      ['18-24 h', '100 %'],
    ]);
    expect(element.querySelector('.empty')).toBeNull();
  });

  it('shows a message instead of the table when there are no periods', () => {
    const element = render({ averageTemperature: 17.5, unit: 'G_CEL', precipitation: [] });

    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('.empty')?.textContent).toBe(
      'No hay datos de probabilidad de precipitación para mañana.',
    );
  });
});
