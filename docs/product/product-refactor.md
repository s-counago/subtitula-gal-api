Exit code: 0
Wall time: 0.8 seconds
Output:
# Refactor de interfaz: un producto, tres intenciones

## Resultado deseado

Una persona autenticada elige por qué viene a Subtitula una vez. A partir de ahí trabaja siempre en el mismo espacio de proyecto: sube el medio, obtiene una transcripción, la revisa en un editor y publica o exporta. El modo cambia las ayudas y los controles, no duplica el flujo ni obliga a abandonar el editor para leer la transcripción.

| Modo | Promesa | Usuario principal | Salida dominante |
|---|---|---|---|
| Creador | De tu vídeo a subtítulos listos para publicar. | Creador/a gallego/a | Vídeo con subtítulos, SRT/VTT y clip |
| Institución | De una sesión a un acta audiovisual revisable. | Comunicación, Secretaría, personal técnico | Sesión accesible, transcript verificable y documentos |
| Transparencia | Encuentra qué se dijo y compruébalo. | Ciudadanía, prensa, oposición, asociaciones | Resultado citable por minuto, vídeo y fuentes |

El tercer modo tiene dos caras: la consulta pública es abierta y no requiere cuenta; un usuario institucional autenticado ve además Gestionar portal. No hay que obligar a un ciudadano a pasar por el selector para buscar un pleno.

## Arquitectura de navegación

~~~mermaid
flowchart LR
  A["/app — elegir intención"] --> B["Creador"]
  A --> C["Institución"]
  A --> D["Transparencia: gestionar"]
  B --> E["/proyectos/nuevo?modo=creador"]
  C --> F["/proyectos/nuevo?modo=institucion"]
  E --> G["/proyectos/:id/editor"]
  F --> G
  G --> H["Exportar / publicar"]
  H --> I["/transparencia/:entidad/sesiones/:slug"]
  J["/transparencia — consulta pública"] --> I
~~~

Rutas propuestas:

- /app: selector tras iniciar sesión. También muestra proyectos recientes y un enlace visible a la consulta pública.
- /proyectos/nuevo?modo=…: un único asistente de subida; precarga ajustes razonables según el modo.
- /proyectos/:id/editor: editor común con paneles condicionales.
- /biblioteca: todos los proyectos propios, con filtros por estado, modo y entidad.
- /transparencia: buscador público de sesiones publicadas.
- /transparencia/:entidad/sesiones/:slug: página pública de una sesión, estable y enlazable.
- /institucion/portal: administración de la entidad; solo roles autorizados.

No crear destinos de trabajo competidores para subir, transcripciones y editor. La biblioteca puede abrir cualquier proyecto directamente en la pestaña que corresponda.

## Pantalla inicial autenticada

Encabezado: saludo breve, botón secundario Ver mi biblioteca y enlace Explorar transparencia.

Tres tarjetas de acción, no botones desnudos:

1. **Crear subtítulos**
   - Texto: Sube un vídeo y déjalo listo para redes.
   - Señales: estilo, formato vertical/horizontal, traducción y exportación.
   - CTA: Crear una pieza.

2. **Publicar una sesión**
   - Texto: Convierte un pleno o acto en una sesión accesible y verificable.
   - Señales: hablantes, puntos del día, revisión de dudas y documentos.
   - CTA: Nueva sesión.

3. **Gestionar transparencia**
   - Texto: Publica, ordena y haz encontrable el archivo institucional.
   - Señales: sesiones publicadas, borradores y búsquedas ciudadanas.
   - CTA: Abrir portal.
   - Si no existe una entidad vinculada, el CTA abre una solicitud de acceso, no un falso flujo funcional.

Debajo, Continuar donde lo dejaste: máximo cuatro proyectos, estado y acción exacta. Esto resuelve el retorno sin añadir una cuarta navegación.

## Asistente de subida único

### Paso 1 — Medio

Arrastrar/seleccionar, URL de vídeo si se soporta y control de idioma de audio. Mostrar duración, peso, formato admitido y estimación de créditos antes de procesar.

### Paso 2 — Contexto

Campos compartidos: título interno, idioma, diccionario/glosario y permiso de uso.

Campos que aparecen en institución:

- entidad y órgano;
- fecha, lugar y tipo de sesión;
- URL de vídeo original;
- orden del día: pegar, importar o crear después;
- adjuntos oficiales; y
- política de publicación y retención.

Campos que aparecen en creador:

- destino: TikTok, Reels, YouTube o archivo;
- relación de aspecto;
- estilo inicial; y
- idioma(s) de subtítulo.

### Paso 3 — Confirmar

Una sola pantalla de revisión con coste/consumo estimado, aviso de que el texto es automático y CTA Transcribir y abrir editor. Tras confirmación, la persona aterriza en el proyecto, con el estado de proceso visible; nunca en una página muerta de transcripciones.

## Editor común

~~~text
┌─────────────────────────────────────────────────────────────────┐
│ Nombre · estado · Deshacer/Rehacer · Guardado · Publicar/Exportar│
├───────────────┬──────────────────────────────┬──────────────────┤
│ pestañas      │ vídeo + controles + timeline  │ panel contextual │
│ Transcripción │                              │ segmento activo  │
│ Subtítulos    │ selección sincronizada       │                  │
│ Publicación   │                              │                  │
│ [Institución] │                              │ hablante/conf.   │
│ Sesión        │                              │ estilo/agenda    │
│ Documentos    │                              │                  │
└───────────────┴──────────────────────────────┴──────────────────┘
~~~

