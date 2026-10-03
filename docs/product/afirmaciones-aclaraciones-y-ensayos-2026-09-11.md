# Afirmaciones: identidad, contexto, persistencia y ensayos

**Aclaraciones al informe exploratorio · 11 de septiembre de 2026**

Este documento recoge las preguntas del usuario tras revisar el [informe exploratorio](afirmaciones-asuntos-informe-exploratorio-2026-09-11.md). La dirección preferida es un LLM económico con tareas acotadas, fuentes obligatorias y validación. GLM-4.7-Flash queda como referencia inicial por la integración existente; DeepSeek-V4.1-Flash es un candidato adicional para comparar. Se prioriza evitar información errónea sobre completar todos los campos automáticamente.

Las estructuras siguientes ilustran una propuesta, no un esquema de base de datos aprobado. Los cálculos son reproducibles, pero no se han ejecutado ensayos con estos modelos para la nueva funcionalidad ni cambiado la aplicación.

## La afirmación tiene identidad; su redacción no es su identificador

Conviene separar cuatro elementos: persona, asunto, afirmación y aparición. Una afirmación tiene un identificador estable aunque se exprese con palabras diferentes. Cada ocasión en que se pronuncia tiene el suyo y mantiene sus propias evidencias. Para la primera exploración, una afirmación agrupa lo sostenido por una misma persona sobre un hecho concreto y bajo condiciones equivalentes. Otra persona que sostenga lo mismo conserva una atribución separada; se pueden relacionar ambas sin mezclar sus apariciones.

Una ficha ficticia podría ser:

```json
{
  "claimId": "AF-104",
  "personId": "P-7",
  "matterId": "AS-23",
  "statement": "El presupuesto inicial aprobado de la obra fue de 1.600.000 EUR, IVA incluido.",
  "property": "presupuesto_inicial_aprobado",
  "value": { "operator": "equal", "amount": "1600000", "unit": "EUR" },
  "qualifiers": { "vatIncluded": true, "referencePeriod": null },
  "modality": "hecho_afirmado",
  "occurrenceIds": ["AP-1", "AP-2", "AP-3"],
  "reviewState": "propuesta"
}
```

Los identificadores son didácticos; la aplicación asignaría identificadores opacos. `referencePeriod: null` expresa que no conocemos ese dato. `reviewState` tampoco significa que se haya verificado la veracidad del presupuesto: distingue el tratamiento de la ficha.

Cada aparición incluye sesión, fecha, revisión de transcripción, orador de esa sesión, cita literal y referencias a uno o varios segmentos con sus tiempos. Añade qué fragmentos permiten resolver contexto, como «esta obra», y qué vínculo atribuye el orador a la persona. La afirmación conserva evidencia a través de esas apariciones; no se mantiene un texto resumen aislado de ellas.

Para reconocer una nueva paráfrasis seguirán interviniendo textos y embeddings. Lo que se evita es usar la igualdad de cadenas como identidad. El buscador recupera candidatos y el comparador verifica sujeto, asunto, propiedad, valor, periodo, modalidad y condiciones. Si es equivalente, añade una aparición a AF-104. Si cambia el valor o una condición sustancial, conserva otra afirmación y una relación revisable entre ambas. Un hash de la frase no resuelve esa equivalencia semántica.

La redacción puede corregirse sin perder identidad si sigue expresando la misma proposición. Una corrección sustancial requiere una revisión explícita o una afirmación distinta, conservando lo anterior y sus fuentes. Procesar otra publicación del mismo pasaje tampoco debería incrementar el número de veces que la persona lo dijo.

## La prudencia se aplica en cada paso

El criterio del usuario implica poder terminar con datos pendientes. Una identidad dudosa no se enlaza a un perfil público; una referencia ambigua no crea un asunto consolidado; un fragmento insuficiente no se convierte en contradicción. La falta de pruebas no se interpreta como falsedad.

Hay que separar fidelidad de extracción, atribución de persona, pertenencia a un asunto y conclusión comparativa. Que una cita exista no demuestra que se haya interpretado correctamente. Un asunto correctamente identificado tampoco confirma el importe mencionado. La revisión debe mostrar qué parte está confirmada y cuál queda pendiente.

