# Línea base de los ensayos de afirmaciones y Jev

**Fecha de captura:** 22 de septiembre de 2026. Resumen de evidencia para la [propuesta de integración](claims-evidence-integration-plan.md), no una evaluación nueva de modelos.

Los laboratorios viven en el workspace, fuera de ambos repositorios: `labs/claims-pipeline` y `labs/jev-evidence`. Las rutas de esta página son identificadores relativos al workspace, no enlaces disponibles en este checkout. Esta PR conserva un resumen portable y hashes, pero no incorpora todos los audios, documentos ni respuestas. La primera PR de implementación debe portar contratos y fixtures sanitizadas con sus procedencias; estos hashes por sí solos no reproducen el ensayo.

## Extracción: evidencia real y límites

Fuente: sesión pública de Vigo del 23 de diciembre de 2025, 26 min 55 s, 4.356 palabras y 67 segmentos. Se reutilizó la misma transcripción. La referencia previa contiene 25 casos positivos seleccionados y seis controles; fue elaborada/revisada por asistentes, sin validación humana independiente. Permaneció congelada y fuera de las peticiones al extractor. No anota exhaustivamente todas las afirmaciones ni evalúa su verdad externa.

| Ejecución completada | Fieles / 25 | Parciales | Incorrectos dirigidos a la referencia | Omitidos | Registros guardados |
|---|---:|---:|---:|---:|---:|
| Luna low | 9 | 5 | 0 | 11 | 28 |
| Luna xhigh | 12 | 7 | 0 | 6 | 89 |
| Sol medium | 16 | 6 | 0 | 3 | 69 |

Estos ceros no prueban que no haya errores entre todos los registros guardados. Una ejecución por configuración no mide estabilidad ni permite generalizar a otros plenos. El usuario eligió Luna xhigh el 12 de septiembre por su equilibrio percibido entre coste y calidad; esta propuesta mantiene esa preferencia, sin presentarla como una decisión de producción ya validada.

El pase Luna xhigh completado, `vigo-2025-12-23-luna-xhigh-v1b`, guardó 89 de 100 candidatos, rechazó 11 y no detectó duplicados exactos. Registró seis operaciones, 137.713 tokens de entrada más salida y 1.725,615 segundos. Los tokens de razonamiento están incluidos en la salida. Un intento anterior se interrumpió a los 360 segundos sin contador final: **el consumo de todos los intentos es desconocido**. El pase completado permitió 900 segundos por operación. Los precios orientativos del informe no son una factura ni una medición del futuro adaptador hospedado.

El transporte fue `codex_exec` con autenticación `chatgpt_subscription`. No demuestra que el backend pueda usar ese acceso ni que las mismas condiciones de latencia/precio se mantengan con una API de producción.

El 22 de septiembre el usuario confirmó que la integración usará **la API de OpenAI para Luna**, con credenciales de la aplicación, sin su cuenta de ChatGPT. Esa decisión no cambia la procedencia de los resultados históricos; requiere evaluar de nuevo el adaptador API.

Hallazgos que deben convertirse en regresiones de integración:

- Confusión entre fecha de la sesión, año del presupuesto y periodo del hecho.
- Modalidad de discurso referido incorrecta y estructura propiedad/valor incoherente en algunos candidatos.
- Pérdida de afirmaciones por referencias a asuntos no declarados: la resolución de asuntos no debe bloquear la extracción.
- Citas no literales y generación desde segmentos que solo eran contexto vecino.
- Diarización dudosa en S0027–S0028: no atribuir el contenido a un grupo por una etiqueta automática.
- La propuesta de casi diez millones adicionales para vivienda (G11) quedó omitida: conservar búsqueda directa sobre pasajes.
- Una coincidencia literal no valida por sí sola la interpretación, y deduplicar texto exacto no resuelve identidad semántica.

## Jev: preparación sin inferencia real

El ensayo reutiliza cuatro extracciones Luna xhigh y siete fuentes públicas con snapshots y hashes. La recuperación y selección de pasajes fueron manuales. Las fixtures v2 contienen **24 casos semánticos: cuatro extracciones originales y veinte variantes/controles**, más diez escenarios técnicos simulados. No son 24 declaraciones reales independientes.

La referencia es del asistente y no viaja al proveedor. R03, sobre equivalencia entre un programa y becas de inglés, se conserva como exploratorio y queda fuera de scoring. Solo tres de las cuatro extracciones reales tienen referencia puntuable. Los casos sintéticos están identificados y no se atribuyen como declaraciones reales.

El contrato preparado usa `jev-1.13.0`, API directa de TypeSafe, límite local de 24.000 bytes por petición, espera de 45 segundos y cero reintentos automáticos. Son parámetros del laboratorio, no garantías de capacidad o latencia del proveedor. La preparación incluye `supported`, `contradicted`, `insufficient`, `conflicting` y `not_verifiable`, todos relativos al paquete documental.

