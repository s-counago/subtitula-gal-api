# Contrato operativo de entornos

Este documento traduce la estrategia de ramas a un despliegue seguro. No contiene secretos ni habilita proveedores.

## Flujo de Git

```text
feature/* ──PR──> develop ──push──> development
                    │
                    └──PR aprobada──> master (frontend) / main (API) ──push──> production
```

Los workflows de `develop` ejecutan CI y despliegan con Wrangler/OpenNext y Cloudflare Containers cuando `DEPLOY_ENABLED=true`. Mientras no sea exactamente `true`, no tocan servicios externos. Los workflows de producción siguen bloqueados por el anexo de lanzamiento.

## Invariante de artefacto

La API se compila una sola vez. Desarrollo valida una imagen OCI identificada por digest y producción promociona exactamente ese digest. Los tres entornos ejecutan el mismo código Spring, JPA/JDBC, Flyway y Spring Session sobre PostgreSQL 17. `SPRING_PROFILES_ACTIVE=local|dev|prod` elige configuración externa; `dev` y `prod` activan el mismo perfil `hosted`.

La única excepción temporal es de transporte de correo, elegida por variable y dentro del mismo artefacto:

- local: `EMAIL_PROVIDER=smtp`, `EMAIL_DELIVERY_REQUIRED=false`, contra Mailpit;
- dev pre-dominio: `EMAIL_PROVIDER=disabled`, `EMAIL_DELIVERY_REQUIRED=false`; cada intento falla explícitamente y queda en logs;
- prod: `EMAIL_PROVIDER=smtp`, `EMAIL_DELIVERY_REQUIRED=true`; el arranque falla si falta SMTP o un remitente incorporado.

No existe fallback hospedado.

El frontend de dev se construye con `NEXT_PUBLIC_API_URL=/backend`, `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false` y `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=false`. Oculta temporalmente Google, verificación y recuperación por correo; registro/login por contraseña y el resto de la aplicación siguen disponibles. Local y el futuro prod habilitan ambas capacidades.

## URLs de la fase actual

Al crear una cuenta de Workers, Cloudflare asigna `<account-subdomain>.workers.dev`. Cada Worker queda en `<worker-name>.<account-subdomain>.workers.dev`; no hace falta comprar ni incorporar una zona DNS.

| Entorno | Frontend | API | Callback OAuth de Google |
|---|---|---|---|
| Local | `http://localhost:3000` | `http://localhost:8080` | `http://localhost:8080/login/oauth2/code/google` |
| Desarrollo remoto | `https://subtitula-web-dev.s-counago00.workers.dev` | `https://subtitula-api-dev.s-counago00.workers.dev` | pospuesto; la UI Google está oculta |
| Producción real | Definida en el [anexo de lanzamiento](custom-domain-launch-annex.md) | Definida en el anexo | Definida en el anexo |

Los Workers son `subtitula-web-dev` y `subtitula-api-dev`. Como `workers.dev` está en la Public Suffix List, sus dos hostnames son sitios distintos para cookies. El navegador llama al gateway same-origin `/backend/*` del frontend; este elimina `/backend` y reenvía al API Worker. Las rutas Spring siguen siendo `/ping`, `/register`, etc. y no ganan un prefijo `/api`.

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

PlanetScale clasifica obligatoriamente la rama predeterminada como production-capable, pero el contrato de la aplicación la reserva exclusivamente para `dev`, datos sintéticos y pruebas. El password inicial del rol se descartó deliberadamente: antes del primer despliegue se debe resetear y guardar directamente en Bitwarden y Worker Secrets.

## Google OAuth durante la fase `workers.dev`

Google funciona localmente. En hosted dev queda explícitamente pospuesto y oculto porque el callback actual terminaría en el hostname del API, fuera de la sesión same-origin del frontend. Se habilitará cuando el callback se enrute y pruebe a través del gateway o al activar el dominio propio. El secreto dev puede permanecer instalado, pero `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=false` evita prometer un flujo incompleto.

## Antes de activar development

1. Confirmar el subdominio `workers.dev` de la cuenta y reservar ambos nombres Worker.
2. Configurar `SPRING_PROFILES_ACTIVE=dev`, `EMAIL_PROVIDER=disabled` y `EMAIL_DELIVERY_REQUIRED=false`.
3. Construir el frontend con `NEXT_PUBLIC_API_URL=/backend` y el site URL exacto.
4. Probar el gateway same-origin, cookie/sesión y CSRF en un navegador real.
5. Resetear el password de `subtitula_app_dev` directamente en Bitwarden/Worker Secrets y validar la rama `development`; no crear producción.
6. Mantener `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=false` hasta implementar/probar el callback same-origin.
7. Probar registro, login por contraseña, carga, transcripción, sesión y logout. Probar verificación/reset de email localmente con Mailpit.
8. Activar `DEPLOY_ENABLED=true` únicamente después de instalar las credenciales bootstrap; borrarlas de GitHub al terminar el primer despliegue.

## Qué se pospone

- compra e incorporación del dominio;
- hosts de producción y DNS;
- onboarding del remitente de Cloudflare Email Service, SMTP token y entregabilidad;
- PlanetScale production HA y despliegue público de producción;
- publicación/verificación final de Google OAuth.

Todos esos pasos están agrupados en [Anexo de lanzamiento: dominio propio y correo](custom-domain-launch-annex.md). La topología seleccionada está en [Arquitectura Cloudflare-first](cloudflare-architecture.md) y los valores exactos en [Configuración de secretos](secrets-setup.md).
