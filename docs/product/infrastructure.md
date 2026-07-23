# Infraestructura y entornos

**Estado:** hosted dev desplegado; contraseña y Google validados en `workers.dev`; dominio/producción pública en anexo · **Actualizado:** 23 de julio de 2026.

## Regla de base

La API tiene una sola implementación y un artefacto OCI. Dev despliega un digest y producción promociona el mismo digest. Local, dev y prod usan PostgreSQL 17, JPA/JDBC, Flyway, Spring Session y ElevenLabs; solo cambian perfiles, URLs, credenciales y límites.

`develop` representa development; `master` (frontend) y `main` (API) representan production. Los workflows de `develop` verifican y despliegan automáticamente porque la variable de repositorio `DEPLOY_ENABLED=true`; producción continúa bloqueada por el anexo.

## Fases

| Servicio | Local | Hosted dev ahora | Producción futura |
|---|---|---|---|
| Next.js | `localhost:3000` | `subtitula-web-dev.<account>.workers.dev` | dominio propio, misma fuente |
| Spring | `localhost:8080` | `subtitula-api-dev.<account>.workers.dev` | dominio propio, mismo digest |
| PostgreSQL | Docker 17 | PlanetScale dev | PlanetScale prod HA |
| Google | OAuth Web localhost | cliente dev y callback same-origin por `/backend`; UI habilitada | cliente y dominio verificados |
| ElevenLabs | clave Free/local | clave dev con cuota | clave production con alertas |
| Email | Mailpit E2E; UI habilitada | `EMAIL_PROVIDER=disabled`, fallo visible; UI de email oculta | Cloudflare Email Service |
| Objetos | temporal | R2 dev cuando se implemente | R2 prod |

`workers.dev` elimina la compra y zona DNS como prerrequisito. Cloudflare no lo considera host de producción crítica, y no podemos usarlo como dominio remitente. El [anexo de lanzamiento](../operations/custom-domain-launch-annex.md) agrupa dominio propio, correo, Google público y producción HA.

## Configuración

| Grupo | Contrato |
|---|---|
| Perfil | `local`, `dev` o `prod`; dev/prod comparten `hosted` |
| Datos | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_*`; misma API PostgreSQL |
| OAuth | clientes/secrets aislados; callback exacto |
| Hosts | `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_SITE_URL`, `CORS_ORIGINS`, `FRONTEND_URL` |
| Transcripción | `ELEVENLABS_API_KEY`, `scribe_v2`, `glg`, cuota por entorno |
| Correo | local SMTP/Mailpit + flag frontend `true`; dev provider/required `disabled`/`false` + flag frontend `false`; prod SMTP/required + flag `true` |
| Deploy | Account ID como variable, token mínimo como secret, `DEPLOY_ENABLED` como gate |

No enviar secretos por chat ni guardarlos en el repo. Las instrucciones ejecutables están en [Configuración de secretos](../operations/secrets-setup.md).

## Estado y orden actual

1. Hecho: Workers Paid, PlanetScale dev, credenciales no productivas, manifests y CI/CD.
2. Hecho: frontend/API dev desplegados; Flyway, JDBC/TLS, CORS, cookies, CSRF, registro, logout y login validados.
3. Hecho: E2E local con PostgreSQL 17 y correo capturado en Mailpit.
4. Pendiente: ejecutar una transcripción dev real y medir latencia, memoria/cuota y coste.
5. Hecho: Google OAuth se enruta por el gateway y se muestra en hosted dev.
6. Mantener producción sin provisionar hasta que exista dominio/fecha de piloto; entonces ejecutar el anexo y promover el digest aprobado.

## Coste

| Ámbito | Base mensual aproximada antes de IVA/overages |
|---|---:|
| Local | USD 0 |
| Hosted dev | USD 10: Workers Paid 5 + PlanetScale dev 5 |
| Incremento al lanzar prod | USD 15 de PlanetScale HA + coste del dominio |
| Dev + prod tras lanzamiento | USD 25 + dominio |

No incluye consumo por encima de cuotas de Containers, Workers, R2, ElevenLabs o correo. El hostname `workers.dev` no añade coste.
