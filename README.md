# Previsión meteorológica con AEMET

Aplicación web que busca un municipio español y muestra la previsión del día siguiente: temperatura media
(en grados Celsius o Fahrenheit) y probabilidad de precipitación por tramos de 6 horas. Los datos vienen de
AEMET OpenData. El backend traduce y cachea las respuestas de AEMET y expone una API REST propia; el
frontend la consume a través de nginx.

Stack:

- Backend: Java 21, Spring Boot 4.1.1, Maven 3.10.0 (wrapper `mvnw`), Caffeine, JUnit.
- Frontend: Angular 22.2 (componentes standalone y signals), TypeScript 6.0, RxJS 7.8, Vitest 5.
- Contenedores: Docker Compose con dos servicios (backend en `eclipse-temurin:21-jre-alpine`,
  frontend en `nginx:1.30.0-alpine`).

Hace falta una API key de AEMET OpenData (se solicita gratis en https://opendata.aemet.es).

## Ejecución con Docker (recomendada)

Requisitos: Docker con Compose v2.

```bash
cp .env.example .env
# Editar .env y poner la clave: AEMET_API_KEY=...
docker compose up --build
```

La aplicación queda en http://localhost:8081.

- El backend no publica puertos: solo es accesible desde la red de Compose, a través del proxy `/api/` de
  nginx. El frontend espera a que el healthcheck del backend (`/actuator/health`) esté en verde.
- Compose exige `AEMET_API_KEY` en todos sus comandos (también `ps` y `down`); si falta, se detiene con un
  mensaje que remite a `.env.example`.
- Para validar el fichero de Compose usa `docker compose config --quiet`. Sin `--quiet` imprime la
  configuración resuelta, clave incluida.

**No subas nunca `.env` al repositorio.** Contiene la clave y está en `.gitignore`.

## Ejecución local sin Docker

### Backend

Requisitos: JDK 21 (`java.version` 21 en `pom.xml`). Maven no hace falta: lo descarga el wrapper.

```bash
cd backend
export AEMET_API_KEY=...          # PowerShell: $env:AEMET_API_KEY = "..."
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run
```

Escucha en http://localhost:8080 (puerto por defecto de Spring Boot; no se cambia en `application.yml`).
Si `AEMET_API_KEY` está vacía o no existe, la aplicación no arranca (`@NotBlank` sobre `aemet.api-key`).
Comprueba con `./mvnw -v` que Maven usa un JDK 21.

Al arrancar descarga en segundo plano el listado de municipios (tarda unos segundos) para que la primera búsqueda no
espere a AEMET. Se desactiva con `aemet.cache.warmup-on-startup=false`.

### Frontend

Requisitos: Node `^22.22.3`, `^24.15.0` o `>=26.0.0` (campo `engines` de `@angular/core`; Node 23 y 25
no son compatibles). El proyecto está probado con Node 24, el que usan el Dockerfile y la CI. `package.json` declara `npm@11.19.1`.

```bash
cd frontend
npm ci
npm start                          # ng serve
```

Se abre en http://localhost:4200. `ng serve` usa `src/proxy.conf.json`, que reenvía `/api` a
`http://localhost:8080`, así que el backend debe estar arrancado. El proxy solo se lee al iniciar: si cambias
ese fichero o `angular.json`, reinicia `ng serve`.

El backend permite por CORS el origen `http://localhost:4200` (propiedad `cors.allowed-origins`). Con el
proxy las peticiones son del mismo origen, así que en la práctica solo importa si se llama a `:8080`
directamente desde el navegador.

## API

Todas las respuestas de error son `application/problem+json` (ProblemDetail de Spring), con textos fijos que
no repiten la entrada del usuario ni detalles internos. Los mensajes de error (campo `detail`) son textos
fijos en inglés.

### `GET /api/municipios?nombre={prefijo}`

Búsqueda por prefijo, sin distinguir mayúsculas ni acentos, ordenada por nombre. `codigo` es el id de AEMET
sin el prefijo `id`. `nombre` es obligatorio (máximo 100 caracteres); vacío devuelve `[]`.

```bash
curl 'http://localhost:8081/api/municipios?nombre=coru'
```

```json
[
  {"codigo": "24059", "nombre": "Corullón"},
  {"codigo": "09112", "nombre": "Coruña del Conde"},
  {"codigo": "15030", "nombre": "Coruña, A"}
]
```

### `GET /api/municipios/{codigo}/prediccion/dia-siguiente?unidad=G_CEL|G_FAH`

`codigo` son cinco dígitos. `unidad` es opcional; por defecto `G_CEL`.

```bash
curl 'http://localhost:8081/api/municipios/03002/prediccion/dia-siguiente?unidad=G_FAH'
```

```json
{
  "mediaTemperatura": 60.8,
  "unidadTemperatura": "G_FAH",
  "probPrecipitacion": [
    {"probabilidad": 0, "periodo": "00-06"},
    {"probabilidad": 5, "periodo": "06-12"},
    {"probabilidad": 0, "periodo": "12-18"},
    {"probabilidad": 0, "periodo": "18-24"}
  ]
}
```

(La misma petición sin `unidad` devolvió `"mediaTemperatura": 16.0, "unidadTemperatura": "G_CEL"`.)
Los tramos sin valor en AEMET se omiten, así que la lista puede tener menos de cuatro elementos.

### Errores

| Código | Origen | Cuándo |
|---|---|---|
| 400 | backend | Falta `nombre`, `nombre` con más de 100 caracteres, `codigo` que no son 5 dígitos, `unidad` distinta de `G_CEL`/`G_FAH`. |
| 404 | backend | Código de municipio que no está en el catálogo, o AEMET no tiene previsión con temperaturas para mañana. |
| 502 | backend | AEMET no responde, responde con error (HTTP 4xx/5xx, cuerpo vacío, `estado` distinto de 200) o con datos no válidos, y no hay una respuesta buena anterior que servir. |
| 503 | backend | AEMET rechaza por exceso de peticiones (HTTP 429 o `estado` 429). Lleva `Retry-After: 60` (estimación propia: AEMET no indica cuánto esperar). |
| 429 | nginx | El cliente supera el límite de ritmo de `/api/` (ver más abajo). La respuesta la genera nginx, no el backend. |
| 500 | backend | Error inesperado; la traza va al log y el cuerpo es un texto genérico. |

Ejemplo real:

```bash
curl 'http://localhost:8081/api/municipios/03002/prediccion/dia-siguiente?unidad=KELVIN'
```

```json
{"detail": "Invalid value for parameter 'unidad'.", "instance": "/api/municipios/03002/prediccion/dia-siguiente", "status": 400, "title": "Bad Request"}
```

## Arquitectura y decisiones

### Capas del backend

Hexagonal ligera, con dependencias hacia dentro. `domain` no depende de Spring.

```
infrastructure ──────────────► application ──────────► domain
  aemet   (cliente HTTP,          port    (MunicipalityCatalog,     model     (DailyForecast,
           traducción AEMET)               ForecastSource)                     TemperatureUnit, ...)
  web     (controladores, DTO,    service (búsqueda,                exception
           errores)                        previsión de mañana)
  config  (caché, resiliencia,
           cableado de beans)
```

- `domain`: modelos y reglas de negocio (media de temperatura, conversión a Fahrenheit).
- `application`: puertos (interfaces hacia AEMET) y servicios de caso de uso. Sin Spring: se instancian en
  `WeatherConfig`.
- `infrastructure`: adaptadores. La traducción AEMET -> dominio vive en `infrastructure.aemet`; la API REST
  en `infrastructure.web`.

### Cadena de decoradores

Cada puerto se cablea como `Resilient -> Cached -> adaptador AEMET` (`WeatherConfig`):

- Resilient (`ProviderGuard`, compartido por catálogo y predicciones): tras un fallo de AEMET deja de
  llamarlo durante 30 s (`aemet.resilience.cooldown`) y, si hay una respuesta buena anterior para esa
  petición, la sirve: hasta 6 h las predicciones (`aemet.resilience.stale-ttl`) y hasta 7 días el
  catálogo de municipios (`aemet.resilience.stale-ttl-municipios`). Cada respuesta correcta, también los
  aciertos de caché, renueva ese plazo; el del catálogo debe ser mayor que su caché de 24 h (se valida al
  arrancar), porque si no, tras un hueco sin tráfico, no habría reserva justo cuando caduca la caché y
  AEMET falla. Si no la hay, devuelve el error
  (502 o 503, el mismo tipo que abrió el circuito). Un 404 de AEMET es una respuesta, no un fallo: no abre
  el circuito.
- Cached: va por dentro del resiliente, así que un acierto de caché nunca se ve afectado por el circuito.
  `sync = true`: varias peticiones simultáneas que fallan en caché provocan una sola llamada a AEMET. Las
  excepciones no se cachean.

### Cachés y TTL

AEMET responde con `Cache-Control: no-store`, así que la caché es nuestra (Caffeine, valores de
`application.yml`):

| Caché | TTL | Tamaño máximo |
|---|---|---|
| Municipios (lista completa, una entrada) | 24 h | 1 |
| Predicciones (por municipio y fecha) | 30 min (compromiso entre frescura de los datos y carga sobre AEMET) | 10 000 |
| Última respuesta buena (municipios) | 7 días (mayor que la caché de municipios) | 1 |
| Última respuesta buena (predicciones) | 6 h | 10 000 |

Todas registran estadísticas de aciertos y fallos (`recordStats()`), que hoy no se exponen: Actuator solo
expone `health`.

### Temperatura media

- "Día siguiente" es mañana en `Europe/Madrid`, y el día se localiza en la respuesta de AEMET por `fecha`,
  no por posición.
- La media es la de las lecturas de `temperatura.dato`. Si AEMET no las envía (en la respuesta real que usan
  los tests, `prediccion-agost.json`, llegan vacías desde el tercer día), se usa `(máxima + mínima) / 2`. Se calcula en Celsius y se convierte después.
- El resultado puede diferir del punto medio entre máxima y mínima. Con datos reales de Agost (03002):
  lecturas 14, 24, 20 y 16 °C dan una media de 18,5 °C, mientras que `(26 + 14) / 2` sería 20 °C
  (test `DailyForecastTest.realAgostForecastAveragesReadingsAndConvertsToFahrenheit`).
- El redondeo a un decimal (`HALF_UP`) es presentación: se hace al construir la respuesta en la capa web
  (`ForecastResponse`), no en el dominio.
- Los tramos de precipitación se limitan a 00-06, 06-12, 12-18 y 18-24. Un tramo sin valor se omite; un 0
  real se conserva.

### Cliente de AEMET

- Cada endpoint funciona en dos pasos: un primer GET devuelve un sobre `{descripcion, estado, datos, ...}` y
  un segundo GET a la URL de `datos` devuelve el contenido.
- La clave viaja en la cabecera `api_key` en todas las peticiones, así que antes de seguir la URL de `datos`
  se comprueba que sea `https` y del mismo host que `aemet.base-url` (`opendata.aemet.es`). Si no, se trata
  como fallo del proveedor y la clave no sale hacia otro host.
- Se trata como error: HTTP 4xx/5xx, cuerpo vacío y `estado` distinto de 200 (AEMET devuelve HTTP 200 con
  `estado: 404` para un código inexistente). El cuerpo se lee como bytes porque llega como `text/plain`, y se
  decodifica como UTF-8 estricto con caída a ISO-8859-1.
- Timeouts: conexión 5 s, lectura 30 s.

### Límite de ritmo en nginx

`/api/` está limitado por IP a 10 peticiones/s con `burst=20 nodelay`: las ráfagas cortas (escribir,
recargar, cambiar de unidad) se sirven al momento y lo que excede la ráfaga recibe 429. Se usa 429 y no el
503 por defecto de nginx porque 503 ya significa "AEMET limita al backend". La `location ^~ /api/` evita
que las reglas de ficheros estáticos capturen rutas de la API.

nginx también sirve la SPA con gzip, caché inmutable de un año para los ficheros con hash y `no-cache` para
`index.html`.

### Frontend

- Componentes standalone con signals y plantillas `@if`/`@for`; sin NgModules, enrutado ni SSR.
- `core/` contiene los modelos, `WeatherApiService` y sus DTO (los únicos sitios donde aparecen los nombres
  en español del contrato) y el almacenamiento. `features/weather/` contiene la página, el buscador, el
  selector de unidad y la tarjeta de previsión.
- Las peticiones usan `switchMap`, que cancela la anterior: una respuesta lenta antigua no puede pisar una
  nueva. El buscador espera 250 ms tras la última tecla y no busca con menos de 2 caracteres; muestra como
  máximo 50 resultados.
- El buscador es un combobox accesible (patrón WAI-ARIA APG) con navegación por teclado.
- El último municipio y la unidad elegidos se guardan en `localStorage` (clave versionada
  `weather.last-selection.v1`, validada al leer) y se restauran al abrir la página. Cualquier fallo del
  almacenamiento se ignora.
- Cada código de error de la API tiene su mensaje; 404 y 400 no ofrecen reintentar.

## Solución de problemas

### La página carga, pero no se encuentran municipios ni previsiones

El buscador muestra "No se han podido buscar los municipios. Inténtalo de nuevo." y la tarjeta de previsión
"No se ha podido contactar con el servicio meteorológico. Inténtalo de nuevo en unos minutos." (este segundo
mensaje corresponde a un 502, un 504 o un fallo de red).

Causa más frecuente: la clave de AEMET es incorrecta. AEMET responde HTTP 401 y el backend lo convierte en
502. Para comprobar la clave sin mostrarla, desde la raíz del proyecto (imprimen solo el código HTTP):

```powershell
# PowerShell
$key = (Get-Content .env | Where-Object { $_ -like 'AEMET_API_KEY=*' }) -replace '^AEMET_API_KEY=', ''
curl.exe -s -o NUL -w "%{http_code}`n" -H "api_key: $key" https://opendata.aemet.es/opendata/api/maestro/municipios
```

```bash
# bash
curl -s -o /dev/null -w '%{http_code}\n' -H "api_key: $(grep '^AEMET_API_KEY=' .env | cut -d= -f2-)" \
  https://opendata.aemet.es/opendata/api/maestro/municipios
```

- `200`: la clave es válida. `401`: es incorrecta. Ojo: con la clave vacía AEMET también responde 200 (con
  el cuerpo vacío), así que comprueba antes que `.env` no tiene `AEMET_API_KEY=` sin valor.
- En `.env` la clave va entera, en una sola línea y sin comillas: `AEMET_API_KEY=eyJ...`. Es la clave que
  llega en el cuerpo del correo de AEMET, no el enlace de confirmación.
- Tras cambiar `.env`, recrea los contenedores:

  ```bash
  docker compose down
  docker compose up --build
  ```

### El puerto 8081 está ocupado

El frontend se publica en `8081` (`ports: "8081:80"` en `docker-compose.yml`). Libera ese puerto o cambia
la parte izquierda (por ejemplo `"8082:80"`) y abre la aplicación en el puerto nuevo.

### Docker no responde

Docker Desktop debe estar en marcha antes de ejecutar `docker compose`. `docker info` falla si no lo está.

### Ver los registros del backend

```bash
docker compose logs backend
```

Un fallo de AEMET aparece como `WARN` con el texto `Weather provider unavailable: ...` (con una clave
incorrecta, `AEMET answered HTTP 401`). No pegues la clave en ningún sitio (issues, chats, capturas) y recuerda que `docker compose config` sin `--quiet` la imprime.

## Tests y CI

Backend (JUnit, sin llamadas reales a AEMET):

```bash
cd backend
./mvnw test
```

Frontend (Vitest, sin red):

```bash
cd frontend
npx ng test --no-watch
```

El workflow `.github/workflows/ci.yml` se ejecuta en cada push a `main` y en cada pull request, con
permisos de solo lectura y cancelando ejecuciones anteriores de la misma rama. Tiene tres jobs:

- `backend`: JDK 21 (Temurin) y `./mvnw -B verify` (compila, ejecuta los tests y empaqueta).
- `frontend`: Node 24, `npm ci`, `npm run build` y `npx ng test --no-watch`.
- `docker`: levanta `docker compose up --build --wait` con una clave ficticia, comprueba que la SPA responde
  en `:8081` y que la API responde a través del proxy con `application/problem+json` (AEMET rechaza la clave,
  así que se espera un error con el formato correcto).

Dependabot revisa semanalmente las actions, Maven, npm (los paquetes de Angular agrupados) y las imágenes
Docker (sin saltos de versión mayor).

## Respuestas a las preguntas teóricas

Ver [docs/respuestas.md](docs/respuestas.md).

## Limitaciones conocidas y mejoras

Limitaciones:

- AEMET pone el artículo al final del nombre ("Coruña, A"). Como la búsqueda es por prefijo, "A Coruña" o
  "La Coruña" no encuentran el municipio; hay que escribir "Coruña".
- En Docker Desktop el tráfico local llega a nginx desde la IP del gateway de la red, así que el límite de
  10 r/s es global para todos los clientes locales, no por cliente. Detrás de un balanceador pasaría lo mismo
  si no se configura el módulo `realip` de nginx.
- El tratamiento del 429 real de AEMET (503 con `Retry-After`) solo está probado con tests unitarios (el cliente,
  con `MockRestServiceServer`; el circuito y el manejador de errores, con dobles); no se ha provocado contra
  AEMET.
- `Retry-After: 60` es una estimación propia; AEMET no indica cuánto esperar.

Mejoras:

- Copia persistente del catálogo de municipios, para no depender de AEMET al arrancar ni tras expirar la
  caché.
- Índice normalizado precalculado (sin tildes ni mayúsculas), en lugar de normalizar varios miles de nombres
  en cada búsqueda.
- `ETag` y `Cache-Control` en `/api/municipios`, para que el navegador y nginx reutilicen las respuestas.
- TTL de las predicciones alineado con las actualizaciones de AEMET, o `refreshAfterWrite` para renovar
  las entradas en segundo plano sin que ninguna petición espere.
- Resilience4j como alternativa al interruptor de circuito propio (`ProviderGuard`).
- Caché compartida (Redis) si hay varias instancias del backend, con precarga programada del catálogo.
- Exponer las métricas de Micrometer (las cachés ya registran estadísticas, pero Actuator solo expone
  `health`).
- Configurar `realip` en nginx para limitar por la IP real del cliente cuando haya un balanceador delante.
- Solicitar a AEMET una cuota mayor.