Se pueden guardar propuestas privadas y sus evidencias para investigarlas. La aceptación para el historial público requiere comprobaciones adicionales; las contradicciones señaladas públicamente necesitan revisión del caso. Esto permite ser conservador sin eliminar todo lo que todavía no sabemos resolver. El ensayo medirá también cuántas propuestas quedan pendientes para que la prudencia no oculte un sistema que apenas resuelve casos.

## Bloques pequeños con acceso adicional al contexto

Los 2.000–4.000 tokens son una unidad inicial de lectura, no una frontera de conocimiento. El proceso debería poder solicitar turnos vecinos, un tramo más amplio de la sesión, la agenda y antecedentes autorizados. El acceso necesita herramientas concretas: leer fragmentos por identificador, ampliar un intervalo y buscar en una sesión o en el corpus permitido.

Si encuentra «esa cifra», primero puede leer los turnos anteriores; si encuentra una rectificación, ampliar hasta completar el intercambio. Si cita un pleno anterior, buscar ese antecedente. Cada consulta devuelve texto con identificadores, revisión y origen. La resolución final debe citar los fragmentos recuperados de los que depende, además de la declaración principal.

Tendría un límite de ampliaciones, tokens y tiempo por caso, ajustable en el experimento. Agotar ese límite produce contexto insuficiente, no una conclusión inventada. Todo lo leído se trata como contenido de las fuentes y no como instrucciones para el agente. Los permisos se comprueban al recuperar: un corpus compartido no da acceso a borradores de otras instituciones.

No hace falta que todas las operaciones usen un agente con herramientas. Un extractor sencillo puede pedir `needsContext` y explicar qué referencia no resuelve. Solo esos casos pasan a una lectura con herramientas. El ensayo comparará esa opción con permitir herramientas desde el principio.

## Salida estructurada y una operación de guardado controlada

La idea de terminar en un registro persistente es buena. La propuesta sería que el agente invoque una operación como `registrar_propuesta_afirmacion` con campos definidos, referencias de evidencia y una breve justificación comprobable. El programa valida esos datos y realiza la transacción; el agente no escribe SQL arbitrario ni decide por sí solo publicar el resultado.

Antes de guardar se comprueban permisos, revisión vigente, existencia y pertenencia de fuentes, coincidencia de la cita con el texto, atribución y valores permitidos. Si falla, la respuesta indica el problema para una corrección acotada o una salida pendiente. El registro debe distinguir propuesta, aceptada, rechazada o pendiente de contexto; un resultado vacío también es válido.

La legibilidad para personas procede de presentar la ficha junto a sus citas y diferencias, no simplemente de estar en una tabla. Por eso conservaríamos campos estructurados y una formulación breve. Para una contradicción, se mostrarían las afirmaciones enfrentadas, las condiciones comparadas y las fuentes que justifican la relación.

«Estructurado», «repetible» e «idéntico en cada ejecución» son propiedades distintas. Un esquema fija el formato. Guardar fuente, versión, instrucciones, parámetros, modelo, consultas y salida permite auditar una ejecución. Una clave de operación y restricciones al persistir evitan duplicar el resultado de un reintento. Ninguna de esas medidas garantiza que otra inferencia genere exactamente la misma interpretación.

