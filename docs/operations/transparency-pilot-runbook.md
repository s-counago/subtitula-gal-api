# Runbook do piloto de transparencia, evidencia e busca

**Estado:** implementación local lista; recursos hosted e gates de piloto
pendentes · **Actualizado:** 30 de xullo de 2026.

Este runbook opera o fluxo descrito en
[`transparency-evidence-search-implementation-plan.md`](../product/transparency-evidence-search-implementation-plan.md).
Non autoriza un despregamento, non contén segredos e non substitúe o anexo de
produción. Un push a `develop` desprega automaticamente: comprobar todos os
prerrequisitos antes de facer push.

## 1. Estado seguro por defecto

As migracións V11–V18 son aditivas e o frontend só mostra cada corte cando a API
devolve a capacidade. Os valores versionados de development son:

```text
normalized transcript      true
durable upload             false
exception review           false
automatic agenda           false
structured guide           false
public publication         false
lexical search             false
hybrid search              false
```

Non activar varios cortes á vez. Un fallo de enriquecemento ou embeddings nunca
debe retirar unha publicación nin desactivar a busca literal.

## 2. Prerrequisitos duros antes do primeiro deploy

1. **PlanetScale:** habilitar deliberadamente `vector` na rama
   `development` e comprobar `vector`, `unaccent` e `pg_trgm` co rol de
   migración. V17 crea a extensión e unha columna `vector(1024)`; un deploy antes
   desta comprobación pode fallar en Flyway aínda que `hybrid-search=false`.
2. **R2:** crear un bucket privado de development chamado
   `subtitula-media-dev`. Só datos sintéticos ou xa públicos. Aplicar e verificar
   a política de navegador versionada no repositorio:

   ```powershell
   cd processing-worker
   npm run r2:cors:dev:apply
   npm run r2:cors:dev:list
   ```
3. **Processing Worker:** confirmar os nomes
   `subtitula-processing-dev`, `subtitula-ingest-dev`,
   `subtitula-enrich-dev` e `subtitula-index-dev`.
4. **Bindings:** `API_SERVICE` do processor debe apuntar a
   `subtitula-api-dev`; `PROCESSING_SERVICE` do web debe apuntar ao processor.
   Non substituír estes bindings por `fetch()` público entre Workers.
5. **Rate limiting:** comprobar que os namespace IDs `10001`–`10004` son
   únicos na conta antes do deploy. Non reciclalos para outra regra.
6. **Segredos:** instalar, por ambiente, só os nomes listados na sección 3.
   Non intentar ler de volta un Worker Secret.
7. **ElevenLabs:** configurar webhook STT cara ao processor e gardar o seu ID
   como variable non secreta. A proba real usa un clip público curto e unha
   clave development con límite de gasto.
8. **Backups:** tomar un backup verificable da base development antes das
   migracións e confirmar que a política/versionado de R2 permite recuperar un
   obxecto borrado por erro operativo.

PlanetScale documenta as extensións dispoñibles e pgvector; local e
Testcontainers usan `pgvector/pgvector:pg17`, conservando PostgreSQL major 17.
Non montar un volume de PostgreSQL 16 nesta imaxe nin borrar
`postgres17-data`.

## 3. Inventario de configuración

### Segredos do API Worker/Container

- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `ELEVENLABS_API_KEY` para o camiño creador legado mentres exista
- `INTERNAL_API_HMAC_SECRET`
- `SEARCH_ANALYTICS_HMAC_SECRET` opcional; se está baleiro non se persisten
  consultas nin clicks

### Segredos do Processing Worker

- `ELEVENLABS_API_KEY`
- `ELEVENLABS_WEBHOOK_SECRET`
- `INTERNAL_API_HMAC_SECRET`, exactamente o mesmo valor que no API
- `R2_S3_ACCESS_KEY_ID`
- `R2_S3_SECRET_ACCESS_KEY`

As credenciais S3 só as usa a sinatura de PUT/GET; o código dentro do Worker usa
o binding R2. Ningún destes nomes pode ser `NEXT_PUBLIC_*`.

### Variables relevantes

