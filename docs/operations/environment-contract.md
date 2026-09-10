# Contrato operativo de entornos

**8 septiembre 2026: development suspendido por petición del usuario.**
Las puertas de despliegue están en `false`; rutas públicas, previews y Cron
desactivados. Véase [registro de suspensión](hosted-suspension-2026-09-08.md).
Las instrucciones de despliegue siguientes describen cómo funciona el sistema,
no autorizan reactivarlo.

Este documento traduce la estrategia de ramas a un despliegue seguro. No contiene secretos ni habilita proveedores.

## Flujo de Git

```text
feature/* ──PR──> develop ──push──> development
                    │
                    └──PR aprobada──> master (frontend) / main (API) ──push──> production
```

Los workflows de `develop` ejecutan CI y despliegan con Wrangler/OpenNext y Cloudflare Containers cuando la variable de repositorio `DEPLOY_ENABLED=true`. Debe ser de repositorio porque GitHub evalúa el `if:` del job antes de cargar su Environment. Mientras no sea exactamente `true`, no tocan servicios externos. Los workflows de producción siguen bloqueados por el anexo de lanzamiento.

## Invariante de artefacto

La API se compila una sola vez. Desarrollo valida una imagen OCI identificada por digest y producción promociona exactamente ese digest. Los tres entornos ejecutan el mismo código Spring, JPA/JDBC, Flyway y Spring Session sobre PostgreSQL 17. `SPRING_PROFILES_ACTIVE=local|dev|prod` elige configuración externa; `dev` y `prod` activan el mismo perfil `hosted`.

La única excepción temporal es de transporte de correo, elegida por variable y dentro del mismo artefacto:

- local: `EMAIL_PROVIDER=smtp`, `EMAIL_DELIVERY_REQUIRED=false`, contra Mailpit;
- dev pre-dominio: `EMAIL_PROVIDER=disabled`, `EMAIL_DELIVERY_REQUIRED=false`; cada intento falla explícitamente y queda en logs;
- prod: `EMAIL_PROVIDER=smtp`, `EMAIL_DELIVERY_REQUIRED=true`; el arranque falla si falta SMTP o un remitente incorporado.

No existe fallback hospedado.

El frontend de dev se construye con `NEXT_PUBLIC_API_URL=/backend`,
`NEXT_PUBLIC_PROCESSING_URL=/processing`,
`NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false` y
`NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=true`. Oculta temporalmente verificación y
recuperación por correo, pero habilita contraseña, Google y el resto de la
aplicación. Local habilita ambas capacidades; el futuro prod también las
habilitará después de incorporar dominio y correo.

## URLs de la fase actual

Al crear una cuenta de Workers, Cloudflare asigna `<account-subdomain>.workers.dev`. Cada Worker queda en `<worker-name>.<account-subdomain>.workers.dev`; no hace falta comprar ni incorporar una zona DNS.

| Entorno | Frontend | API | Processor | Callback OAuth de Google |
|---|---|---|---|---|
| Local | `http://localhost:3000` | `http://localhost:8080` | `http://localhost:8787` | `http://localhost:8080/login/oauth2/code/google` |
| Desarrollo remoto | `https://subtitula-web-dev.s-counago00.workers.dev` | `https://subtitula-api-dev.s-counago00.workers.dev` | `https://subtitula-processing-dev.s-counago00.workers.dev` (pendiente de provisionar) | `https://subtitula-web-dev.s-counago00.workers.dev/backend/login/oauth2/code/google` |
| Producción real | Definida en el [anexo de lanzamiento](custom-domain-launch-annex.md) | Definida en el anexo | Definida en el anexo | Definida en el anexo |

Los Workers son `subtitula-web-dev`, `subtitula-api-dev` y, cuando se ejecute
el gate de transparencia, `subtitula-processing-dev`. Como `workers.dev` está
en la Public Suffix List, sus hostnames son sitios distintos para cookies. El
navegador llama a los gateways same-origin `/backend/*` y `/processing/*` del
frontend. Estos usan respectivamente `API_SERVICE` y `PROCESSING_SERVICE`.
El processor usa a su vez `API_SERVICE` para comandos HMAC internos. No se usa
`fetch()` público entre Workers de la misma zona porque Cloudflare lo rechaza
con error 1042. Las rutas Spring siguen siendo `/ping`, `/register`, etc. y no
ganan un prefijo `/api`.

