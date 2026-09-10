# subtitula.gal — Arquitectura y flujos

> Recuperado de `desktop-mes66hq-fluttering-lake` (17 agosto) y versionado el
> 8 septiembre 2026. Es un mapa de la implementación, no una certificación de
> despliegue. Consultar el [estado consolidado](../operations/continuity-2026-09-08.md)
> para capacidades, copias locales y pendientes actuales.

Diagramas de alto nivel de los dos repos (`subtitula-gal` = frontal, `subtitula-gal-api` = back)
y de los servicios externos que ataca cada paso. Los diagramas están en **Mermaid**: un mapa de
contenedores para el funcionamiento general y diagramas de secuencia por flujo, que es donde se ve
qué servicio externo interviene en cada función.

> Estado (a 30-jul-2026): base *hosted dev* desplegada; la vertical institucional/transparencia
> está implementada en local detrás de *capabilities* apagadas. Los diagramas describen el sistema
> completo tal como está en el código, marcando lo que hoy está *dark*.

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
            SpringPub["Rutas públicas<br/>/auth /projects /public ..."]
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
    S->>EL: POST /v1/speech-to-text (file, language_code=glg)
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
    WF->>EL: submitTranscription(sourceUrl, webhookMetadata)
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
es un paso aparte (cola corta), no un aprobado línea a línea.

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

La publicación crea un **snapshot inmutable** en Spring. La indexación construye la proyección
**léxica** (FTS de Postgres) y, si procede, la **semántica** (embeddings BGE-M3 en pgvector).

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
    S->>PG: Publication + documentos

    B->>P: POST /processing/publications/{id}/index
    P->>S: prepareLexicalWorkflow
    P->>WF: arranca Index workflow
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

Fusiona candidatos léxicos y semánticos con **RRF (k=60)**. Cualquier fallo de IA o interno degrada
con gracia a **búsqueda léxica** pura. Analítica sin texto de consulta (solo HMAC + metadatos).

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
        P->>AI: embedTexts(consulta) → vector
        P->>S: hybridSearch(query, embedding, filtros)
        S->>PG: léxico top-100 + semántico top-100 → RRF
        S-->>P: resultados con evidencia (timestamps)
        P-->>B: 200 resultados
    else IA o interno falla
        P->>S: fallback GET /public/search (solo léxico)
        S->>PG: FTS + trigram
        S-->>B: 200 resultados léxicos
    end
```

---

## 8. Tareas programadas (cron del Processing Worker)

```mermaid
flowchart LR
    C5["⏰ cada 5 min<br/>*/5 * * * *"]
    C1["⏰ diario 03:17<br/>17 3 * * *"]

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

- **Un solo código, un solo artefacto:** *local*, *dev* y *prod* ejecutan el mismo binario; solo
  cambian variables/secretos y *capabilities*. Prod promociona el mismo *digest* probado en dev.
- **Email por entorno:** local usa Mailpit (no sale correo real), dev tiene `EMAIL_PROVIDER=disabled`
  (falla visible en logs), prod usará Cloudflare Email Service (requiere dominio propio, aún diferido).
- **Reproducción privada de media:** el navegador reutiliza primero su copia en IndexedDB; si no,
  el Processing Worker autoriza contra Spring y entrega un GET R2 firmado de corta duración (en local
  hay un proxy Range porque no hay credenciales S3).
- **Sin servicios de más:** el híbrido vive en PostgreSQL/pgvector; no se usan Vectorize, AI Search,
  D1, Kafka ni Hyperdrive (Spring conecta a Postgres por JDBC directo).
