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
7. Crear el cliente OAuth production con callback `https://api.<domain>/login/oauth2/code/google`; completar branding, privacidad, términos y verificación de dominio que Google solicite.
8. Construir frontend con sus dos URLs públicas y desplegar el mismo digest API ya validado en dev.
9. Ejecutar E2E remoto: cookies/CORS, password, Google, verificación/reset por email, transcripción, logout y rollback.
10. Antes de abrir registro público, decidir y aplicar en la API qué acciones requieren `emailVerified=true`; el campo no bloquea funciones por sí solo.
11. Activar revisión humana de producción y después `DEPLOY_ENABLED=true`. No borrar rutas `workers.dev` hasta confirmar tráfico, callbacks y rollback.

## Criterio de salida

El anexo solo está completo cuando el correo autentica correctamente, Google acepta el callback/branding, producción usa credenciales y datos aislados, y el digest desplegado coincide con el aprobado en dev.