Sin la clave no se ha enviado ninguna inferencia real a Jev. No hay precisión, latencia ni coste real de Jev medidos. Los tests locales usan respuestas simuladas y directorios temporales. Se volvieron a ejecutar durante esta propuesta:

```powershell
python -m unittest discover -s scripts -p 'test_*.py' -v
```

**Resultado: 16 tests, todos correctos, sin llamadas a proveedores.** Cubren separación de referencia, integridad de entradas/recibos, replay, reanudación, abstención local, respuesta incorrecta con confianza alta y los diez escenarios técnicos: 401, 429, 529, timeout, JSON ilegible, respuesta incompleta, etiqueta inválida, probabilidades inválidas, modelo distinto y recibo interrumpido.

La confianza 0,85 es una hipótesis sin calibrar. El preflight puede evitar tres de las 24 llamadas, pero se apoya en procedencia seleccionada manualmente; no demuestra un clasificador automático fiable de fuentes. El runner reutiliza éxitos dentro de cada run y bloquea reenvíos inciertos, pero no tiene un bloqueo multiproceso apto para producción.

Cuando exista acceso y presupuesto, el protocolo comienza por seis casos smoke y después los 24 completos; sus caches están separadas y juntos pueden consumir hasta 30 llamadas. Una prueba adicional de estabilidad son nueve llamadas. Este documento no ejecuta ni autoriza ese consumo.

El ejemplo Sogama demuestra la necesidad de preservar alcance: una tarifa reducida de 95 euros más 10 % de IVA da 104,50 euros bajo sus condiciones. Esa cuenta no establece por sí sola la tarifa previa de 86 euros ni su aplicación a Vigo. Las guardas actuales pueden abstenerse ante composición de varias fuentes; esa cobertura sigue siendo trabajo futuro.

## Registro de procedencia

Hashes SHA-256 de archivos locales leídos durante la propuesta. Verificar antes de reutilizar el material; un cambio de archivo crea otra línea base.

| Archivo relativo al workspace | SHA-256 |
|---|---|
| `labs/claims-pipeline/reports/resultados-luna-xhigh.md` | `2763c6747ae1a25879e242bc1f1017f3433e430f244d6248a6a47c923994dda9` |
| `labs/claims-pipeline/reports/resultados-sol.md` | `97aab3cd517fc70f19b8eeb602d613481cd3dd39cf24650d11b6dba1d1a8ab19` |
| `labs/claims-pipeline/runs/vigo-2025-12-23-luna-xhigh-v1b/manifest.json` | `91f032dd87d317257ddc79f9dcfee89ae80b8c9f233ae0ab06c6fa01b77435fe` |
| `labs/claims-pipeline/inputs/sources/manifest.json` | `d1b2af0a6ba1c06bfd32f96bd2d7a03f5561a7a00424e1d6c39d685a9a589bd8` |
| `labs/jev-evidence/fixtures/v2/manifest.json` | `29687e6b346f1906ec377ad18700dfc211304238031a5ede787c937e830b24ef` |
| `labs/jev-evidence/fixtures/v2/reference.json` | `b597afc3678cc607328aa4663bf60a125daec1bc5533127be40167d11c453380` |
| `labs/jev-evidence/fixtures/v2/transport.json` | `3db8da65a2157d07b93ef9b08e01193f6995e1687fc7e11e15b016841915d5b7` |
| `labs/jev-evidence/reports/fixtures-v2-preparacion.md` | `7e30b7288dc9b03b9cbd37cdd981706b1ea2de45ba78a5cc02076a7f649ca655` |

El manifiesto Luna fija además la huella de referencia `cb350871140fed8212d67bd94b7cc36798b556a97c7845951d567f23fd55af62` y la de segmentos `f9586c987436a721d0ae7de165f6a214a885925bb5ca67bea5709ddfb249dc5b`. El manifiesto Jev fija peticiones, política, referencia y escenarios; no sustituye recibos reales de proveedor.

## Qué puede trasladarse ahora

Reutilizar conceptos de contrato, conservación de respuestas, validación de citas, fixtures y separación entre referencia y entrada del modelo. Adaptar almacenamiento a PostgreSQL/R2, permisos al dominio actual y ejecución a Workflows. Incorporar exclusión concurrente, versionado de documentos, recuperación automática y estados de consumo incierto: todavía no están resueltos de extremo a extremo por estos laboratorios.

No convertir los scripts locales en un servicio de producción mediante una llamada de shell desde Spring. La primera integración utiliza replay y datos públicos o sanitizados, conserva las limitaciones y no necesita credenciales Jev.
