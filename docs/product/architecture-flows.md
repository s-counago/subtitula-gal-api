# subtitula.gal — Arquitectura y flujos

> Actualizado el **10 de septiembre de 2026**. Las ocho capacidades están activas
> y verificadas en desarrollo con sesiones ficticias en gallego y castellano.
> Producción sigue bloqueada; los criterios del piloto institucional siguen pendientes.
> Ver las [comprobaciones del despliegue](../operations/capability-rollout-2026-09-10.md)
> y la [Sala de control interactiva](subtitula-sala-de-control.html#caps).

Diagramas de alto nivel de los dos repos (`subtitula-gal` = frontal, `subtitula-gal-api` = back)
y de los servicios externos que ataca cada paso. Los diagramas están en **Mermaid**: un mapa de
contenedores para el funcionamiento general y diagramas de secuencia por flujo, que es donde se ve
qué servicio externo interviene en cada función.

El idioma de transcripción es **gallego por defecto** (`glg`); **castellano** (`spa`)
es la única alternativa. La configuración local se consulta por separado: este estado
describe el desarrollo alojado y no es un monitor en tiempo real.

## Las ocho capacidades

**Limitación conocida:** la pregunta completa «quién habló de las pérdidas de las
tuberías del agua?» devuelve cero resultados; «pérdidas en las tuberías de agua»
recupera siete documentos relevantes o relacionados. La intención «quién» y la
agrupación por hablante están pendientes. Ver el [diagnóstico y siguiente desarrollo](natural-language-speaker-search-2026-09-10.md).

| Capacidad | Qué permite | Estado en desarrollo |
|---|---|---|
| 1. Subida duradera (`durableInstitutionalUpload`) | Conservar grabaciones en R2, procesar con el navegador cerrado y reintentar sin volver a subir | Activa; subida, cancelación, duplicados y reproducción comprobados |
| 2. Transcripción normalizada (`normalizedTranscript`) | Versionar texto, hablantes y fragmentos con tiempos que pueden citarse | Activa; transcripción del proveedor en ambos idiomas |
| 3. Revisión por excepciones (`exceptionReview`) | Resolver sólo los problemas que requieren una decisión; los avisos no bloquean | Activa; revisión en navegador comprobada; estudio de tiempos pendiente |
| 4. Agenda automática (`automaticAgenda`) | Alinear el orden del día opcional con los tramos de la sesión | Activa; alineación comprobada |
| 5. Guía estructurada (`structuredGuide`) | Organizar temas e intervenciones con fuentes y confirmar u omitir posibles acuerdos | Activa; guías citadas en ambos idiomas |
| 6. Publicación pública (`publicPublication`) | Compartir sesión, transcripción y grabación sin cuenta; corregir por versiones y retirar | Activa; acceso anónimo, corrección inmutable y retirada comprobados |
| 7. Búsqueda literal (`lexicalSearch`) | Encontrar palabras, frases, nombres y variantes con filtros y acceso al segundo de la evidencia | Activa; referencia de evaluación: 13/16 consultas con respuesta |
| 8. Búsqueda híbrida (`hybridSearch`) | Combinar palabras y significado, incluidas paráfrasis entre los dos idiomas | Activa; 16/16 consultas con respuesta en el conjunto principal |

```mermaid
flowchart TD
    Upload["1 · Subida duradera<br/>Grabación privada en R2"]
    Transcript["2 · Transcripción normalizada<br/>Texto, hablantes, tiempos y revisiones"]
    Review["3 · Revisión por excepciones<br/>Sólo incidencias obligatorias"]
    Agenda["4 · Agenda automática<br/>Orden del día opcional"]
    Guide["5 · Guía estructurada<br/>Temas e intervenciones con evidencia"]
    Publish["6 · Publicación pública<br/>Versión inmutable y reproducción"]
    Lexical["7 · Búsqueda literal<br/>Palabras, frases y filtros"]
    Hybrid["8 · Búsqueda híbrida<br/>Palabras + significado"]
    Source["Ver evidencia<br/>Fragmento y segundo de la grabación"]

    Upload --> Transcript --> Review
    Review --> Agenda --> Guide --> Publish
    Review -.->|"Publicable sin agenda ni guía"| Publish
    Publish -->|"Indexación en segundo plano"| Lexical
    Lexical -->|"Añade coincidencias por significado"| Hybrid
    Lexical --> Source
    Hybrid --> Source
```

La guía es asistida y no sustituye al acta oficial. No exige aprobar cada tema ni leer
por segunda vez toda la transcripción. Los acuerdos dudosos se confirman u omiten.
Una corrección publica una versión nueva con nota visible; retirar bloquea nuevos
accesos públicos e invalida ambas búsquedas.

Las cifras corresponden a pruebas pequeñas y ficticias. El conjunto adicional obtuvo
6/10 en literal y 9/10 en híbrida; los diez casos sin respuesta entre ambos conjuntos
fueron correctos y el p95 híbrido en caliente quedó por debajo de 2,4 s. Una respuesta
concreta sigue fuera de los primeros 20 resultados del conjunto adicional. Quedan
pendientes sesiones reales y de duración habitual, tiempos humanos, carga,
accesibilidad, restauración y gobernanza, además del anexo de producción.

---

## Piezas del sistema

| Componente | Repo | Runtime | Rol |
|---|---|---|---|
| **subtitula-web** | `subtitula-gal` | Next.js + OpenNext sobre **Cloudflare Worker** | UI + dos *gateways* same-origin: `/backend/*` y `/processing/*` |
| **subtitula-api** | `subtitula-gal-api` | **Spring Boot** en **Cloudflare Container** (expuesto por un Worker + Durable Object) | Autoridad de dominio: auth, proyectos, evidencia, publicación, búsqueda |
| **subtitula-processing** | `subtitula-gal-api/processing-worker` | **Cloudflare Worker** + 3 Workflows | Firma R2, orquesta ElevenLabs, Workers AI y los Workflows; crons |

### Servicios externos (leyenda)

| Servicio | Para qué | Quién lo llama |
|---|---|---|
| 🟠 **ElevenLabs Scribe v2** | Transcripción voz→texto | Spring (creador, síncrono) · Processing Worker (institucional, asíncrono con webhook) |
| 🟣 **Cloudflare Workers AI** | `@cf/zai-org/glm-4.7-flash` (guía) · `@cf/baai/bge-m3` (embeddings 1024-dim) | Processing Worker (enriquecimiento, indexación, consulta de búsqueda) |
| 🔵 **Cloudflare R2** (bucket privado) | Media durable, artefactos de proveedor, transcripciones normalizadas, lotes de embeddings | Processing Worker (PUT/GET firmados); navegador (PUT/GET directo firmado) |
| 🟢 **PlanetScale PostgreSQL 17 + pgvector** | Estado de dominio, FTS léxico y vectores semánticos | Spring (JDBC/TLS directo) |
| 🟡 **Cloudflare Email Service** (prod) / **Mailpit** (local) | Verificación y reset de contraseña | Spring (`EmailSender`). En *dev* está `disabled` |
| ⚪ **Google OAuth** | Inicio de sesión federado | Spring (Spring Security OAuth2) |
| 🔴 **Cloudflare Rate Limiting / Workflows / Cron** | Límite de peticiones, ejecución durable, tareas programadas | Processing Worker |

**Autenticación entre piezas:**
- Navegador → Workers: cookie de sesión + comprobación de `Origin` y *echo* CSRF (`x-xsrf-token`).
- Processing Worker → Spring (rutas `/internal/*`): peticiones **firmadas con HMAC** (`INTERNAL_API_HMAC_SECRET`) + *nonce*.
- Web Worker → API/Processing Worker: **service bindings** (`API_SERVICE`, `PROCESSING_SERVICE`);
  un `fetch()` público entre Workers de la misma zona `workers.dev` daría el error 1042.

---

## 1. Mapa de contenedores y servicios externos

```mermaid
flowchart TB
    Browser["🖥️ Navegador<br/>(Next.js UI)"]

    subgraph CF["☁️ Cloudflare"]
        subgraph WEB["subtitula-web · Worker (OpenNext)"]
            UI["UI Next.js"]
            GWB["/backend/* gateway"]
            GWP["/processing/* gateway"]
        end

        subgraph API["subtitula-api · Container (Spring Boot)"]
            SpringPub["API de aplicación<br/>/projects con sesión · /auth y /public"]
            SpringInt["Rutas internas firmadas<br/>/internal/*"]
        end

        subgraph PROC["subtitula-processing · Worker"]
            ProcHttp["HTTP: upload, media, search, webhook"]
            WF["Workflows durables:<br/>Ingest · Enrich · Index"]
            Cron["Cron: limpieza + reconciliación"]
        end

        R2[("🔵 R2 privado<br/>media + artefactos")]
        AI["🟣 Workers AI<br/>GLM-4.7-flash · BGE-M3"]
        RL["🔴 Rate Limiting"]
    end

    PG[("🟢 PlanetScale<br/>PostgreSQL 17 + pgvector")]
    EL["🟠 ElevenLabs Scribe v2"]
    Google["⚪ Google OAuth"]
    Mail["🟡 Email Service / Mailpit"]

    Browser -->|"HTTPS same-origin"| UI
    Browser -->|"/backend/*"| GWB
    Browser -->|"/processing/*"| GWP
    Browser -.->|"PUT/GET media firmado"| R2

    GWB -->|"API_SERVICE"| SpringPub
    GWP -->|"PROCESSING_SERVICE"| ProcHttp

    SpringPub -->|"JDBC/TLS"| PG
    SpringInt -->|"JDBC/TLS"| PG
    SpringPub -->|"multipart (creador)"| EL
    SpringPub --> Google
    SpringPub --> Mail

    ProcHttp -->|"HMAC firmado"| SpringInt
    ProcHttp --> R2
    ProcHttp --> RL
    ProcHttp -->|"submit + webhook"| EL
    WF --> R2
    WF --> AI
    WF -->|"HMAC firmado"| SpringInt
    WF -->|"async"| EL
    ProcHttp -->|"embed consulta"| AI
    Cron --> R2
    Cron -->|"HMAC firmado"| SpringInt
```

---

## 2. Autenticación (contraseña y Google)

El registro/verificación es **no bloqueante**: la sesión se crea aunque el email falle.

```mermaid
sequenceDiagram
    participant B as Navegador
    participant W as web /backend gateway
    participant S as Spring (Container)
    participant PG as 🟢 PostgreSQL
    participant M as 🟡 Email Service/Mailpit
    participant G as ⚪ Google

    rect rgb(245,245,255)
    note over B,M: Registro / login por contraseña
    B->>W: POST /backend/auth/register (email, pass)
    W->>S: API_SERVICE → POST /auth/register
    S->>PG: crea User + sesión (Spring Session JDBC)
    S-->>M: envía verificación (best-effort; disabled en dev)
    S-->>B: 201 + cookie de sesión
    end

    rect rgb(245,255,245)
    note over B,G: Google Sign-In
    B->>W: GET /backend/oauth2/authorization/google
    W->>S: API_SERVICE
    S-->>B: 302 a Google (pasa a través del gateway)
    B->>G: consentimiento OAuth
    G-->>B: 302 a /backend/login/oauth2/code/google
    B->>W: callback con code
    W->>S: API_SERVICE
    S->>G: intercambia code por perfil
    S->>PG: liga OAuthAccount + crea sesión
    S-->>B: 302 a la app + cookie
    end
```

---

## 3. Flujo creador (legacy, síncrono)

Camino del PoC de subtitulado individual. Spring transcribe **en línea** contra ElevenLabs; el
editor y la exportación (SRT/VTT/burn-in) son del lado del navegador. La media del creador se
conserva de momento en el navegador (IndexedDB).

```mermaid
sequenceDiagram
    participant B as Navegador (editor)
    participant W as web /backend gateway
    participant S as Spring
    participant EL as 🟠 ElevenLabs Scribe v2
    participant PG as 🟢 PostgreSQL

    B->>W: POST /backend/projects (multipart: vídeo)
    W->>S: API_SERVICE → POST /projects
    S->>EL: POST /v1/speech-to-text (file, language_code=glg o spa)
    EL-->>S: words[] + language_code
    S->>PG: guarda Project + transcripción
    S-->>B: 201 ProjectResponse
    B->>B: edición de cues, estilos, posición
    B->>W: PATCH /backend/projects/{id} (autosave)
    W->>S: API_SERVICE → PATCH
    S->>PG: persiste cambios
    B->>B: exporta SRT/VTT o quema subtítulos (cliente)
```

---

## 4. Ingesta institucional (subida + transcripción asíncrona)

Camino de transparencia. La media va **directa del navegador a R2** (URL firmada); la transcripción
la orquesta un Workflow durable y ElevenLabs responde por **webhook**.

```mermaid
sequenceDiagram
    participant B as Navegador
    participant P as web /processing gateway → Processing Worker
    participant S as Spring /internal (HMAC)
    participant R2 as 🔵 R2 privado
    participant WF as Workflow Ingest
    participant EL as 🟠 ElevenLabs Scribe v2
    participant PG as 🟢 PostgreSQL

    B->>P: POST /processing/upload-intents
    P->>S: createUploadIntent (intent+recording+job)
    S->>PG: persiste intent
    P->>P: firma uploadToken (HMAC) + presign R2 PUT
    P-->>B: uploadUrl (presigned) + token
    B->>R2: PUT bytes de media (directo)
    B->>P: POST /processing/upload-intents/{id}/complete
    P->>R2: HEAD (verifica tamaño/tipo)
    P->>S: completeUpload
    S->>PG: marca uploaded
    P->>WF: arranca Ingest workflow

    WF->>S: jobContext / startJob
    WF->>R2: presign GET para el proveedor
    WF->>EL: submitTranscription(sourceUrl, idioma del proyecto, webhookMetadata)
    EL-->>P: POST /webhooks/elevenlabs/speech-to-text (firmado)
    P->>R2: guarda artefacto crudo
    P->>S: webhookReceived
    P->>WF: sendEvent(transcription_complete)
    WF->>R2: lee artefacto → normaliza → guarda normalizado
    WF->>S: ingestTranscript (segmentos, hablantes, coste)
    S->>PG: persiste transcripción normalizada
```

---

## 5. Enriquecimiento (agenda + guía estructurada)

Alinea la agenda de forma **determinista** (sin IA) y genera la guía con **Workers AI** por ventanas
acotadas y citadas. Todos los artefactos intermedios viven en R2. La revisión humana por excepción
es una cola corta. No se exige aprobar línea a línea ni cada tema generado.
La extracción usa `guide-v3`; cada entidad nueva tiene identidad propia y los
reintentos exactos conservan su resultado sin sobrescribir una guía publicada.

```mermaid
sequenceDiagram
    participant B as Navegador
    participant P as Processing Worker
    participant WF as Workflow Enrich
    participant S as Spring /internal
    participant R2 as 🔵 R2 privado
    participant AI as 🟣 Workers AI (GLM-4.7-flash)
    participant PG as 🟢 PostgreSQL

    B->>P: POST /processing/projects/{id}/enrichment
    P->>S: startEnrichment
    P->>WF: arranca Enrich workflow

    WF->>S: enrichmentContext (transcripción + agenda)
    WF->>R2: congela contexto (snapshot)
    WF->>WF: alignAgenda (determinista) + planifica ventanas
    loop por cada ventana de guía
        WF->>R2: lee contexto/plan
        WF->>AI: generateGuideWindow (guía citada)
        WF->>R2: guarda ventana generada
    end
    WF->>R2: valida y ensambla guía con evidencia
    WF->>S: ingestGuide / ingestAgenda (+ coste tokens)
    S->>PG: persiste guía, temas, decisiones candidatas
```

---

## 6. Publicación + indexación de búsqueda

La publicación crea un **snapshot inmutable** y encola un trabajo de indexación en
Spring. La página pública queda accesible; la búsqueda se habilita cuando su proyección
está lista. El frontal arranca el trabajo de inmediato, también con híbrida apagada.
El cron recupera trabajos pendientes y reutiliza la instancia admitida si coincide
con el navegador. Primero se construye la proyección **léxica** y después, si procede,
la **semántica** (embeddings BGE-M3 en pgvector).

```mermaid
sequenceDiagram
    participant B as Navegador
    participant W as web gateways
    participant S as Spring
    participant P as Processing Worker
    participant WF as Workflow Index
    participant R2 as 🔵 R2 privado
    participant AI as 🟣 Workers AI (BGE-M3)
    participant PG as 🟢 PostgreSQL

    B->>W: POST /backend/.../publications (publica)
    W->>S: crea snapshot inmutable
    S->>PG: Publication + documentos fijados + job INDEX
    S-->>B: publicación accesible + indexJobId

    B->>P: POST /processing/publications/{id}/index
    P->>S: prepareLexicalWorkflow
    P->>WF: arranca o reutiliza Index workflow admitido
    WF->>S: buildLexicalIndex (FTS)
    S->>PG: proyección léxica (tsvector, trigram)
    alt búsqueda semántica activa
        WF->>S: embeddingContext (documentos activos)
        WF->>R2: escribe lotes de documentos
        loop por lote
            WF->>AI: embedTexts (BGE-M3, 1024-dim)
            WF->>S: ingestEmbeddings
            S->>PG: guarda vectores (pgvector)
        end
        WF->>S: completeEmbeddings (+ coste)
    end
```

---

## 7. Búsqueda híbrida pública

Fusiona candidatos léxicos y semánticos con **RRF (k=60)** y una similitud semántica
mínima de **0,48**. Un tema relacionado también recupera sus fragmentos citados,
siempre dentro de la misma publicación activa y su guía fijada. Un fallo de IA o
de la consulta híbrida recurre a **búsqueda léxica**. La analítica no guarda el texto
de consulta (sólo HMAC y metadatos).

```mermaid
sequenceDiagram
    participant B as Navegador (explorador público)
    participant P as web /processing gateway → Processing Worker
    participant RL as 🔴 Rate Limiter
    participant AI as 🟣 Workers AI (BGE-M3)
    participant S as Spring /internal
    participant PG as 🟢 PostgreSQL

    B->>P: GET /processing/search?q=... (o /sessions/{slug}/search)
    P->>RL: limit(actor)
    alt dentro de límite
        alt IA y consulta híbrida disponibles
            P->>AI: embedTexts(consulta) → vector
            P->>S: hybridSearch(query, embedding, filtros)
            S->>PG: léxico + semántico con evidencia citada → RRF
            S-->>P: resultados con evidencia (timestamps)
            P-->>B: 200 resultados
        else fallo de IA o consulta híbrida
            P->>S: fallback GET /public/search (sólo léxico)
            S->>PG: FTS + trigram
            S-->>P: resultados léxicos
            P-->>B: 200 resultados léxicos
        end
    else límite de peticiones superado
        P-->>B: 429, sin llamada a IA
    end
```

---

## 8. Tareas programadas (cron del Processing Worker)

```mermaid
flowchart LR
    C5["⏰ cada 5 min<br/>*/5 * * * *"]
    C1["⏰ diario 03:17 UTC<br/>17 3 * * *"]

    C5 --> CE["cleanupExpiredUploads"]
    C5 --> RW["reconcilePendingWorkflows"]
    C1 --> CA["cleanupSearchAnalytics"]

    CE -->|"finaliza intents caducados"| S["🟢 Spring /internal"]
    CE -->|"borra objetos huérfanos"| R2["🔵 R2"]
    RW -->|"reencola Ingest/Enrich/Index"| WF["Workflows"]
    RW --> S
    CA -->|"purga analítica > retención (90d)"| S
```

---

## Notas transversales

- **Desarrollo activo:** rutas públicas restauradas; previews apagadas; despliegues
  GitHub habilitados en ambos repos y processor; dos tareas programadas activas.
  Producción conserva rutas, tareas y capacidades apagadas hasta el lanzamiento.
- **Un solo código, un solo artefacto:** *local*, *dev* y *prod* ejecutan el mismo binario; solo
  cambian variables/secretos y *capabilities*. Prod promociona el mismo *digest* probado en dev.
- **Email por entorno:** local usa Mailpit (no sale correo real), dev tiene `EMAIL_PROVIDER=disabled`
  (falla visible en logs), prod usará Cloudflare Email Service (requiere dominio propio, aún diferido).
- **Reproducción privada de media:** el navegador reutiliza primero su copia en IndexedDB; si no,
  el Processing Worker autoriza contra Spring y entrega un GET R2 firmado de corta duración (en local
  hay un proxy Range porque no hay credenciales S3).
- **Sin servicios de más:** el híbrido vive en PostgreSQL/pgvector; no se usan Vectorize, AI Search,
  D1, Kafka ni Hyperdrive (Spring conecta a Postgres por JDBC directo).
