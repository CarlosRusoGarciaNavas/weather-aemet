# Respuestas a las preguntas teóricas

## 1. Lista de municipios lenta

El listado completo de AEMET tarda en llegar (son dos llamadas encadenadas y varios miles de municipios), y como casi nunca cambia no tiene sentido pedirlo en cada búsqueda. Lo que he hecho:

- Caché en el servidor con Caffeine durante 24 horas. Si llegan varias peticiones a la vez con la caché vacía, solo una llama a AEMET.
- Precarga al arrancar, en segundo plano, para que la primera búsqueda no espere.
- Búsqueda por prefijo en el servidor: el navegador recibe solo los municipios que coinciden, no el listado entero.
- En el frontend, espero 250 ms tras la última tecla, no busco con menos de 2 caracteres y cancelo la petición anterior si el usuario sigue escribiendo.
- Si AEMET falla, sirvo la última copia buena (hasta 6 horas).

Mejoras posibles: guardar el catálogo en disco o base de datos para no depender de AEMET ni al arrancar, precalcular un índice con los nombres normalizados (sin tildes ni mayúsculas) y añadir ETag y Cache-Control para que el navegador reutilice respuestas.

## 2. ¿Por qué AEMET devuelve una URL en vez de los datos?

AEMET no lo documenta, así que esto es mi interpretación. Creo que separa dos cosas: la primera llamada comprueba la clave, aplica las cuotas y valida la petición, que es barato; la segunda sirve los datos, que probablemente son ficheros ya generados y alojados donde se pueden cachear y escalar sin pasar por la lógica de la API. Además, el sobre (`descripcion`, `estado`, `datos`, `metadatos`) permite devolver contenidos muy distintos con la misma interfaz y facilita controlar abusos en un punto.

El coste para el cliente es que hay dos llamadas y dos puntos de fallo, que los errores pueden llegar con HTTP 200 y un estado 404 o 429, y que hay que validar la URL recibida. Por eso solo la sigo si es https y del mismo host que la base configurada: la clave va en una cabecera y no debe salir hacia otro sitio.

## 3. Recordar el último municipio

Al elegir un municipio o cambiar la unidad guardo `{código, nombre, unidad}` en `localStorage`, con una clave con versión (`weather.last-selection.v1`). Al abrir la página lo leo, lo valido (código de 5 dígitos, nombre no vacío, unidad conocida), relleno el campo y cargo la previsión del día siguiente. Si el almacenamiento falla, la página arranca vacía.

Guardo la elección y no la previsión, para que los datos sean siempre actuales. Alternativas: cookies (se envían en cada petición sin necesidad), IndexedDB (demasiado para tres campos) o guardarlo en el servidor por usuario (exigiría cuentas y plantea privacidad).

## 4. Evitar la sobrecarga de AEMET

La idea es que el tráfico de los usuarios no se traslade a AEMET: con N usuarios, AEMET no debe ver N llamadas.

- Cachés en el servidor: catálogo 24 h, predicciones 30 min por municipio y fecha, con tamaño máximo. Los errores no se cachean.
- Una sola llamada por clave aunque haya peticiones simultáneas.
- Validación del código de municipio contra el catálogo en caché antes de llamar, para que entradas inventadas no generen tráfico.
- Interruptor de circuito (`ProviderGuard`): tras un fallo, durante 30 s no se llama a AEMET y se sirve la última respuesta buena si existe. Un 429 de AEMET se traduce en un 503 con `Retry-After`.
- Frontend: espera de 250 ms, mínimo de caracteres y cancelación de peticiones anteriores.
- nginx: límite de 10 peticiones por segundo por IP con ráfaga de 20. En una prueba de 60 peticiones seguidas, 27 pasaron y 33 recibieron 429.

Mejoras posibles: alinear el TTL con las actualizaciones reales de AEMET o renovar en segundo plano (`refreshAfterWrite`), Resilience4j en lugar del interruptor propio, una caché compartida (Redis) si hay varias instancias, métricas expuestas, `realip` en nginx tras un balanceador, y pedir a AEMET una cuota mayor si el uso lo justifica.
