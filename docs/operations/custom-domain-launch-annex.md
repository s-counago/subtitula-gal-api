# Anexo de lanzamiento: dominio propio y correo

**No es necesario para hosted dev.** Ejecutar este anexo cuando se haya elegido/comprado el dominio y exista una fecha de lanzamiento o piloto externo.

## Resultado

- frontend y API con hosts propios estables;
- producción aislada con el mismo digest que dev;
- Cloudflare Email Service con SPF, DKIM y DMARC;
- Google OAuth listo para usuarios externos;
- `workers.dev` conservado únicamente para desarrollo o deshabilitado.

## Variables de transición

No cambia Java ni el esquema de datos. Cambian conjuntamente:

- frontend: `NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=true`;
- API: `CORS_ORIGINS`, `FRONTEND_URL`, `EMAIL_PROVIDER=smtp`, `EMAIL_DELIVERY_REQUIRED=true`, `APP_EMAIL_FROM`, `SMTP_PASSWORD`;
- Google: Authorized Redirect URI exacta;
- Cloudflare: Custom Domains/rutas y DNS.

## Checklist

1. Comprar el dominio en un registrador fiable e incorporarlo como zona de Cloudflare.
2. Elegir hosts, por ejemplo `app.<domain>` y `api.<domain>`; crear Custom Domains en los Workers correspondientes.
3. Crear/proteger una rama/base PlanetScale `production` HA separada de `development`, con rol `subtitula_app_prod`, credenciales y Worker Secrets exclusivos. No copiar datos ni passwords dev.
4. Onboardear un dominio o subdominio de envío en Cloudflare Email Service. Dejar que Cloudflare añada los registros de bounce, SPF, DKIM y DMARC.
5. Crear un API token exclusivo con `Email Sending: Edit`; instalarlo como `SMTP_PASSWORD` de production. Nunca reutilizar el token de deploy.
6. Establecer `APP_EMAIL_FROM=no-reply@<sending-domain>`, `EMAIL_PROVIDER=smtp` y `EMAIL_DELIVERY_REQUIRED=true`.
7. Crear el cliente OAuth production con callback `https://app.<domain>/backend/login/oauth2/code/google`; establecer esa misma URI en `GOOGLE_REDIRECT_URI` y completar branding, privacidad, términos y verificación de dominio que Google solicite. Inicio y callback pasan por el gateway del frontend.
8. Construir frontend con `NEXT_PUBLIC_SITE_URL=https://app.<domain>`, `NEXT_PUBLIC_API_URL=/backend`, `NEXT_PUBLIC_PROCESSING_URL=/processing` y las dos funciones de correo/Google habilitadas. Configurar sus bindings `API_SERVICE` y `PROCESSING_SERVICE` exclusivamente a los Workers production; el processor también debe usar `API_SERVICE`. Desplegar el mismo digest API ya validado en dev.
9. Ejecutar E2E remoto: cookies/CORS, password, Google, verificación/reset por email, transcripción, logout y rollback.
10. Antes de abrir registro público, decidir y aplicar en la API qué acciones requieren `emailVerified=true`; el campo no bloquea funciones por sí solo.
11. Activar revisión humana de producción y después `DEPLOY_ENABLED=true`. No borrar rutas `workers.dev` hasta confirmar tráfico, callbacks y rollback.

## Criterio de salida

El anexo solo está completo cuando el correo autentica correctamente, Google acepta el callback/branding, producción usa credenciales y datos aislados, y el digest desplegado coincide con el aprobado en dev.

## Preparación de configuración sin desplegar

Con ambos repositorios hermanos presentes y sus dependencias instaladas, copiar
`scripts/fixtures/production-input.example.json` a un archivo ignorado y sustituir
sus valores por los hosts futuros, cuenta, remitente, webhook exclusivo y digest
de la imagen aprobada en desarrollo. Este archivo solo admite datos no secretos.
Ejecutar desde el repositorio API:

```powershell
node scripts/prepare-production.mjs .wrangler/production-input.json
```

Se generan `.wrangler/production/api.json`, `processing.json`, `web.json` y
`buildEnvironment.json`. No se llama a Cloudflare ni se crean recursos. Los tres
Workers tienen nombres `*-prod`, bindings de servicio aislados, bucket
`subtitula-media-prod`, Workflows propios y namespaces de rate limit separados.
La API exige `SPRING_PROFILES_ACTIVE=prod` y correo obligatorio. El generador
rechaza imágenes por tag/Dockerfile y hosts `workers.dev`; solo acepta el digest
inmutable ya validado. Los archivos generados contienen rutas absolutas de esta
máquina: regenerarlos en el checkout de despliegue.

Las rutas, previews, Cron y las ocho capacidades se generan desactivados. Esto
permite revisar el material antes del lanzamiento, pero no constituye un entorno
production operativo. Seguir la checklist anterior para aprovisionar la base HA,
bucket, webhook, dominio y correo exclusivos; instalar Worker Secrets de producción
(`DB_URL`, `DB_USER`, `DB_PASSWORD`, OAuth, ElevenLabs, firma interna compartida
solo entre los dos Workers prod, claves R2 limitadas al bucket prod y SMTP).
La base dev, su rol y sus secretos no son entradas del generador ni se reutilizan.
No habilitar analítica sin aprobar finalidad y retención.

Después de aprobar el lanzamiento, añadir los Custom Domains a los respectivos
archivos y habilitar las capacidades que hayan pasado sus gates. El processor
necesita una URL pública de webhook en su host; frontend y processor acceden a la
API mediante service bindings. Restaurar los Cron de producción solo cuando sus
recursos y secretos estén verificados. Construir el frontend con el archivo de
variables públicas generado, conservando `/backend` y `/processing`.
El workflow GitHub de producción sigue siendo un bloqueo explícito; no hay un
despliegue automático habilitado ni se ha contratado ningún recurso production.

El Worker propaga `SMTP_PASSWORD` (Worker Secret) y `APP_EMAIL_FROM` al contenedor.
El perfil hosted compartido conserva los valores SMTP documentados: host
`smtp.mx.cloudflare.net`, puerto `465`, TLS implícito y usuario `api_token`.
Referencia: [SMTP de Cloudflare Email Service](https://developers.cloudflare.com/email-service/api/send-emails/smtp/).
