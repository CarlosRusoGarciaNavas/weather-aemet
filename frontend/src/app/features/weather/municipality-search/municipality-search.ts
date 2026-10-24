import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';

import { Municipality } from '../../../core/models/municipality';
import { WeatherApiService } from '../../../core/services/weather-api.service';

export const MAX_VISIBLE_RESULTS = 50;
const DEBOUNCE_MS = 250;
const MIN_SEARCH_LENGTH = 2;

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'results'; municipalities: Municipality[] }
  | { status: 'empty' }
  | { status: 'error' };

/** Makes the ids unique when the component appears more than once on a page. */
let nextInstanceId = 0;

/** Accessible combobox (WAI-ARIA APG pattern) that searches municipalities by prefix. */
@Component({
  selector: 'app-municipality-search',
  styleUrl: './municipality-search.css',
  templateUrl: './municipality-search.html',
})
export class MunicipalitySearch {
  private readonly api = inject(WeatherApiService);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  /** Name of the municipality selected from outside (e.g. restored on start). Shown without searching. */
  readonly selectedName = input<string | null>(null);
  readonly selected = output<Municipality>();

  private readonly idPrefix = `municipality-search-${nextInstanceId++}`;
  protected readonly inputId = `${this.idPrefix}-input`;
  protected readonly listboxId = `${this.idPrefix}-listbox`;

  protected readonly text = signal('');
  protected readonly state = signal<SearchState>({ status: 'idle' });
  protected readonly open = signal(false);
  protected readonly activeIndex = signal(-1);

  protected readonly visibleResults = computed(() => {
    const state = this.state();
    return state.status === 'results' ? state.municipalities.slice(0, MAX_VISIBLE_RESULTS) : [];
  });
  protected readonly expanded = computed(() => this.open() && this.visibleResults().length > 0);
  protected readonly activeOptionId = computed(() =>
    this.expanded() && this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : null,
  );
  protected readonly statusMessage = computed(() => statusMessage(this.state()));

  private readonly typed = new Subject<string>();
  private readonly searchResets = new Subject<void>();

  constructor() {
    // Each reset subscribes again to what is typed: that drops a pending debounce, cancels a request
    // in flight and forgets the last term seen by distinctUntilChanged.
    this.searchResets
      .pipe(
        startWith(undefined),
        switchMap(() =>
          this.typed.pipe(
            debounceTime(DEBOUNCE_MS),
            map((text) => text.trim()),
            distinctUntilChanged(),
            switchMap((term) => this.search(term)),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.showState(state));

    effect(() => {
      const name = this.selectedName() ?? '';
      untracked(() => this.showSelection(name));
    });
  }

  protected optionId(index: number): string {
    return `${this.listboxId}-option-${index}`;
  }

  protected onInput(text: string): void {
    this.text.set(text);
    this.typed.next(text);
  }

  protected onKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        this.moveActive(event, 1);
        break;
      case 'ArrowUp':
        this.moveActive(event, -1);
        break;
      case 'Enter': {
        const active = this.visibleResults()[this.activeIndex()];
        if (this.expanded() && active) {
          event.preventDefault();
          this.select(active);
        }
        break;
      }
      case 'Escape':
        if (this.expanded()) {
          event.preventDefault();
          this.close();
        }
        break;
    }
  }

  /** preventDefault on mousedown keeps the focus in the input, so blur does not close the list first. */
  protected onOptionMouseDown(event: MouseEvent, municipality: Municipality): void {
    event.preventDefault();
    this.select(municipality);
  }

  protected close(): void {
    this.open.set(false);
    this.activeIndex.set(-1);
  }

  private select(municipality: Municipality): void {
    this.showSelection(municipality.name);
    this.selected.emit(municipality);
  }

  private showSelection(name: string): void {
    this.searchResets.next();
    this.text.set(name);
    this.showState({ status: 'idle' });
  }

  private showState(state: SearchState): void {
    this.state.set(state);
    this.activeIndex.set(-1);
    this.open.set(state.status === 'results');
  }

  /** Cyclic. Also reopens the list closed with Escape while there are results. */
  private moveActive(event: KeyboardEvent, step: 1 | -1): void {
    const count = this.visibleResults().length;
    if (count === 0) {
      return;
    }
    event.preventDefault();
    this.open.set(true);
    const current = this.activeIndex();
    const next = current === -1 ? (step === 1 ? 0 : count - 1) : (current + step + count) % count;
    this.activeIndex.set(next);
    this.host.nativeElement
      .querySelector(`#${this.optionId(next)}`)
      ?.scrollIntoView({ block: 'nearest' });
  }

  /** catchError lives inside switchMap: an error ends only this search, not the whole stream. */
  private search(term: string): Observable<SearchState> {
    if (term.length < MIN_SEARCH_LENGTH) {
      return of<SearchState>({ status: 'idle' });
    }
    return this.api.searchMunicipalities(term).pipe(
      map(
        (municipalities): SearchState =>
          municipalities.length === 0 ? { status: 'empty' } : { status: 'results', municipalities },
      ),
      catchError(() => of<SearchState>({ status: 'error' })),
      startWith<SearchState>({ status: 'loading' }),
    );
  }
}

function statusMessage(state: SearchState): string {
  switch (state.status) {
    case 'results': {
      const total = state.municipalities.length;
      if (total > MAX_VISIBLE_RESULTS) {
        return `${total} municipios encontrados; se muestran los ${MAX_VISIBLE_RESULTS} primeros. Escribe más letras para afinar.`;
      }
      return total === 1 ? '1 municipio encontrado.' : `${total} municipios encontrados.`;
    }
    case 'empty':
      return 'Ningún municipio empieza por ese texto.';
    case 'error':
      return 'No se han podido buscar los municipios. Inténtalo de nuevo.';
    default:
      return '';
  }
}