- modelos, dimensións e prezos estimados están en
  `processing-worker/wrangler.jsonc`;
- a API recibe o mesmo modelo/dimensión e prezo de query embedding;
- `SEARCH_ANALYTICS_RETENTION_DAYS=90`;
- `UPLOAD_CLEANUP_GRACE_SECONDS=3600`;
- `MEDIA_URL_TTL_SECONDS=900`; o editor renova a URL privada se caduca;
- o script de deploy deriva `R2_S3_ENDPOINT` de `CLOUDFLARE_ACCOUNT_ID` e
  falla cedo se falta nun despregamento real;
- `WEBHOOK_MAX_BYTES=16777216`; o HMAC actualízase sen copiar o body e o límite
  mantén acoutada a memoria durante o parseo JSON;
- as oito variables `CAPABILITY_*` controlan o rollout no servidor.

Os prezos son supostos configurados en microunidades de USD. As métricas din
“estimación bruta”; nunca se presentan como factura.

## 4. Verificación local antes do hosted smoke

Desde o repositorio API:

```powershell
.\mvnw.cmd -B test
npm run cf-typegen
npm run typecheck:worker
npm run test:processing-worker
npm run test:search-evaluation
npm run deploy:dev:dry-run
```

Desde o frontend:

```powershell
npm test
npm run build
npm run deploy:dev:dry-run
```

Para E2E, iniciar desde `subtitula-gal` con `.\start-up.ps1`. O processor local
queda en `:8787`; o endpoint de Cron local é
`/cdn-cgi/handler/scheduled?format=json`. Probar:

- creación/expiración/abort da intención sen cargar o ficheiro en Spring;
- pechar o navegador despois do PUT e reabrir o editor no mesmo dispositivo e
  noutro sen copia IndexedDB;
- fallo retryable do provedor e “Tentar de novo” sen volver subir;
- Range de media e salto a timestamp;
- completar só issues requiridos, non ler toda a transcrición;
- guía sen axenda e guía con transición ambigua;
- publicar, recargar, corrixir, publicar con nota e retirar;
- reindexar conservando os eventos de click;
- busca literal dispoñible cando AI ou Workflow fallan.

## 5. Hosted smoke e orde de activación

Executar cunha soa sesión pública sintética:

1. Deploy de esquema escuro, processor e bindings; todas as capacidades novas
   permanecen `false`.
2. Confirmar `/ping`, Cron, R2 privado e sinaturas/replay internos.
3. Activar `durable upload`; subir e abandonar un ficheiro de proba, e verificar
   a limpeza despois do grace period.
4. Activar `exception review`; medir tempo activo e comprobar descoñecidos.
5. Activar xuntos `automatic agenda` e despois `structured guide`. A guía pode
   omitirse; nunca bloquea a transcrición publicable.
6. Activar `public publication`, comprobar DTO allowlist e Range, e só entón
   `lexical search`.
7. Etiquetar un gold set real en galego e castelán e executar:

   ```powershell
   npm run evaluate:search -- --origin <API_ORIGIN> --mode lexical --fixture <gold.jsonl>
   npm run evaluate:search -- --origin <WEB_ORIGIN> --mode hybrid --fixture <gold.jsonl>
   ```

8. Activar `hybrid search` só se mellora materialmente o éxito sen empeorar
   precisión de “sen evidencia”, neutralidade, latencia ou marxe.

O informe de avaliación contén métricas agregadas e IDs de casos, non o texto
das consultas. O JSONL etiquetado é dato de produto gobernado e non debe conter
transcrición privada.

## 6. Gates de carga humana, relevancia e custo

Por sesión, revisar o panel “Métricas do piloto”:

- revisión/enriquecemento habitual: **2–5 minutos activos**;
- máis de 10 minutos: fallo de produto; non compensar con formación ou outro
  paso manual;
- tarefas manuais, issues resoltos/descartados e tempo ata publicación;
- custo bruto configurado total e por hora de media;
- volume, p50/p95, resultados baleiros e clicks de evidencia atribuíbles.

Para busca, acordar por escrito os limiares antes do pilot: Recall@20, task
success@20, MRR/nDCG, false-no-answer, correct-no-answer e p95. Non graduar
hybrid só porque sobe recall: unha resposta plausible sen evidencia é peor que
un baleiro honesto.