Se puede congelar la salida válida de un paso y reutilizarla al reanudar. Una nueva inferencia se registra como otra ejecución y no reescribe silenciosamente lo publicado. Temperatura baja o una semilla son ayudas de estabilidad, no una garantía suficiente. La documentación de DeepSeek también distingue el uso de herramientas —que ejecuta la aplicación— del contenido que propone el modelo, y documenta un modo estricto de esquema en beta. [Fuente: Tool Calls](https://api-docs.deepseek.com/guides/tool_calls/).

## Internet y fuentes externas

Acceder a más contexto interno y cotejar información externa son dos capacidades separadas. La primera debe formar parte de la exploración inicial. La segunda queda registrada para futuras iteraciones por petición del usuario, en [cotejo documental y fuentes públicas](afirmaciones-cotejo-documental-futuro.md).

La extracción no necesita internet para determinar qué dice una transcripción. Para contrastar si el hecho es cierto, sí puede ser necesario encontrar documentación externa. En esa futura etapa, propondría herramientas de búsqueda y lectura que registren la fuente consultada, su versión y el pasaje utilizado. Un resultado de buscador o el conocimiento previo del LLM no serían por sí solos evidencia incorporada.

Separar las tareas también ayuda a diagnosticar errores: si el sistema altera una declaración porque conoce una versión diferente en internet, deja de registrar fielmente qué dijo esa persona. La extracción conserva la declaración; la comprobación añade una relación con otras fuentes.

## GLM y DeepSeek-V4.1-Flash

El anuncio enlazado por el usuario presenta V4.1-Flash el 10 de septiembre de 2026. La documentación actual de la API directa identifica `deepseek-flash` con esa versión. Esto no debe confundirse con el identificador `deepseek-v4-flash-0731` del catálogo consultado de Workers AI ni trasladar automáticamente tarifas entre proveedores. [Anuncio de DeepSeek](https://www.deepseek.com/en/news/deepseek-v4-1-flash/).

Precios de API directa consultados el 11 de septiembre, en USD por millón de tokens:

| Uso en DeepSeek-V4.1-Flash | Horario valle | Horario punta |
|---|---:|---:|
| Entrada que encuentra caché | 0,003 | 0,006 |
| Entrada que no encuentra caché | 0,150 | 0,300 |
| Salida | 0,600 | 1,200 |

El horario punta publicado es de lunes a viernes, 01:00–04:00 y 06:00–10:00 UTC; el resto es valle. Son tarifas del proveedor directo. [Fuente: precios actuales de DeepSeek](https://api-docs.deepseek.com/quick_start/pricing/).

La caché reutiliza prefijos idénticos persistidos. Las instrucciones estables o lecturas repetidas de un mismo documento pueden beneficiarse; un bloque nuevo y diferente no hereda ese descuento completo. El proveedor no garantiza todos los aciertos y la salida se vuelve a generar. Conviene medir `prompt_cache_hit_tokens` y `prompt_cache_miss_tokens`, en vez de presuponer la tasa de acierto. [Fuente: Context Caching](https://api-docs.deepseek.com/guides/kv_cache/).

Aplicando las tarifas al supuesto previo de 250.000 tokens de entrada y 40.000 de salida, sin incluir embeddings ni otros costes:

| Escenario hipotético | Generación |
|---|---:|
| GLM-4.7-Flash con la tarifa del informe | 0,031000 USD |
| DeepSeek en valle, sin aciertos de caché | 0,061500 USD |
| DeepSeek en valle, 50 % de entrada con caché | 0,043125 USD |
| DeepSeek en valle, 90 % de entrada con caché | 0,028425 USD |

Son cálculos a igualdad ficticia de tokens, no mediciones de calidad ni consumo. En este escenario concreto DeepSeek en valle igualaría el coste de GLM alrededor del 83 % de entrada con caché. El punto de equilibrio cambiaría con otra proporción de entrada y salida. Las primeras lecturas, el razonamiento facturable, los reintentos y la tokenización real deben contarse.

Mi propuesta sigue siendo GLM como referencia integrada y DeepSeek como comparación si se autoriza ese ensayo. El ganador sería el que produzca menos errores y menos revisión a un coste aceptable; un modelo algo más caro puede compensar si mejora esas medidas. El descuento por sí solo no justifica enviar todo el historial a cada llamada ni ejecutar procesos permanentes.

## Tres horas, palabras y tokens

45.000 tokens fue un supuesto para explicar costes, no una medición de una sesión parlamentaria. 60.000 palabras en tres horas equivalen a unas 333 palabras por minuto durante los 180 minutos. Las pausas aumentarían la velocidad requerida durante el habla efectiva. Conviene comprobar que la cifra no sea de caracteres, tokens o una transcripción de más horas.

Estos escenarios solo muestran la aritmética; las velocidades y la conversión de 1,5 tokens por palabra son hipótesis ilustrativas, no promedios demostrados para el Parlamento ni para gallego:

| Supuesto de palabras por minuto durante 180 minutos | Palabras | Tokens si se supusieran 1,5 por palabra |
|---|---:|---:|
| 120 | 21.600 | 32.400 |
| 150 | 27.000 | 40.500 |
| 180 | 32.400 | 48.600 |
| 333,33 | 60.000 | 90.000 |

Un token puede ser una palabra, parte de ella, un signo o una combinación; no existe una equivalencia universal. En textos normales es habitual que haya más tokens que palabras, pero la proporción varía según idioma, texto y modelo. Para 60.000 palabras no presupuestaría 45.000 tokens sin medirlo. La documentación de DeepSeek recomienda atender al consumo devuelto por el modelo y ofrece un contador local; no proporciona una conversión validada para nuestro gallego. [Fuente: Token & Token Usage](https://api-docs.deepseek.com/quick_start/token_usage/).

El primer dato del experimento debe ser el recuento del texto real con el tokenizer correspondiente a la versión ensayada. Además se mide el total enviado en todas las llamadas, incluyendo instrucciones, solapes, herramientas y candidatos. «Tokens de la sesión» y «tokens facturados por analizarla» son magnitudes diferentes. Las muestras sintéticas actuales del repositorio no representan tres horas de debate real.

## Ensayos exploratorios que se convierten en comparaciones repetibles

Empezaría con exploraciones pequeñas y casos realistas, pero guardando desde el primer día las entradas, expectativas y resultados. Un cuaderno de pruebas aislado de la aplicación y del índice público es suficiente para investigar. No hace falta construir primero perfiles públicos ni una nueva base en producción.

Una muestra inicial posible sería de dos instituciones y varias sesiones públicas autorizadas, en ambos idiomas, con asuntos repetidos entre fechas. Se elegirían deliberadamente cifras, referencias indirectas y homónimos. Los casos sintéticos complementan esa muestra con negaciones, contradicciones y duplicados controlados; deben aparecer identificados como sintéticos y nunca atribuir declaraciones inventadas a personas reales.

**Primero, lectura y expectativas.** Seleccionar 20–30 fragmentos, anotar las afirmaciones y las fuentes, y acordar qué asuntos son iguales. Los casos ambiguos llevan una respuesta válida de insuficiencia. Contar palabras y tokens del material. Esto descubre desacuerdos del propio criterio antes de atribuirlos al modelo.

**Después, un ensayo guiado de GLM.** Ejecutar extracción con formato fijo y revisar cada salida. Registrar errores y consultas de contexto. Ajustar las instrucciones con esos ejemplos. Si se compara DeepSeek, se usa la misma información disponible, las mismas herramientas y los mismos criterios, anotando las diferencias de configuración y consumo.

**A continuación, congelar una prueba separada.** Reservar fragmentos y sesiones que no se utilizaron para ajustar el comportamiento. Comparar bloque aislado frente a ampliación de contexto, recuperación por texto frente a híbrida y una frente a varias lecturas cuando aporten valor. Medir cada cambio por separado permite saber qué produjo una mejora.

**Finalmente, recorrer sesiones completas.** Medir cobertura, coste, tiempo y revisión con todo el flujo. Procesar en orden cronológico para que las sesiones futuras no ayuden retrospectivamente. Una vez que el catálogo contiene un asunto, puede reutilizarlo en las sesiones siguientes; reservar también asuntos nuevos comprueba que el sistema sabe abstenerse o proponerlos.

El resultado de cada caso debe permitir ver entrada, contexto consultado, salida estructurada, decisión de guardado, evidencia, revisión y consumo. Las métricas prioritarias serían atribuciones falsas, uniones de asuntos incorrectas, afirmaciones sin sustento y falsas contradicciones; después, lo que se omite o queda pendiente. También se mide fidelidad de números, negaciones y condiciones, repetibilidad del registro y esfuerzo humano.

No elegiría únicamente un porcentaje de acierto total: si un conjunto contiene pocas contradicciones, un sistema que nunca detecte ninguna puede parecer preciso. Haría falta medir tanto la proporción de detecciones correctas como la recuperación de los casos esperados, separadas por idioma y dificultad. Un conjunto pequeño sin errores es una señal para seguir investigando, no una garantía suficiente para publicar acusaciones.

La siguiente acción concreta, cuando se decida iniciar los ensayos, sería escoger el pequeño corpus y preparar ese cuaderno con sus expectativas. El presente cambio deja explicado el método; no pone en marcha llamadas de pago ni procesos continuos.
