# Contrato operativo de entornos

Este documento traduce la estrategia de ramas a un despliegue seguro. No contiene secretos ni habilita proveedores.

## Flujo de Git

```text
feature/* ──PR──> develop ──push──> development
                    │
                    └──PR aprobada──> master (frontend) / main (API) ──push──> production
```

Los workflows actuales hacen CI y seleccionan un GitHub Environment, pero no despliegan. Mientras `DEPLOY_ENABLED` no sea exactamente `true`, terminan sin tocar servicios externos.

## Invariante de artefacto

La API se compila una sola vez. Desarrollo valida una imagen OCI identificada por digest y producción promociona exactamente ese digest. Los tres entornos ejecutan el mismo código Spring, JPA/JDBC, Flyway y Spring Session sobre PostgreSQL 17. `SPRING_PROFILES_ACTIVE=local|dev|prod` elige configuración externa; `dev` y `prod` activan el mismo perfil `hosted`.

La única excepción temporal es de transporte de correo, elegida por variable y dentro del mismo artefacto:

- local: `EMAIL_PROVIDER=smtp`, `EMAIL_DELIVERY_REQUIRED=false`, contra Mailpit;
- dev pre-dominio: `EMAIL_PROVIDER=disabled`, `EMAIL_DELIVERY_REQUIRED=false`; cada intento falla explícitamente y queda en logs;
- prod: `EMAIL_PROVIDER=smtp`, `EMAIL_DELIVERY_REQUIRED=true`; el arranque falla si falta SMTP o un remitente incorporado.

No existe fallback hospedado.

El frontend de dev se construye con `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false`: oculta el banner/reenvío de verificación y sustituye la recuperación de contraseña por un aviso de demo. Registro y login siguen disponibles y `emailVerified=false` no bloquea ninguna función. Local y el futuro prod usan `true`.

## URLs de la fase actual

Al crear una cuenta de Workers, Cloudflare asigna `<account-subdomain>.workers.dev`. Cada Worker queda en `<worker-name>.<account-subdomain>.workers.dev`; no hace falta comprar ni incorporar una zona DNS.

| Entorno | Frontend | API | Callback OAuth de Google |
|---|---|---|---|
| Local | `http://localhost:3000` | `http://localhost:8080` | `http://localhost:8080/login/oauth2/code/google` |
| Desarrollo remoto | `https://subtitula-web-dev.<account-subdomain>.workers.dev` | `https://subtitula-api-dev.<account-subdomain>.workers.dev` | `https://subtitula-api-dev.<account-subdomain>.workers.dev/login/oauth2/code/google` |
| Producción real | Definida en el [anexo de lanzamiento](custom-domain-launch-annex.md) | Definida en el anexo | Definida en el anexo |

Los nombres de Worker propuestos son `subtitula-web-dev` y `subtitula-api-dev`. Sustituir el marcador por el subdominio real de la cuenta y cambiar juntos `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_SITE_URL`, `CORS_ORIGINS`, `FRONTEND_URL` y el redirect URI de Google.

`workers.dev` es adecuado para desarrollo y una demo pre-lanzamiento, no para producción crítica. La configuración de producción y sus credenciales siguen aisladas, pero no se provisiona la base HA ni se habilita el despliegue hasta activar el dominio del anexo. Si se necesita un ensayo de promoción antes, se pueden reservar temporalmente `subtitula-web-prod` y `subtitula-api-prod` bajo el mismo `workers.dev`, sin tratarlos como lanzamiento público.

## Perfiles

| Perfil | Uso | Base de datos | Email | Cookie |
|---|---|---|---|---|
| `local` | E2E completo | PostgreSQL 17 en Docker | Mailpit en `localhost:1025` | no Secure |
| `dev` | HTTPS hospedado pre-dominio | PlanetScale PostgreSQL dev | deshabilitado de forma explícita | Secure |
| `prod` | lanzamiento con dominio propio | PlanetScale PostgreSQL prod | Cloudflare Email Service | Secure |

Spring conecta directamente por JDBC/TLS a PlanetScale. No hay D1, gateway HTTP ni Hyperdrive en la ruta de Spring.

## Google OAuth durante la fase `workers.dev`

El callback de desarrollo es HTTPS y debe registrarse con coincidencia exacta. Mantener el proyecto OAuth no productivo en modo Testing y limitarlo a cuentas de prueba. Google puede exigir verificación de dominio para branding/publicación; como no somos propietarios de `workers.dev`, esa verificación queda para el dominio propio. La aceptación del callback y el flujo completo son un checkpoint del despliegue dev, no una suposición. Local sigue disponible si Google rechaza la configuración pre-dominio.

## Antes de activar development

1. Confirmar el subdominio `workers.dev` de la cuenta y reservar ambos nombres Worker.
2. Configurar `SPRING_PROFILES_ACTIVE=dev`, `EMAIL_PROVIDER=disabled` y `EMAIL_DELIVERY_REQUIRED=false`.
3. Construir el frontend con las URLs exactas de Worker.
4. Limitar CORS a la URL exacta del frontend y probar cookie/sesión en un navegador real.
5. Crear solo PlanetScale dev, credenciales dev y una clave ElevenLabs limitada.
6. Registrar y probar el callback Google dev; si no es aceptado, documentar Google OAuth como bloqueado hasta el anexo.
7. Probar registro, login por contraseña, carga, transcripción, sesión y logout. Probar verificación/reset de email localmente con Mailpit.
8. Mantener `DEPLOY_ENABLED=false` hasta que los manifests y comandos de despliegue existan.

## Qué se pospone

- compra e incorporación del dominio;
- hosts de producción y DNS;
- onboarding del remitente de Cloudflare Email Service, SMTP token y entregabilidad;
- PlanetScale production HA y despliegue público de producción;
- publicación/verificación final de Google OAuth.

Todos esos pasos están agrupados en [Anexo de lanzamiento: dominio propio y correo](custom-domain-launch-annex.md). La topología seleccionada está en [Arquitectura Cloudflare-first](cloudflare-architecture.md) y los valores exactos en [Configuración de secretos](secrets-setup.md).
