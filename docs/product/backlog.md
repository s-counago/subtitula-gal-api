Exit code: 0
Wall time: 0.8 seconds
Output:
# Backlog priorizado

Convención: **P0** desbloquea el primer piloto; **P1** aumenta el valor del piloto; **P2** es investigación o una apuesta que todavía no debe construir producto. El estado inicial es Pendiente.

## Ahora — refactor y primer piloto

| ID | Pri. | Estado | Iniciativa | Resultado verificable |
|---|---:|---|---|---|
| UX-01 | P0 | Hecho | Selector de intención tras login | Tres tarjetas, recientes y acceso a biblioteca; no se pierde el acceso a un proyecto anterior. |
| UX-02 | P0 | Hecho | Asistente único de subida | Creador e institución generan el mismo proyecto; sus metadatos contextuales quedan guardados. |
| UX-03 | P0 | Hecho | Editor como superficie única | Vídeo, transcript y segmentos se sincronizan; editar una frase no obliga a salir del flujo. |
| UX-04 | P0 | Hecho | Modo institución | Se pueden corregir hablantes, ver/filtrar segmentos con señales y adjuntar orden del día/documentos. |
| PUB-01 | P0 | Pendiente | Ficha pública de una sesión | URL estable con vídeo original, transcript con timestamp, índice y documentos; funciona sin login. |
| SRCH-01 | P0 | Pendiente | Búsqueda dentro de sesión | Consulta literal, resalta resultados y enlaza al segundo exacto. |
| DATA-01 | P0 | Pendiente | Versionado de transcript/publicación | Toda publicación referencia una revisión; el original y sus cambios son auditables. |
| COST-01 | P0 | Pendiente | Medición por archivo | Registrar minutos, coste por fase/modelo, duración de proceso, segmentos marcados, edición humana y calidad de muestra. |
| VAL-01 | P0 | Pendiente | Descubrimiento con 5–8 usuarios | Entrevistas separadas: 2 creadores, 2 comunicación, 2 secretaría y 2 usuarios de información pública. Grabar tareas, no solo opiniones. |
| PIL-01 | P0 | Pendiente | Elegir piloto seguro | Una entidad publica vídeo ya público y no integra expedientes internos. Definir responsable, periodo, propiedad de datos y criterios de salida. |
| INF-01 | P0 | Pendiente | Google OAuth separado | Dos clientes OAuth web —dev/prod— con callback exacto; no confundir con API key. |
| INF-02 | P0 | Hecho | Base de datos de desarrollo aislada | PlanetScale dev aislado; Flyway/JDBC/TLS validados con datos sintéticos y sin credenciales production. |
| INF-03 | P0 | En código | Migración y validación Scribe v2 | El código usa `scribe_v2`; falta clave dev limitada, corpus gallego y medición de coste/calidad. |

## Siguiente — hacer que sea valioso para institución y ciudadanía

| ID | Pri. | Estado | Iniciativa | Resultado verificable |
|---|---:|---|---|---|
| UX-05 | P1 | Pendiente | Checklist de publicación | Impide publicar sin fecha/título/origen y hace visibles las excepciones justificadas. |
| UX-06 | P1 | Pendiente | Asociación asistida al orden del día | Personal puede mapear tramos al punto correspondiente y corregir el resultado sin fricción. |
| PUB-02 | P1 | Pendiente | Portal por entidad | Archivo de sesiones, filtros y página de cómo usar/solicitar información, embebible o enlazable desde la sede. |
| SRCH-02 | P1 | Pendiente | Búsqueda global léxica | Filtros por entidad, órgano, fecha, hablante y punto del día; fragmento + timestamp en cada resultado. |
| SRCH-03 | P1 | Pendiente | Analítica de necesidades ciudadanas | Búsquedas sin resultado, CTR al minuto, consultas repetidas y clics a documentos; sin perfilar ideología. |
| COST-02 | P1 | Pendiente | Cascada de transcripción | Modelo económico como primera pasada; el coste alto solo se emplea en segmentos que lo justifiquen y queda registrado. |
| COST-03 | P1 | Pendiente | Cascada de traducción | Traducir únicamente tracks publicados o solicitados; detectar segmentos con glosario/nombres y revisar los discrepantes. |
| GOV-01 | P1 | Pendiente | Paquete de compra/piloto | DPA, ubicación de datos, subencargados, seguridad, soporte, salida/exportación y explicación de IA. |
| GOV-02 | P1 | Pendiente | Brecha ENS/RGPD/accesibilidad | Inventario de tratamientos, análisis de riesgo y plan de medidas antes de procesar contenido no público. |
| MARKET-01 | P1 | Pendiente | Mapa de implantaciones | Para 15 concellos: proveedor de sede/transparencia, vídeo de plenos, volumen, URL, buscabilidad y contacto público. |
| INF-04 | P1 | Hecho | CI, ramas y gate de despliegue | Push a `develop` verifica y despliega frontend/API; producción permanece bloqueada en `master`/`main`. |
| INF-05 | P1 | En curso | Provisionar PlanetScale PostgreSQL dev desde Cloudflare | Base/rol dev y Worker Secrets activos; Flyway y JDBC/TLS validados. Falta ensayar restauración y registrar métricas sostenidas. Prod queda en el anexo. |
| INF-06 | P1 | Hecho | Hosted dev en Cloudflare | Next/OpenNext y Spring Container están en `workers.dev`; password auth, sesión, CSRF y gateway E2E validados con email explícitamente deshabilitado. |

