import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TemperatureUnit } from '../../../core/models/temperature-unit';
import { UnitSelector } from './unit-selector';

describe('UnitSelector', () => {
  let fixture: ComponentFixture<UnitSelector>;
  let element: HTMLElement;
  let emitted: TemperatureUnit[];

  beforeEach(() => {
    fixture = TestBed.createComponent(UnitSelector);
    fixture.componentRef.setInput('unit', 'G_CEL');
    emitted = [];
    fixture.componentInstance.changed.subscribe((unit) => emitted.push(unit));
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  });

  function radios(): HTMLInputElement[] {
    return Array.from(element.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
  }

  it('groups two native radios under the legend "Unidad"', () => {
    const labels = Array.from(element.querySelectorAll('label')).map((label) =>
      label.textContent!.trim(),
    );

    expect(element.querySelector('fieldset > legend')?.textContent).toBe('Unidad');
    expect(labels).toEqual(['Celsius (°C)', 'Fahrenheit (°F)']);
    expect(radios().map((radio) => radio.value)).toEqual(['G_CEL', 'G_FAH']);
    expect(radios()[0].name).not.toBe('');
    expect(radios()[1].name).toBe(radios()[0].name);
  });

  it('checks the radio of the given unit', () => {
    expect(radios().map((radio) => radio.checked)).toEqual([true, false]);

    fixture.componentRef.setInput('unit', 'G_FAH');
    fixture.detectChanges();

    expect(radios().map((radio) => radio.checked)).toEqual([false, true]);
  });

  it('emits the chosen unit', () => {
    radios()[1].click();

    expect(emitted).toEqual(['G_FAH']);
  });

  it('does not emit when the checked radio is clicked again', () => {
    radios()[0].click();

    expect(emitted).toEqual([]);
  });

  it('uses a different group name in each instance', () => {
    const other = TestBed.createComponent(UnitSelector);
    other.componentRef.setInput('unit', 'G_CEL');
    other.detectChanges();
    const otherRadio = (other.nativeElement as HTMLElement).querySelector('input')!;

    expect(otherRadio.name).not.toBe(radios()[0].name);
  });
});