Alertas mínimas hosted:

- erro de Workflow, Cron ou webhook replay;
- job `failed_retryable` sen reintento durante 24 h;
- limpeza diaria fallida;
- custo real do provedor por riba do suposto configurado;
- revisión activa >5 min e alarma forte >10 min;
- p95 de busca ou cold start fóra do SLO acordado.

## 7. Recuperación e reindex

### Retry normal

- subida interrompida antes de completar: abortar e crear intención nova;
- transcrición co obxecto xa verificado: usar “Tentar de novo”; non pedir media;
- enriquecemento: conservar a revisión conxelada e rexenerar por hash;
- embeddings: a publicación e a busca literal seguen activas.

### Restore

1. Restaurar PostgreSQL e R2 ao mesmo punto lóxico ou comprobar cada
   `recordings.object_key` publicado.
2. Verificar que publicacións withdrawn seguen withdrawn e que só unha revisión
   activa por proxecto ten documentos de busca activos.
3. Como propietario, abrir **Mantemento da busca → Reconstruír a busca**. O
   endpoint `POST /projects/{projectId}/publications/{publicationId}/reindex`
   reutiliza identidades de documento, conserva clicks e limpa embeddings
   obsoletos.
4. O reconciliador do processor inicia o Workflow lexical/semántico pendente. O
   botón do frontend é só unha aceleración e un reintento manual; pechar o
   navegador non pode deixar o job en cola para sempre.
5. Executar o gold set e probar Range/timestamps antes de reabrir o piloto.

Nunca recuperar borrando táboas, prefixes R2 ou facendo reset da base. Rollback
é desactivar capacidades e aplicar un forward fix.

## 8. Retención e borrado

O Cron development corre cada cinco minutos para reconciliar Workflows e
subidas, e ás 03:17 UTC engade a limpeza diaria de analítica:

- selecciona como máximo 100 intencións expiradas tras unha hora de graza,
  marca primeiro o aggregate como `EXPIRED`, borra despois o obxecto R2 e só
  entón confirma `DELETED`; un fallo de R2 deixa a gravación reintentable e unha
  gravación `VERIFIED` nunca é candidata;
- inicia con IDs idempotentes jobs `QUEUED` ou `FAILED_RETRYABLE` que quedaron
  sen segundo paso de navegador;
- borra `search_query_events` con máis de 90 días; os clicks caen en cascada.

Só se gardan HMAC da consulta, lonxitude, filtros acoutados, modo, resultados,
latencia e custo estimado. Nunca se garda a query crúa, IP nin user-agent nesta
analítica.

Os orixinais verificados, artefactos de provedor, revisións conxeladas e
snapshots publicados non se eliminan automaticamente ata aprobar por
organización unha política contractual de retención, exportación e saída. Esa
decisión, xunto con DPA, subprocesadores, localización, RGPD/ENS e
accesibilidade, é gate de piloto con datos non públicos.

## 9. Rollback

1. Desactivar primeiro `hybrid`, despois `lexical/publication`, guía/axenda,
   review e durable upload, sen tocar datos.
2. Non reverter Flyway nin retirar V11–V18.
3. Conservar R2, artefactos, revisións, publicacións e jobs.
4. Se o processor está indispoñible, o API e proxectos legacy seguen operando;
   non mostrar capacidades dependentes.
5. Documentar incidente, versión, flags, jobs afectados e forward fix.

## Referencias oficiais

- [Cloudflare Workflows](https://developers.cloudflare.com/workflows/)
- [Cloudflare Cron Triggers](https://developers.cloudflare.com/workers/configuration/cron-triggers/)
- [Cloudflare Workers Rate Limiting](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/)
- [Cloudflare R2 presigned URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/)
- [Workers AI BGE-M3](https://developers.cloudflare.com/workers-ai/models/bge-m3/)
- [Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/)
- [PlanetScale PostgreSQL extensions](https://planetscale.com/docs/postgres/extensions)
- [pgvector](https://github.com/pgvector/pgvector)
