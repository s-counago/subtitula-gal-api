# Recuperación de trabajo y punto de continuación — 8 septiembre 2026

## Fuentes y alcance

Se revisaron las tres conversaciones solicitadas, el historial local de Claude,
las dos copias Windows de ambos repositorios y las referencias/configuración de GitHub.
Las conversaciones son evidencia histórica, no una comprobación del estado actual de
Cloudflare, PlanetScale o ElevenLabs. No se desplegó ni se cambiaron credenciales.

| Conversación | Trabajo y último punto |
|---|---|
| [desktop-mes66hq-shimmering-rain](https://claude.ai/code/session_011ex4R8UVt29Jk8jWY2ZbD9) | 5 septiembre: recuperó el rollout local, guardó el arreglo IPv4 del lanzador y la nota de continuidad, y documentó el Worker inexistente y el orden de despliegue. No ejecutó las tres pruebas de subida durable ni hizo push. |
| [desktop-mes66hq-fluttering-lake](https://claude.ai/code/session_012JPXTmi8Y45U4xdw7gzNuz) | 17 agosto: creó ocho diagramas Mermaid de arquitectura y flujos en `ARQUITECTURA-FLUJOS.md`, fuera de Git. No activó capacidades ni desplegó. Documento recuperado en `docs/product/architecture-flows.md`. |
| [omarchy-peaceful-crab](https://claude.ai/code/session_01RNbm1iRBz7uBNQk3ZmBYxU) | Agosto: navegación y herramienta de inventario integradas en `develop` local de Linux; 20 commits por delante de su remoto según el cierre. Subproyecto 3 pendiente de triaje conjunto de 17 pantallas. El usuario decidió el 8 septiembre dejar ese trabajo aparcado y subirlo más adelante si procede. No se ha recuperado ni integrado aquí. |

También se revisó [Documentación de flujos de la aplicación](https://claude.ai/code/session_01PRnggZDvGtftarY24byGQV)
(2–3 septiembre): integración local de transparencia, bootstrap local, extensiones y
la visualización de nueve canales. Esta última estaba fuera de Git y se recuperó como
`docs/product/subtitula-sala-de-control.html`.

## Dos copias Windows explicaban la discrepancia

- `C:/Users/Sejio/subtitula`: copia utilizada por Claude en septiembre. Sus ramas
  `develop` contienen la integración de transparencia y cuatro commits posteriores.
- `C:/Users/Sejio/Dev/subtitula`: carpeta de esta revisión. Estaba en la rama
  `feature/transparency-evidence-search-integrated`, detenida en API `63cf00a` y
  frontend `fa0eeb3`. Se incorporaron los commits de la otra copia mediante fast-forward,
  preservando sus identidades e historia.
- La rama de respaldo es `feature/transparency-evidence-search-integrated` en ambos
  repositorios. Un respaldo de esa rama no activa los workflows de despliegue existentes.
  No hacer push de los `develop` locales como si fuera una simple sincronización.
- Los archivos ignorados y las bases/objetos locales son propios de cada copia:
  sincronizar Git no sincroniza secretos, PostgreSQL ni `.wrangler/state`.

| Repo | Commit recuperado | Qué guarda |
|---|---|---|
| Frontend | `1d1f009` | Sondeo del processor en `127.0.0.1`, timeout de 5 segundos. |
| Frontend | `ec287f4` | Advertencia junto al binding del processor aún no provisionado. |
| API | `de88ad8` | Nota de continuidad de la integración y del bootstrap local. |
| API | `712d018` | Orden de despliegue como prerrequisito del runbook. |

La respuesta de Claude citaba `e63b98d` para el arreglo del lanzador; el commit que
realmente quedó en Git después del amend es `1d1f009`. La nota anterior todavía lo
llamaba «uncommitted» y daba recuentos previos a esos commits. Ambos árboles estaban
limpios al comenzar esta recuperación; los artefactos fuera de los repos sí carecían de commit.

## Tabla de capacidades

«Configuración dev» significa el manifest de la implementación integrada, aún sin
desplegar. No equivale a haber consultado `/capabilities` en el servicio hospedado.
Las fases 0–7 de implementación están desarrolladas; la graduación operacional sigue
en fase 8. La «fase 0 local» de la conversación es una checklist de rollout distinta.

| Capacidad | Local de Claude (`C:/Users/Sejio/subtitula`) | Configuración dev | Qué falta para avanzar |
|---|---|---|---|
| `normalizedTranscript` | Activada por defecto | `true` | Mantener compatibilidad legacy; no confundir con rollout institucional terminado. |
| `durableInstitutionalUpload` | `true` solo en `.env` | `false` | Probar abandono/limpieza, reapertura sin IndexedDB y reintento sin resubida; después smoke real R2/Scribe. |
| `exceptionReview` | `false` | `false` | Validar cola de incidencias, hablantes desconocidos, cierre y tiempo humano. |
| `automaticAgenda` | `false` | `false` | Validar alineación y transiciones ambiguas tras la revisión. |
| `structuredGuide` | `false` | `false` | Workers AI, evidencias válidas y 2–5 minutos de trabajo humano añadido. |
| `publicPublication` | `false` | `false` | Snapshot inmutable, metadatos/permisos, reproducción, corrección y retirada. |
| `lexicalSearch` | `false` | `false` | Corpus publicado, resultados enlazados a evidencia y evaluación léxica. |
| `hybridSearch` | `false` | `false` | Corpus etiquetado GL/ES, mejora de relevancia, no-respuesta, latencia y coste. Mantener apagada. |

En la copia `Dev/subtitula`, el override local de subida durable está ausente y el
valor por defecto es `false`. No se han modificado flags durante esta revisión.

## Claves e infraestructura: resuelto frente a pendiente

| Elemento | Evidencia y siguiente paso |
|---|---|
| ElevenLabs local en la copia de Claude | Clave vacía en API y processor, comprobado el 8 septiembre. No puede transcribir de verdad. |
| ElevenLabs local en `Dev/subtitula` | Hay un valor en API y processor; no se probó vigencia, permisos ni saldo. No se copió a otros entornos. |
| ElevenLabs en Omarchy | La conversación registra un 401, interpretado entonces como clave caducada. La causa exacta/vigencia actual no se verificó aquí. Usaron fixtures SQL para las capturas; eso no valida transcripción real. |
| Google local | Vacío en la copia de Claude, valores presentes sin validar en `Dev/subtitula`. El login por contraseña permite continuar sin OAuth local. |
| HMAC interno local | Presente y coincidente entre API y processor dentro de cada copia Windows, comprobado sin mostrar valores. No implica que esté instalado en hosted. |
| R2 S3 y webhook ElevenLabs | Vacíos en los `.dev.vars` de ambos processors locales; webhook ID también vacío en manifest. Hosted requiere credenciales S3, webhook y su firma. |
| Miniflare | R2 se simula localmente. Upload/lectura Range locales no precisan claves S3; Scribe real no puede descargar un objeto localhost ni devolver su webhook allí. El E2E real source_url/webhook se verifica en hosted con muestra pública. |
| Autenticación Cloudflare local | La sesión de septiembre resolvió un OAuth caducado con login. El binding AI usa servicio remoto incluso en `wrangler dev`. No se ha revalidado su vigencia hoy. |
| PlanetScale | Según la sesión de septiembre, `vector`, `unaccent`, `pg_trgm` ya creados y tipo vector comprobado con rol de aplicación. Falta backup verificable antes de V11–V18. No se repitieron consultas a la DB en esta revisión. |
| R2 hosted | Según el cierre de septiembre, servicio habilitado a nivel cuenta; bucket privado `subtitula-media-dev` y CORS todavía pendientes. Habilitar R2 no crea el bucket. |
| Processor y Workflows | Última evidencia: no provisionados. Confirmar recursos, bindings y namespaces `10001`–`10004` antes de desplegar. |
| GitHub | Comprobado el 8 septiembre: `DEPLOY_ENABLED=true` en los dos repos; `CLOUDFLARE_ACCOUNT_ID` ya existe en ambos Environments development y el secret de despliegue consta por nombre. `PROCESSING_DEPLOY_ENABLED` no está definido en repo ni Environment API. No hace falta volver a crear Account ID. |

GitHub registra despliegues correctos el **28 julio**, API `b2409d0` y frontend
`f4cbd42`. Esto corrige la fecha «23 julio» repetida en las conversaciones, pero no
certifica que no haya habido despliegues manuales posteriores en Cloudflare.

## Orden para retomar transparencia

1. Elegir explícitamente una copia local. Sus envs y datos no son intercambiables.
   La copia de Claude ya tiene el flag local de subida durable; la de `Dev` contiene
   valores de proveedor pendientes de validar. No copiar secretos entre ellas a ciegas.
2. Arrancar con `start-up.ps1` desde el frontend. Verificar Docker y autenticación
   Wrangler si lo exige AI. El arreglo IPv4 ya está guardado.
3. Terminar las tres comprobaciones locales de subida/abandono, reapertura y reintento.
   El registro «stack arriba en 16 s» solo certifica readiness, no el flujo completo.
   Usar dobles de proveedor para fallos locales; una clave ausente/rechazada puede dar
   un fallo terminal y no demuestra un reintento recuperable.
4. Backup de PlanetScale; confirmar extensiones; bucket R2 privado y CORS; credenciales
   y webhook ElevenLabs; HMAC compartido; nombres/bindings/namespaces del processor.
   Seguir el runbook, no activar todas las capacidades para intentar que funcione.
5. Preparar API/processor antes del frontend. El workflow API despliega primero la API
   y luego el processor si el gate está activo; el frontend no debe desplegar hasta que
   `subtitula-processing-dev` exista. Primero health con capacidades nuevas apagadas;
   después habilitar subida durable para su smoke con una muestra pública.
6. Graduar revisión → agenda → guía → publicación → búsqueda léxica por separado.
   Híbrida sigue sujeta a evaluación. Producción y datos institucionales no públicos
   mantienen los gates de gobernanza, accesibilidad, restauración y carga.

## Omarchy: trabajo aparcado por decisión del usuario

Último cierre leído, sin certificar el disco Linux:

- Subproyecto 1: shell/navegación integrado localmente. Punto de partida de la sesión:
  `e448806`, 313 tests, build, lint y dry-run aprobados según la conversación.
- Subproyecto 2: `lib/navigation-graph.ts`, footer/entry router conectados al grafo,
  herramienta `scripts/screens/`, fixtures SQL y galería `docs/screens/index.html`;
  17 rutas × 2 viewports = 34 capturas. Integrado localmente; suite final de 350 tests
  reportada por Claude, no ejecutada aquí.
- Subproyecto 3: soporte de `severity` y ordenación preparados. Las 17 pantallas seguían
  con decisión `pendente`; faltaba triaje con el usuario (decisión, severidad y notas en
  `screens.yml`) antes de aplicar los lotes de coherencia.
- Tres decisiones heredadas: hint de cuenta vacía en el cajón, uso de
  `WORKFLOW_COPY.uploadTitle`, icono `Building2` frente a `Upload`.
- Los tres specs de agosto sí están en `origin/master` del frontend. Su código Linux
  no está en el remoto ni en esta copia; recuperar los specs no recupera esos 20 commits.
- Cuando se retome, respaldar el repo Linux en una rama sin despliegue y reconciliarlo
  con transparencia antes de llevarlo a `develop`. No sustituir una línea de trabajo
  por otra ni asumir que ambas están integradas por compartir el nombre de rama.

## Verificación de esta recuperación

Comprobación de los cuatro worktrees Windows, ramas y stashes; fetch de ambos remotos;
fast-forward de los cuatro commits; configuración y últimos runs de GitHub leídos;
solo presencia de claves y coincidencia local de HMAC; sintaxis PowerShell del lanzador.
Los secretos, datos locales y recursos hospedados quedan fuera del respaldo Git.
Frontend `npm test`: **301/301 pruebas**, 48 archivos, completadas el 8 septiembre.
La advertencia conocida de navegación de jsdom no hace fallar la suite. Los resultados
API, processor, builds y E2E anteriores se conservan como históricos, no como pruebas
nuevas. No se repiten despliegues/dry-runs para esta recuperación documental.