Reglas de interacción:

- Clicar una frase mueve el vídeo al inicio del segmento; clicar el vídeo selecciona el segmento.
- El texto se edita en línea, con guardado optimista e historial de revisiones.
- Buscar dentro del proyecto no abre otra página. Las coincidencias se navegan con Enter y Shift+Enter.
- El botón principal cambia por estado: Procesando, Revisar 18 dudas, Preparar publicación, Publicar.
- La transcripción completa se lee y se corrige aquí. No hay una vista separada que compita con el editor.

### Panel específico de creador

- estilos guardados y previsualización sobre el vídeo;
- segmentación para lectura, máximo de caracteres y saltos de línea;
- traducción y ajuste de timings;
- recorte/clip opcional;
- exportar SRT, VTT, texto, vídeo con subtítulos integrados y, después, presets sociales.

### Panel específico de institución

- asignar, fusionar y renombrar hablantes; conservar Hablante desconocido en vez de inventar nombres;
- cola de revisión por confianza: filtro crítica, media, resuelta; cada alerta explica su señal: audio pobre, solapamiento, nombre propio o desacuerdo de modelo;
- dividir y asociar segmentos a puntos del orden del día;
- adjuntar acta, convocatoria, orden del día y acuerdos; se enlazan, no se reemplazan;
- estado de publicación con checklist: título/fecha, vídeo original, revisión, accesibilidad, adjuntos y responsable;
- generar enlace público, descarga de transcripción y exportación de subtítulos.

No presentar una puntuación de precisión inventada. Decir señales de revisión y explicar el motivo mantiene la confianza del personal y evita confundir confianza del modelo con verdad.

## Página pública de transparencia

Una página de sesión debe poder responder una pregunta en menos de un minuto:

1. título, órgano, fecha, duración, estado de publicación y enlace al origen;
2. buscador dentro de la sesión y URL con timestamp;
3. vídeo, transcripción sincronizada y hablantes claramente identificados o marcados como no identificados;
4. índice por orden del día; y
5. documentos asociados con procedencia, fecha y enlace oficial.

La búsqueda global empieza con texto completo, filtros por entidad/órgano/fecha/persona/punto del día y resultados que muestran el fragmento, minuto y sesión. La IA no debe inventar resúmenes de pleno como interfaz principal.

## Búsqueda: orden técnico recomendado

**MVP — búsqueda léxica con evidencia.** Indexar transcripciones revisadas y publicadas en PostgreSQL Full Text Search para gallego y castellano, complementado con coincidencia tolerante a errores para nombres con pg_trgm. Guardar segmento, inicio, fin, hablante, sesión, orden del día y revisión.

**Después — búsqueda híbrida.** Añadir embeddings por fragmento y combinar recuperación semántica con el resultado léxico. Toda respuesta generativa debe devolver los segmentos citados, minuto, vídeo y documento; si no hay evidencia, debe decir que no la encontró. Medir si mejora éxito de búsqueda antes de asumir coste permanente.

**Más tarde — preguntas asistidas.** Solo sobre contenido público indexado, sin inferir intenciones, sin rankings políticos opacos y con controles de coste, límites por consulta y aviso claro de que es una ayuda de navegación.

## Modelo de datos mínimo

Proyecto es el centro, no un archivo de subtítulos.

| Entidad | Campos imprescindibles |
|---|---|
| organization | nombre, dominio/slug, branding, políticas, plan |
| project | modo, organización opcional, título, estado, medio, idioma, propietario |
| recording | URL original, copia de trabajo, duración, checksum, permisos |
| transcript_revision | proyecto, versión, fuente/modelo, idioma, estado de revisión, creador |
| segment | revisión, inicio/fin, texto, hablante, señales de revisión, orden del día |
| speaker | proyecto, etiqueta, nombre confirmado, cargo opcional, origen |
| caption_track | proyecto, idioma, estilo/preset, estado, archivo exportado |
| agenda_item | proyecto, orden, título, inicio/fin opcionales, fuente |
| document | proyecto, tipo, URL oficial, archivo, fecha, hash, visibilidad |
| publication | proyecto, slug público, fecha, responsable, estado, versión publicada |
| search_document | segmento publicado, metadatos filtrables, texto y vector futuro |

Estados mínimos de proyecto: borrador → subido → procesando → requiere revisión → listo → publicado → archivado. Una publicación referencia una revisión concreta, de modo que una corrección posterior no altere silenciosamente lo ya publicado.

## Implementación por cortes

1. **Corte A: navegación y proyecto único.** Selector, asistente común, biblioteca y entrada única al editor; conservar el flujo actual detrás de adaptadores.
2. **Corte B: revisión institucional.** Hablantes, señales de revisión, agenda y adjuntos dentro del mismo editor.
3. **Corte C: publicación pública.** Ficha pública de sesión con vídeo/transcripción sincronizada y enlaces a documentos.
4. **Corte D: búsqueda verificable.** Índice léxico, filtros y analítica de búsquedas sin resultado.
5. **Corte E: búsqueda híbrida.** Solo con corpus suficiente y métricas comparativas favorables.

Cada corte debe poder desplegarse sin migrar forzosamente los proyectos previos; un proyecto antiguo puede abrirse en el editor común con modo creador hasta que su dueño lo cambie.