## Después — solo tras comprobar necesidad

| ID | Pri. | Estado | Iniciativa | Decisión que debe informar |
|---|---:|---|---|---|
| SRCH-04 | P2 | Pendiente | Recuperación híbrida/vectorial | Comparar éxito de búsqueda, latencia y coste contra SRCH-02 en consultas reales anonimizadas. |
| SRCH-05 | P2 | Pendiente | Preguntas en lenguaje natural con citas | Lanzar únicamente si cada respuesta expone evidencia suficiente y supera una evaluación humana. |
| INT-01 | P2 | Pendiente | Embed/API/sede electrónica | Priorizar tras saber qué usan los pilotos y si el enlace simple ya resuelve el caso. |
| PUB-03 | P2 | Pendiente | Archivo histórico | Paquete cerrado para digitalizar plenos antiguos; estimar calidad y coste por hora antes de venderlo. |
| CRE-01 | P2 | Pendiente | Presets sociales y publicación directa | Construir cuando creadores confirmen que reduce su paso manual más doloroso. |
| BIZ-01 | P2 | Pendiente | Oferta provincial | Validar con una diputación tras demostrar operación repetible en varios concellos. |
| INF-07 | P2 | Pendiente | Anexo dominio, correo y producción | Comprar/incorporar dominio, Cloudflare Email Service, Google público y PlanetScale prod HA solo cuando exista fecha de lanzamiento. |

## Diseño de la cascada de coste

No escalar todo a un modelo caro. El objetivo es comprar calidad donde importa y saber cuánto cuesta.

1. Normalizar audio y detectar silencios/solapamientos; dividir con solape pequeño.
2. Transcribir todos los fragmentos con el modelo económico adecuado para gallego.
3. Marcar para segunda pasada solo los que presenten señales: baja confianza del ASR, ratio de compresión anómalo, ruido/silencio, solapamiento, entidad del glosario, nombre propio o palabra clave institucional.
4. En los marcados, comparar con un segundo modelo o una decodificación más cara; medir desacuerdo por palabra/entidad, no una falsa certeza.
5. Enviar al humano únicamente los discrepantes de riesgo alto o los que se vayan a publicar como fuente pública.
6. Aprender del feedback: diccionarios por entidad, nombres de cargos, topónimos y errores recurrentes.

Métricas de salida: coste por minuto publicado, porcentaje de minutos escalados, tiempo hasta borrador, tiempo humano por hora, tasa de cambios en muestras auditadas, errores en nombres propios y porcentaje de publicación sin incidencias.

## Preguntas de validación

- ¿Qué compra realmente la entidad: cumplimiento, ahorro de Secretaría/Comunicación, mejor archivo, reputación pública o todo junto?
- ¿Un enlace o iframe desde la sede existente evita una integración cara para el primer contrato?
- ¿Qué minuto de revisión ahorra el marcador de dudas frente a un editor genérico?
- ¿Una persona encuentra una decisión antes de pedir información? Medirlo con tareas reales.
- ¿Qué presupuestos y vías de compra usan las entidades objetivo? No diseñar precio para encajar artificialmente en un umbral contractual.
