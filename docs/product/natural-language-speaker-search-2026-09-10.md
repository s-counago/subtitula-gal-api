# Búsqueda por preguntas y hablantes — seguimiento del 10 de septiembre de 2026

**Estado: pendiente de desarrollo.** El diagnóstico está reproducido en desarrollo;
esta actualización documenta el problema y el trabajo siguiente, sin modificar el buscador.

La búsqueda híbrida está activa, pero todavía no resuelve con fiabilidad preguntas
como «quién habló de las pérdidas de las tuberías del agua?». Encontrar un tema,
recuperar su fragmento y responder quién intervino son comprobaciones distintas.

## Prueba observada

Se consultó el endpoint público `/processing/search` de
`https://subtitula-web-dev.s-counago00.workers.dev`, sin sesión ni filtros, sobre
las publicaciones ficticias del piloto. Las cinco peticiones respondieron HTTP 200
y declararon modo `HYBRID`, con similitud mínima configurada de 0,48.

| Consulta enviada | Total comunicado | Observación |
|---|---:|---|
| quién habló de las pérdidas de las tuberías del agua? | 0 | Falso negativo: la publicación sí contiene evidencia relevante |
| pérdidas en las tuberías de agua | 7 | Tema correcto primero; intervención y evidencia en posiciones 2 y 3 |
| reparación de las conducciones de agua | 7 | Recupera el tema y la intervención relevantes en gallego |
| perdas nas tubaxes de auga | 30 | Intervención y evidencia relevantes en posiciones 3 y 4 |
| quen falou das perdas nas tubaxes de auga? | 30 | Recupera la evidencia en posición 4, pero encabeza otro tema sobre preguntas e inicio de las obras |

Los totales cuentan documentos de búsqueda; **no son personas ni intervenciones
únicas**. Un tema, una contribución y su evidencia pueden citar el mismo fragmento.
Los conteos registran esta prueba; no deben convertirse en expectativas rígidas
para un corpus que puede crecer.

Evidencia relevante comprobada:

- Publicación gallega, versión 2: `2890b42f-ee7f-4ca6-81b4-945806bc68ea`.
- Fragmento: `1b949ec3-e4af-479b-ab7d-467ad72ea8ab`, entre 11.179 y 15.939 ms.
- Texto: «A Presidencia propón renovar as tubaxes da rúa do Río para reducir as fugas.»
- Etiqueta del hablante: `Persoa non identificada`.
- [Abrir la evidencia en la sesión ficticia](https://subtitula-web-dev.s-counago00.workers.dev/transparencia/sesion-ficticia-en-galego-auga-e-biblioteca-proba-de-capacidades-4ce077be?t=11&evidence=1b949ec3-e4af-479b-ab7d-467ad72ea8ab).

El [registro estructurado](../operations/evidence/natural-language-search-2026-09-10.json)
conserva las consultas, los conteos y las referencias públicas. Es una transcripción
de las observaciones de esta sesión, no una evaluación nueva ni una captura íntegra
de respuestas. Las peticiones se ejecutaron en dos grupos paralelos; sus tiempos
individuales no se usan como un nuevo p95 ni como comparación de latencia.

Reproducción de la consulta que falla:

```powershell
$queryText = 'quién habló de las pérdidas de las tuberías del agua?'
$queryUrl = 'https://subtitula-web-dev.s-counago00.workers.dev/processing/search?q=' + [uri]::EscapeDataString($queryText) + '&limit=10'
Invoke-RestMethod -Method Get -Uri $queryUrl
```

## Qué demuestra y qué falta

La formulación completa devuelve cero mientras formulaciones relacionadas recuperan
el contenido. No es un error HTTP ni una caída al modo léxico. No se ha aislado todavía
qué contribución exacta tienen la preparación de consulta, el embedding, el umbral
y el orden de resultados; no atribuir el fallo a una sola palabra sin medirlo.

La revisión de `PublicSearchService.prepare()` y `hybrid()` muestra eliminación de
algunas palabras interrogativas y clasificación genérica de documentos mediante RRF.
No hay una intención específica de «quién» que organice los resultados por hablante.
El frontal muestra `speakerLabel` cuando está disponible, pero eso no constituye
una respuesta agrupada a la pregunta.

La identidad es además un requisito de datos: la voz de este audio ficticio sigue
sin identificar. La frase «A Presidencia propón…» describe contenido; **no acredita
por sí sola quién pronunció la frase**. Sólo deben mostrarse nombres o cargos
asociados explícitamente al hablante en la sesión. Se conserva la etiqueta desconocida
cuando esa asociación no existe.

## Próximo desarrollo propuesto

1. Separar la intención «quién / quen» del tema buscado, conservando entidades,
   filtros y la consulta original para su presentación. Evaluar preguntas completas
   y paráfrasis en gallego y castellano.
2. Recuperar y priorizar las intervenciones que realmente tratan el tema. Afinar
   preparación, recuperación y ordenación con evidencia, sin rebajar globalmente
   el umbral como solución no evaluada: la calibración anterior ya encontró falsos
   positivos con 0,32.
3. Presentar hablante, intervención, sesión, tiempo y fuente; agrupar referencias
   duplicadas sin confundir documentos con personas. No fusionar voces desconocidas
   ni personas de distintas sesiones por compartir una etiqueta. Para agrupar entre
   sesiones haría falta una identidad común confirmada.
4. Añadir casos de aceptación antes de cambiar el comportamiento: varios hablantes,
   identidades confirmadas y desconocidas, contenido mencionado por terceros,
   paráfrasis bilingües, temas ausentes y publicaciones retiradas. Mantener el acceso
   directo al fragmento y la alternativa léxica ante fallo semántico.

### Criterios propuestos para la siguiente entrega

- **NLS-01 — pregunta original:** devuelve la evidencia relevante indicada arriba
  entre los cinco primeros resultados, con enlace al fragmento y sin inventar identidad.
- **NLS-02 — reformulaciones:** las cuatro variantes de la tabla mantienen acceso a
  la evidencia; no basta con devolver un tema general de preguntas.
- **NLS-03 — quién intervino:** una sesión de prueba con varios hablantes permite
  distinguir a quienes hablaron del tema de quienes sólo son mencionados en el texto.
  Cada persona presentada conserva una intervención y una fuente verificables.
- **NLS-04 — desconocidos:** una identidad no confirmada sigue sin identificar y no
  se mezcla con otros hablantes por tener la misma etiqueta visible.
- **NLS-05 — sin respuesta y retirada:** no aparecen personas ni evidencias para los
  temas ausentes etiquetados; sólo se recuperan publicaciones activas.
- **NLS-06 — evaluación completa:** repetir las comparaciones principales/adicionales,
  nuevos casos, latencia y coste. Medir agrupación y atribución además de recuperar
  al menos un fragmento; los ensayos institucionales siguen pendientes.

El 16/16 del conjunto principal anterior sigue siendo una medición válida de ese
conjunto pequeño. Esta pregunta nueva demuestra que no permite afirmar fiabilidad
general en lenguaje natural ni cobertura completa de preguntas por hablante.

La activación de las ocho capacidades se mantiene. Este trabajo queda dentro del
endurecimiento del piloto y no modifica los requisitos del lanzamiento de producción.
