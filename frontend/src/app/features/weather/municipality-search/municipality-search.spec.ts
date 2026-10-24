import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';

import { Municipality } from '../../../core/models/municipality';
import { WeatherApiError } from '../../../core/models/weather-api-error';
import { WeatherApiService } from '../../../core/services/weather-api.service';
import { MunicipalitySearch } from './municipality-search';

const AGOST = { code: '03002', name: 'Agost' };
const AGRES = { code: '03003', name: 'Agres' };
const AGUILAS = { code: '30003', name: 'Águilas' };

/** Each call returns a Subject the test controls, so it decides when (and whether) each search answers. */
class FakeWeatherApi {
  readonly calls: { prefix: string; response: Subject<Municipality[]> }[] = [];

  searchMunicipalities(prefix: string): Observable<Municipality[]> {
    const response = new Subject<Municipality[]>();
    this.calls.push({ prefix, response });
    return response;
  }

  answer(callIndex: number, municipalities: Municipality[]): void {
    this.calls[callIndex].response.next(municipalities);
    this.calls[callIndex].response.complete();
  }
}

describe('MunicipalitySearch', () => {
  let api: FakeWeatherApi;
  let fixture: ComponentFixture<MunicipalitySearch>;
  let element: HTMLElement;
  let input: HTMLInputElement;
  let emitted: Municipality[];

  beforeEach(() => {
    vi.useFakeTimers();
    // jsdom does not implement scrolling.
    Element.prototype.scrollIntoView = vi.fn();
    api = new FakeWeatherApi();
    TestBed.configureTestingModule({
      imports: [MunicipalitySearch],
      providers: [{ provide: WeatherApiService, useValue: api }],
    });
    fixture = TestBed.createComponent(MunicipalitySearch);
    emitted = [];
    fixture.componentInstance.selected.subscribe((municipality) => emitted.push(municipality));
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
    input = element.querySelector('input')!;
  });

  afterEach(() => vi.useRealTimers());

  function type(text: string): void {
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function wait(ms = 250): void {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
  }

  function search(text: string): void {
    type(text);
    wait();
  }

  function answer(callIndex: number, municipalities: Municipality[]): void {
    api.answer(callIndex, municipalities);
    fixture.detectChanges();
  }

  function press(key: string): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, cancelable: true });
    input.dispatchEvent(event);
    fixture.detectChanges();
    return event;
  }

  function options(): HTMLElement[] {
    return Array.from(element.querySelectorAll<HTMLElement>('[role="option"]'));
  }

  function optionNames(): string[] {
    return options().map((option) => option.textContent!.trim());
  }

  function listbox(): HTMLElement {
    return element.querySelector('[role="listbox"]')!;
  }

  function status(): string {
    return element.querySelector('[role="status"]')!.textContent!.trim();
  }

  function isExpanded(): boolean {
    return input.getAttribute('aria-expanded') === 'true' && !listbox().hidden;
  }

  function showResults(municipalities: Municipality[] = [AGOST, AGRES, AGUILAS]): void {
    search('Ag');
    answer(api.calls.length - 1, municipalities);
  }

  describe('search', () => {
    it('sends a single request for several quick keystrokes, after the debounce', () => {
      type('Ag');
      wait(100);
      type('Ago');
      wait(100);
      type('Agos');
      wait(249);

      expect(api.calls).toHaveLength(0);

      wait(1);

      expect(api.calls.map((call) => call.prefix)).toEqual(['Agos']);
      expect(element.querySelector('.hint')?.textContent).toContain('Buscando');
    });

    it('does not search with fewer than 2 characters and closes the list', () => {
      showResults();
      expect(isExpanded()).toBe(true);

      search('A');

      expect(api.calls).toHaveLength(1);
      expect(isExpanded()).toBe(false);
      expect(options()).toHaveLength(0);
      expect(status()).toBe('');
    });

    it('trims the text and does not repeat the same term', () => {
      search('Ag');
      search(' Ag ');

      expect(api.calls.map((call) => call.prefix)).toEqual(['Ag']);
    });

    it('does not let a slow old response overwrite a newer one', () => {
      search('Ag');
      search('Agr');

      answer(1, [AGRES]);
      answer(0, [AGOST, AGRES, AGUILAS]);

      expect(api.calls[0].response.observed).toBe(false);
      expect(optionNames()).toEqual(['Agres']);
    });

    it('shows an error and still searches afterwards', () => {
      search('Ag');
      api.calls[0].response.error(new WeatherApiError(502));
      fixture.detectChanges();

      expect(status()).toBe('No se han podido buscar los municipios. Inténtalo de nuevo.');
      expect(isExpanded()).toBe(false);

      search('Agr');
      answer(1, [AGRES]);

      expect(optionNames()).toEqual(['Agres']);
      expect(status()).toBe('1 municipio encontrado.');
    });

    it('says so when nothing matches', () => {
      search('Zz');
      answer(0, []);

      expect(status()).toBe('Ningún municipio empieza por ese texto.');
      expect(isExpanded()).toBe(false);
      expect(options()).toHaveLength(0);
    });

    it('shows at most 50 results and warns when there are more', () => {
      const many = Array.from({ length: 120 }, (_, i) => ({ code: `${i}`, name: `Municipio ${i}` }));

      showResults(many);

      expect(options()).toHaveLength(50);
      expect(status()).toBe(
        '120 municipios encontrados; se muestran los 50 primeros. Escribe más letras para afinar.',
      );
    });

    it('does not warn with exactly 50 results', () => {
      const fifty = Array.from({ length: 50 }, (_, i) => ({ code: `${i}`, name: `Municipio ${i}` }));

      showResults(fifty);

      expect(options()).toHaveLength(50);
      expect(status()).toBe('50 municipios encontrados.');
    });
  });

  describe('keyboard', () => {
    it('moves the active option cyclically with the arrows', () => {
      showResults();

      press('ArrowDown');
      expect(input.getAttribute('aria-activedescendant')).toBe(options()[0].id);

      press('ArrowUp');
      expect(input.getAttribute('aria-activedescendant')).toBe(options()[2].id);

      press('ArrowDown');
      expect(input.getAttribute('aria-activedescendant')).toBe(options()[0].id);

      expect(options().map((option) => option.getAttribute('aria-selected'))).toEqual([
        'true',
        'false',
        'false',
      ]);
    });

    it('starts from the last option with ArrowUp', () => {
      showResults();

      const event = press('ArrowUp');

      expect(event.defaultPrevented).toBe(true);
      expect(input.getAttribute('aria-activedescendant')).toBe(options()[2].id);
    });

    it('selects the active option with Enter', () => {
      showResults();
      press('ArrowDown');
      press('ArrowDown');

      const event = press('Enter');

      expect(event.defaultPrevented).toBe(true);
      expect(emitted).toEqual([AGRES]);
      expect(input.value).toBe('Agres');
      expect(isExpanded()).toBe(false);
      expect(input.hasAttribute('aria-activedescendant')).toBe(false);
    });

    it('does nothing on Enter without an active option', () => {
      showResults();

      const event = press('Enter');

      expect(event.defaultPrevented).toBe(false);
      expect(emitted).toEqual([]);
      expect(isExpanded()).toBe(true);
    });

    it('closes the list with Escape and reopens it with ArrowDown', () => {
      showResults();
      press('ArrowDown');

      press('Escape');

      expect(isExpanded()).toBe(false);
      expect(input.hasAttribute('aria-activedescendant')).toBe(false);
      expect(input.value).toBe('Ag');

      press('ArrowDown');

      expect(isExpanded()).toBe(true);
      expect(input.getAttribute('aria-activedescendant')).toBe(options()[0].id);
    });

    it('keeps the list open on mousedown in the list itself (e.g. its scrollbar)', () => {
      showResults();
      const event = new MouseEvent('mousedown', { cancelable: true });

      listbox().dispatchEvent(event);
      fixture.detectChanges();

      expect(event.defaultPrevented).toBe(true);
      expect(isExpanded()).toBe(true);
      expect(emitted).toEqual([]);
    });

    it('selects the whole text on focus without searching', () => {
      fixture.componentRef.setInput('selectedName', 'Agost');
      fixture.detectChanges();

      input.dispatchEvent(new FocusEvent('focus'));
      fixture.detectChanges();
      wait(1000);

      expect(input.selectionStart).toBe(0);
      expect(input.selectionEnd).toBe('Agost'.length);
      expect(api.calls).toHaveLength(0);
    });

    it('closes the list when the input loses focus', () => {
      showResults();

      input.dispatchEvent(new FocusEvent('blur'));
      fixture.detectChanges();

      expect(isExpanded()).toBe(false);
    });
  });

  describe('selection', () => {
    it('selects with mousedown, preventing the default to keep the focus', () => {
      showResults();
      const event = new MouseEvent('mousedown', { cancelable: true });

      options()[2].dispatchEvent(event);
      fixture.detectChanges();

      expect(event.defaultPrevented).toBe(true);
      expect(emitted).toEqual([AGUILAS]);
      expect(input.value).toBe('Águilas');
      expect(isExpanded()).toBe(false);
      expect(status()).toBe('');
    });

    it('does not launch another search when selecting', () => {
      showResults();
      press('ArrowDown');
      press('Enter');

      wait(1000);

      expect(api.calls).toHaveLength(1);
    });

    it('drops a pending debounced search when selecting', () => {
      showResults();
      type('Agr');

      options()[1].dispatchEvent(new MouseEvent('mousedown', { cancelable: true }));
      wait(1000);

      expect(api.calls).toHaveLength(1);
      expect(input.value).toBe('Agres');
      expect(isExpanded()).toBe(false);
    });

    it('drops a response in flight when a name is selected from outside', () => {
      search('Agr');

      fixture.componentRef.setInput('selectedName', 'Adra');
      fixture.detectChanges();
      answer(0, [AGRES]);

      expect(api.calls[0].response.observed).toBe(false);
      expect(input.value).toBe('Adra');
      expect(isExpanded()).toBe(false);
      expect(element.querySelector('.hint')).toBeNull();
    });

    it('searches again for the same term typed after a selection', () => {
      showResults();
      press('ArrowDown');
      press('Enter');

      search('Ag');

      expect(api.calls.map((call) => call.prefix)).toEqual(['Ag', 'Ag']);
    });

    it('shows the name selected from outside without searching', () => {
      showResults();
      type('Agr');

      fixture.componentRef.setInput('selectedName', 'Adra');
      fixture.detectChanges();
      wait(1000);

      expect(input.value).toBe('Adra');
      expect(api.calls).toHaveLength(1);
      expect(isExpanded()).toBe(false);
      expect(emitted).toEqual([]);
    });
  });

  describe('ARIA', () => {
    it('exposes the combobox pattern', () => {
      const label = element.querySelector('label')!;

      expect(input.getAttribute('role')).toBe('combobox');
      expect(input.getAttribute('aria-autocomplete')).toBe('list');
      expect(input.getAttribute('aria-expanded')).toBe('false');
      expect(input.getAttribute('aria-controls')).toBe(listbox().id);
      expect(input.hasAttribute('aria-activedescendant')).toBe(false);
      expect(label.htmlFor).toBe(input.id);

      const live = element.querySelector('[role="status"]')!;
      expect(live.getAttribute('aria-live')).toBe('polite');
    });

    it('marks the expanded list and gives each option a unique id', () => {
      showResults();

      expect(input.getAttribute('aria-expanded')).toBe('true');
      const ids = options().map((option) => option.id);
      expect(new Set(ids).size).toBe(3);
      expect(ids.every((id) => id.startsWith(listbox().id))).toBe(true);
      expect(options().every((option) => option.getAttribute('aria-selected') === 'false')).toBe(
        true,
      );
    });

    it('uses different ids in each instance', () => {
      const other = TestBed.createComponent(MunicipalitySearch);
      other.detectChanges();
      const otherInput = (other.nativeElement as HTMLElement).querySelector('input')!;

      expect(otherInput.id).not.toBe(input.id);
      expect(otherInput.getAttribute('aria-controls')).not.toBe(input.getAttribute('aria-controls'));
    });
  });
});