`workers.dev` es adecuado para desarrollo y una demo pre-lanzamiento, no para producción crítica. La configuración de producción y sus credenciales siguen aisladas, pero no se provisiona la base HA ni se habilita el despliegue hasta activar el dominio del anexo. Si se necesita un ensayo de promoción antes, se pueden reservar temporalmente `subtitula-web-prod` y `subtitula-api-prod` bajo el mismo `workers.dev`, sin tratarlos como lanzamiento público.

## Perfiles

| Perfil | Uso | Base de datos | Email | Cookie |
|---|---|---|---|---|
| `local` | E2E completo | PostgreSQL 17 en Docker | Mailpit en `localhost:1025` | no Secure |
| `dev` | HTTPS hospedado pre-dominio | PlanetScale PostgreSQL dev | deshabilitado de forma explícita | Secure |
| `prod` | lanzamiento con dominio propio | PlanetScale PostgreSQL prod | Cloudflare Email Service | Secure |

Spring conecta directamente por JDBC/TLS a PlanetScale. No hay D1, gateway de base de datos ni Hyperdrive en la ruta JDBC de Spring.

### Estado real de PlanetScale

La base `subtitula` ya está provisionada y facturada mediante Cloudflare:

- rama predeterminada `development`;
- PS-5 single-node, cero réplicas;
- región `gcp-europe-west1` (St. Ghislain, Bélgica);
- rol de rama `subtitula_app_dev`;
- sin rama, rol ni credenciales de producción.

PlanetScale clasifica obligatoriamente la rama predeterminada como production-capable, pero el contrato de la aplicación la reserva exclusivamente para `dev`, datos sintéticos y pruebas. El password inicial del rol se descartó y después se reseteó directamente al bootstrap protegido. El valor runtime está en Cloudflare Worker Secrets y las copias bootstrap de GitHub ya se borraron; no hay copia humana recuperable en Bitwarden salvo que se vuelva a resetear.

## Google OAuth durante la fase `workers.dev`

Google funciona localmente y en hosted dev. La misma configuración Spring lee `GOOGLE_REDIRECT_URI`: local usa el callback directo del API y hosted dev usa `/backend/login/oauth2/code/google` en el origen del frontend. El gateway elimina `/backend` al reenviar, pero conserva la cookie y el estado OAuth en el origen del navegador. El cliente de Google debe autorizar esa URI de forma exacta. El E2E real de hosted dev completó consentimiento, callback, creación de sesión y llegada autenticada a `/projects`.

## Validación de development

1. Hecho: subdominio, Workers, perfil `dev`, provider de email deshabilitado y frontend `/backend`.
2. Hecho: PlanetScale, Flyway/JDBC/TLS, gateway, CSRF, cookie/sesión, registro, logout y login hospedados.
3. Hecho: `DEPLOY_ENABLED=true`; credenciales runtime instaladas en Cloudflare y bootstrap eliminado de GitHub.
4. Hecho: registro y entrega local capturada por Mailpit.
5. Hecho en baseline: contrato real Scribe v2 sanitizado y coste/latencia
   documentados. Pendiente: smoke hosted del nuevo camiño R2/Workflow.
6. Hecho: inicio y callback de Google atraviesan el gateway same-origin; `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=true`.
7. Pendiente: habilitar pgvector en PlanetScale antes de que Flyway ejecute V17,
   crear R2/processor/Workflows y seguir
   [el runbook do piloto](transparency-pilot-runbook.md).

## Qué se pospone

- compra e incorporación del dominio;
- hosts de producción y DNS;
- onboarding del remitente de Cloudflare Email Service, SMTP token y entregabilidad;
- PlanetScale production HA y despliegue público de producción;
- publicación, branding y verificación de dominio final de Google OAuth para producción.

Todos esos pasos están agrupados en [Anexo de lanzamiento: dominio propio y correo](custom-domain-launch-annex.md). La topología seleccionada está en [Arquitectura Cloudflare-first](cloudflare-architecture.md) y los valores exactos en [Configuración de secretos](secrets-setup.md).
