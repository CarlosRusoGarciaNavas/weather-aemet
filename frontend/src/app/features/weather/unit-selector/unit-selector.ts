import { Component, input, output } from '@angular/core';

import { TemperatureUnit } from '../../../core/models/temperature-unit';

interface UnitOption {
  value: TemperatureUnit;
  label: string;
}

const OPTIONS: readonly UnitOption[] = [
  { value: 'G_CEL', label: 'Celsius (°C)' },
  { value: 'G_FAH', label: 'Fahrenheit (°F)' },
];

/** Makes the radio group name unique when the component appears more than once on a page. */
let nextInstanceId = 0;

/** Native radios: the browser already provides the group semantics and arrow-key navigation. */
@Component({
  selector: 'app-unit-selector',
  styleUrl: './unit-selector.css',
  templateUrl: './unit-selector.html',
})
export class UnitSelector {
  readonly unit = input.required<TemperatureUnit>();
  readonly changed = output<TemperatureUnit>();

  protected readonly options = OPTIONS;
  protected readonly groupName = `unit-selector-${nextInstanceId++}`;
}
