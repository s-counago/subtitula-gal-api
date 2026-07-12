# Infraestructura y entornos

**Estado:** preparado en código, sin proveedor de hosting ni secretos configurados · **Actualizado:** 13 de julio de 2026.

## Regla de base

`develop` es desarrollo; `master` (frontend) y `main` (API) son producción. Una PR abierta nunca publica producción: al aprobarse y fusionarse, el `push` resultante a la rama de producción será el disparador de despliegue cuando el proveedor esté configurado.

Desarrollo y producción deben tener datos, credenciales y límites de gasto independientes. Compartir una clave de transcripción, una base de datos o un cliente OAuth elimina la utilidad del entorno de desarrollo.

## Inventario de servicios

| Servicio | Desarrollo | Producción | Estado y siguiente paso |
|---|---|---|---|
| GitHub | `develop`, CI y GitHub Environment `development` | `master`/`main`, CI y Environment `production` | Workflows preparados y despliegue bloqueado hasta elegir proveedor. Crear los dos Environments en cada repositorio. |
| Next.js | `NEXT_PUBLIC_API_URL=http(s)://api.dev…` o localhost | `NEXT_PUBLIC_API_URL=https://api.subtitula.gal` | Plantilla preparada. Esta variable es pública: nunca alojar secretos en `NEXT_PUBLIC_*`. |
| API Spring | Perfil `dev`, `CORS_ORIGINS`/`FRONTEND_URL` de dev | Perfil `prod`, cookie Secure y origen/remitente obligatorios | Perfiles y plantilla `.env.example` preparados. Falta host de cómputo. |
| PostgreSQL | Instancia/base aislada con datos de prueba | Instancia/base aislada con backup y restauración probada | Falta seleccionar/provisionar proveedor. Nunca copiar producción a dev. |
| Google OAuth | Cliente OAuth **web** de dev y usuarios de prueba | Cliente OAuth **web** de producción | Falta acceso a consola y dominios estables. Son `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET`, no una API key. |
| ElevenLabs Scribe | Clave distinta con cuota dura | Clave de servicio con alertas y coste por archivo | Código actualizado a `scribe_v2`; falta crear clave dev y validar corpus gallego. |
| Amazon SES | Sandbox/allow-list de correos propios | Dominio, DKIM/SPF/DMARC y acceso de producción | Falta verificar identidades y crear IAM con mínimo privilegio. |
| Cloudflare | Futuro `app.dev.subtitula.gal` + `api.dev.subtitula.gal` | Futuro `app.subtitula.gal` + `api.subtitula.gal` | Sin cambios en Cloudflare todavía. Cuando toque: DNS y túnel nombrado, no Quick Tunnel para OAuth. |
| Observabilidad, backups y archivos | Límites y limpieza de datos de prueba | Retención, alertas, backups y borrado acordados | Pendiente de elegir hosting/almacenamiento. No registrar vídeos, tokens o transcripciones completas en logs. |

## Datos y variables a preparar

No enviar estas claves por chat ni guardarlas en el repositorio. La API incluye [.env.example](../../.env.example), que se copia localmente a `.env` y queda ignorado por Git.

| Grupo | Variables | Lugar futuro |
|---|---|---|
| Base de datos | `DB_URL`, `DB_USER`, `DB_PASSWORD` | Secretos `development` y `production` del host/API |
| OAuth | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Secretos de la API por entorno |
| Frontend/API | `NEXT_PUBLIC_API_URL`, `CORS_ORIGINS`, `FRONTEND_URL` | Variable pública del frontend; variables de configuración de API |
| Transcripción | `ELEVENLABS_API_KEY`, `ELEVENLABS_MODEL_ID=scribe_v2`, `ELEVENLABS_LANGUAGE_HINT=glg` | Secreto de la API por entorno |
| Correo | `APP_EMAIL_FROM`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | Secreto de la API por entorno |
| Despliegue | `DEPLOY_ENABLED=false` | Variable de GitHub Environment; solo cambiar tras implementar y revisar el proveedor |

## Orden de activación

1. Crear los GitHub Environments `development` y `production` en ambos repositorios, con `DEPLOY_ENABLED=false`.
2. Elegir hosting para la API Spring y PostgreSQL; Cloudflare quedará como DNS/borde/túnel, no sustituye ese cómputo.
3. Crear y desplegar la base de desarrollo; probar Flyway y crear datos de prueba.
4. Registrar dos clientes OAuth de Google con callbacks finales. Localmente puede usarse `http://localhost:8080/login/oauth2/code/google`; remoto necesita HTTPS y coincidencia exacta.
5. Crear la clave de ElevenLabs de desarrollo con cuota; ejecutar la prueba de gallego contra `scribe_v2`.
6. Configurar SES de desarrollo; después el dominio de producción y autenticación DNS.
7. Cuando exista hosting, configurar Cloudflare DNS/túneles nombrados y las variables de URL. Solo entonces implementar los comandos de despliegue y activar `DEPLOY_ENABLED`.

## Guardarraíles de coste y datos

- Un límite de consumo de ElevenLabs por clave y una métrica de coste/minuto dentro de Subtitula.
- Una base de datos de dev distinta y sin datos personales o institucionales reales.
- SES dev restringido a una allow-list de propiedad del equipo.
- Presupuesto/alerta de gasto en cada proveedor antes de permitir carga pública de vídeo.
- Backups y proceso de borrado definido antes del primer piloto institucional.
