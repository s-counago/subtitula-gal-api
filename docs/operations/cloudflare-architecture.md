# Arquitectura Cloudflare-first con PostgreSQL

**Estado:** base dev provisionada; manifests y CI/CD hosted implementados · **Actualizado:** 23 de julio de 2026.

La aplicación conserva PostgreSQL y la integración nativa de Spring. PlanetScale opera la base, provisionada y facturada mediante Cloudflare. El frontend vive en Workers/OpenNext y Spring en Containers. No se ejecuta PostgreSQL dentro de un Container: su disco es efímero.

## Un solo código y artefacto

El mismo código ejecuta JPA/Hibernate, Flyway PostgreSQL, Spring Session JDBC, el puerto `EmailSender`, ElevenLabs y las mismas rutas HTTP. Dev y prod activan el perfil compartido `hosted`; variables y secretos eligen recursos. Producción promociona el digest probado en dev, sin recompilar.

El modo temporal `EMAIL_PROVIDER=disabled` no entrega ni simula email: lanza un error que los endpoints registran. La misma lógica se configura por variables: dev usa `EMAIL_DELIVERY_REQUIRED=false`; producción usa `true`, lo que obliga a SMTP, remitente incorporado y password en el arranque. Local conserva SMTP contra Mailpit.

## Arquitectura por fase

| Capa | Local | Dev ahora | Producción tras anexo |
|---|---|---|---|
| Frontend | `localhost:3000` | `subtitula-web-dev.s-counago00.workers.dev` + gateway `/backend` | dominio propio en Worker/OpenNext |
| API | `localhost:8080` | `subtitula-api-dev.s-counago00.workers.dev` → Container | dominio propio → misma imagen Container |
| Datos | Docker PostgreSQL 17 | PlanetScale PostgreSQL dev | PlanetScale PostgreSQL prod HA |
| Email | Mailpit, no entrega | deshabilitado y fallo visible | Cloudflare Email Service |
| Objetos | filesystem temporal | R2 dev cuando se implemente | R2 prod |
| Secretos | `.env` ignorado | Worker Secrets dev | Worker Secrets prod |

No se copian datos, buckets ni claves entre dev y producción.

## Por qué `workers.dev`

Cloudflare incluye un subdominio por cuenta y asigna una URL HTTPS a cada Worker. Permite empezar sin zona DNS ni coste de dominio. El `name` del manifest controla el primer segmento de la URL.

Cloudflare lo clasifica como sitio gratuito para proyectos personales/no críticos y recomienda rutas o Custom Domains para producción. Por eso sirve como alojamiento de pruebas y demo, mientras la compra del dominio, el correo real y la salida pública quedan juntos en el [anexo de lanzamiento](custom-domain-launch-annex.md).

Como `workers.dev` figura en la Public Suffix List, los dos Workers no pueden compartir cookies directamente. El frontend ofrece `/backend/*` en su propio origen y reenvía al API Worker, preservando cuerpos, cookies, CSRF y respuestas. Cambiar a dominio propio no requiere una rama ni código alternativo: se añaden Custom Domains y se sustituyen URLs/configuración OAuth. `NEXT_PUBLIC_SITE_URL` evita hardcodear el host.

## PostgreSQL y PlanetScale

Spring conecta directamente mediante JDBC/TLS al PostgreSQL estándar de PlanetScale. Se mantienen pgJDBC, Hikari, JPA, Flyway, sesiones JDBC, SQL PostgreSQL, `pg_dump`/`pg_restore` y Testcontainers.

- dev: base `subtitula`, rama predeterminada `development`, PS-5 single-node en `gcp-europe-west1`, datos sintéticos y rol `subtitula_app_dev`;
- prod: rama/base `production`, HA mínimo, rol y backups propios; no existe y se crea al activar el anexo;
- Hikari empieza con pool pequeño; PgBouncer se evaluará después de probar el Container real.

Hyperdrive no se usa: su connection string es un binding del runtime Workers, no un endpoint JDBC para la JVM del Container.

PlanetScale mantiene la rama predeterminada marcada técnicamente como production-capable. El nombre `development`, las credenciales y el contrato de despliegue son los que determinan su uso real. No se añade otra rama permanente hasta el lanzamiento para evitar pagar un segundo cluster antes de necesitar producción.

## Correo y archivos

`workers.dev` no es un dominio del proyecto y no puede incorporarse como remitente. Cloudflare Email Service exige que el remitente pertenezca a un dominio incorporado. Por eso:

- local valida registro, verificación y reset completos en Mailpit;
- dev remoto funciona sin salida de correo y deja el fallo en logs;
- el dominio propio activa Cloudflare Email Service en prod, sin AWS/SES/Mailtrap de respaldo.

Los Containers no almacenan datos durables. Vídeos, fuentes y exportaciones pertenecerán a R2; PostgreSQL guarda metadatos, transcripciones y referencias.

## Coste inicial

Estimación antes de IVA:

| Fase | Coste base aproximado |
|---|---:|
| Local | USD 0 de infraestructura; consumo eléctrico propio |
| Hosted dev pre-dominio | USD 10/mes: Workers Paid 5 + PlanetScale dev 5 |
| Producción tras anexo | USD 25/mes: Workers Paid 5 + DB dev 5 + DB prod HA 15, más dominio |

El hostname `workers.dev`, Google OAuth, Mailpit y la clave ElevenLabs Free no añaden cuota fija. Containers de poco tráfico pueden caber en la asignación del plan; overages, R2, ElevenLabs adicional e IVA no están incluidos.

## Referencias oficiales

- [Cloudflare Workers: `workers.dev`](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/)
- [Cloudflare Workers: Custom Domains](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/)
- [PlanetScale provisionado mediante Cloudflare](https://developers.cloudflare.com/hyperdrive/planetscale/)
- [PlanetScale Postgres: precios](https://planetscale.com/docs/postgres/pricing)
- [Cloudflare Containers: arquitectura](https://developers.cloudflare.com/containers/platform-details/architecture/)
- [Cloudflare Containers: precios](https://developers.cloudflare.com/containers/pricing/)
- [Cloudflare Email Service: dominios](https://developers.cloudflare.com/email-service/configuration/domains/)
- [Cloudflare Email Service: send bindings](https://developers.cloudflare.com/email-service/configuration/send-bindings/)
