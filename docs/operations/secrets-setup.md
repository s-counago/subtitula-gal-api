# Guía paso a paso de configuración y secretos

**Estado:** runbook para local y hosted dev pre-dominio · **Actualizado:** 18 de julio de 2026.

Esta es la fuente de verdad para obtener y colocar cada valor. El dominio propio, SMTP hospedado y producción pública están pospuestos al [anexo de lanzamiento](custom-domain-launch-annex.md).

Nunca pegues secretos reales en Git, documentación, incidencias, logs, `NEXT_PUBLIC_*` ni chats. Usa un gestor de contraseñas con MFA/passkeys y guarda propietario, entorno, permisos, creación, rotación y revocación de cada entrada.

## 1. Qué existe ahora

| Destino | URL | Datos | Email | Coste fijo aproximado |
|---|---|---|---|---:|
| Local | `localhost` | PostgreSQL 17 Docker | Mailpit | USD 0 |
| Development | dos URLs `workers.dev` | PlanetScale PostgreSQL dev | deshabilitado; fallo visible | USD 10/mes |
| Production | no provisionado | no provisionado | no provisionado | USD 0 hasta el anexo |

Cloudflare da un hostname, no un dominio transferible. La forma es `<worker-name>.<account-subdomain>.workers.dev`. Usaremos:

```text
https://subtitula-web-dev.<account-subdomain>.workers.dev
https://subtitula-api-dev.<account-subdomain>.workers.dev
```

Sustituye `<account-subdomain>` por el valor real en todos los pasos.

## 2. Dónde vive cada tipo de dato

| Tipo | Custodia |
|---|---|
| Secretos locales | `subtitula-gal-api/.env` y `subtitula-gal/.env.local`, ambos ignorados |
| Secretos runtime dev | Cloudflare Worker Secrets/Secret Store del environment `development` |
| Variables runtime dev | bloque versionado `env.development.vars` de Wrangler |
| Token de despliegue | GitHub Environment Secret `CLOUDFLARE_API_TOKEN` |
| Variables de CI/build | GitHub Environment Variables |
| Copia de recuperación | gestor de contraseñas |

GitHub no necesita recibir secretos de DB, Google, ElevenLabs o correo. El Worker los pasa al Container como variables; nunca entran en la imagen OCI.

## 3. Inventario actual

### API local

| Nombre | Secreto | Valor/origen |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | no | `local` |
| `DB_*` | local | valores de `.env.example`/Compose |
| `GOOGLE_CLIENT_ID` | no | cliente Web local de Google |
| `GOOGLE_CLIENT_SECRET` | sí | secreto del cliente Web local |
| `ELEVENLABS_API_KEY` | sí | clave local limitada a STT |
| `APP_EMAIL_FROM` | no | `no-reply@subtitula.local` |
| `SMTP_HOST` / `SMTP_PORT` | no | `localhost` / `1025` |

Mailpit no usa usuario, contraseña ni API key.

### API development

| Nombre | Secreto | Valor/origen | Custodia |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | no | `dev` | Wrangler var |
| `EMAIL_PROVIDER` | no | `disabled` | Wrangler var |
| `EMAIL_DELIVERY_REQUIRED` | no | `false` | Wrangler var |
| `DB_URL` | sí por prudencia | JDBC/TLS PlanetScale dev | Worker Secret |
| `DB_USER` | sí por prudencia | rol PlanetScale dev | Worker Secret |
| `DB_PASSWORD` | sí | password PlanetScale dev | Worker Secret |
| `DB_POOL_MAXIMUM_SIZE` | no | `5` inicialmente | Wrangler var |
| `DB_POOL_MINIMUM_IDLE` | no | `0` | Wrangler var |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | no | `10000` | Wrangler var |
| `GOOGLE_CLIENT_ID` | no | cliente Web dev, si Google acepta el callback | Wrangler var |
| `GOOGLE_CLIENT_SECRET` | sí | secreto cliente dev | Worker Secret |
| `ELEVENLABS_API_KEY` | sí | clave dev limitada | Worker Secret |
| `ELEVENLABS_MODEL_ID` | no | `scribe_v2` | Wrangler var |
| `ELEVENLABS_LANGUAGE_HINT` | no | `glg` | Wrangler var |
| `CORS_ORIGINS` | no | URL exacta del frontend dev | Wrangler var |
| `FRONTEND_URL` | no | URL exacta del frontend dev | Wrangler var |

No configures `APP_EMAIL_FROM`, `SMTP_HOST`, `SMTP_USERNAME` ni `SMTP_PASSWORD` en development mientras no exista dominio. `workers.dev` no pertenece al proyecto y no es un remitente válido.

### Frontend

| Nombre | Local | Development |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | `https://subtitula-api-dev.<account-subdomain>.workers.dev` |
| `NEXT_PUBLIC_SITE_URL` | `http://localhost:3000` | `https://subtitula-web-dev.<account-subdomain>.workers.dev` |
| `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED` | `true` | `false` |

Son valores públicos incluidos en el bundle. Nunca pongas secretos en variables `NEXT_PUBLIC_*`.

### CI/CD development

| Nombre | Secreto | Ubicación |
|---|---|---|
| `CLOUDFLARE_ACCOUNT_ID` | no | GitHub Environment Variable |
| `CLOUDFLARE_API_TOKEN` | sí | GitHub Environment Secret, distinto por repo |
| `DEPLOY_ENABLED` | no | GitHub Environment Variable; `false` hasta implementar manifests |
| las dos `NEXT_PUBLIC_*` | no | GitHub Environment Variables del frontend |

## 4. Cloudflare: cuenta, hostname y tokens

### 4.1 Crear la cuenta y elegir hostname

1. Entra en [Cloudflare Dashboard](https://dash.cloudflare.com) y activa MFA/passkey.
2. Abre **Workers & Pages**. Si la cuenta aún no tiene subdominio, Cloudflare pedirá elegir `<account-subdomain>.workers.dev`.
3. Elige un nombre neutro de cuenta, no una credencial ni un dato personal. Cambiarlo después cambia todas las URLs.
4. Reserva los nombres Worker `subtitula-web-dev` y `subtitula-api-dev` al crear sus manifests/despliegues.
5. Copia el **Account ID** desde la portada de cuenta. Es identificador, no secreto.
6. Activa Workers Paid antes de usar Containers. No añadas una zona ni compres dominio para esta fase.

La URL sale automáticamente del `name` del Worker. Mantén `workers_dev=true` o no declares rutas custom durante la fase dev.

### 4.2 Crear tokens de deployment dev

Crea dos tokens independientes:

- `subtitula-frontend-development-deploy`;
- `subtitula-api-development-deploy`.

Para cada uno:

1. **Account API Tokens** → **Create Token**.
2. Parte de **Edit Cloudflare Workers**.
3. Limita **Account Resources** a la cuenta de Subtitula.
4. No concedas DNS/Zone Edit, Email Sending, R2 Admin ni acceso global: esta fase no los necesita.
5. Copia el token una sola vez al gestor de contraseñas.
6. Instálalo únicamente en el GitHub Environment `development` del repo correspondiente.

No uses la Global API Key. Los tokens de producción se crearán más tarde, no anticipadamente.

## 5. PlanetScale PostgreSQL dev mediante Cloudflare

1. En Cloudflare Dashboard abre la integración **PlanetScale Postgres & MySQL**.
2. Crea PostgreSQL en una región europea próxima a Containers y registra la región exacta.
3. Crea proyecto/base `subtitula` y solo la rama/cluster `development`, single-node, sin datos reales.
4. En PlanetScale, **Connect** o **Settings → Roles → New role**.
5. Crea `subtitula_app_dev`; no uses el rol `postgres` de la plataforma.
6. Copia host, database, username y password una vez al gestor.
7. Construye `DB_URL` con los valores exactos de Connect:

```text
jdbc:postgresql://<HOST>:5432/<DATABASE>?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory
```

Usamos conexión directa 5432 inicialmente. Flyway corre dentro de la misma imagen, así que el rol necesita permisos para las migraciones actuales. Probar el certificado y hostname desde la imagen Linux/Java real antes de activar CI.

## 6. Google OAuth

### 6.1 Local: funciona sin dominio

1. En [Google Cloud Console](https://console.cloud.google.com), crea `subtitula-nonprod`.
2. **Google Auth Platform → Branding/Audience/Data Access**: External, modo Testing, scopes `openid`, `email`, `profile`; añade solo tus cuentas de prueba.
3. **Clients → Create Client → Web application**.
4. Nombre `subtitula-local`.
5. Authorized Redirect URI exacta:

```text
http://localhost:8080/login/oauth2/code/google
```

6. Guarda `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` en el gestor y después en `.env` local.

### 6.2 Hosted dev: validación explícita

1. En el mismo proyecto nonprod crea otro Web client, `subtitula-development`.
2. Añade exactamente:

```text
https://subtitula-api-dev.<account-subdomain>.workers.dev/login/oauth2/code/google
```

3. Mantén Audience en Testing y añade únicamente cuentas de prueba.
4. Instala el ID como Wrangler var y el secret como Worker Secret.
5. Prueba login desde la URL real. Google exige HTTPS, coincidencia exacta y puede exigir propiedad de dominio para branding/publicación. No podemos verificar `workers.dev`; si la consola rechaza el dominio, marca Google OAuth remoto como pospuesto y usa password auth en dev. No inventes un redirect proxy.

El cliente production, branding público y verificación de dominio pertenecen al anexo.

## 7. ElevenLabs

1. En ElevenLabs abre **Profile/Workspace settings → API Keys**.
2. Crea `subtitula-local` y `subtitula-development` por separado.
3. Restringe ambas a **Speech to Text**.
4. Limita créditos/cuota; usa Free mientras alcance y prueba con clips cortos.
5. Guarda cada valor una sola vez. Local va a `.env`; dev va a Worker Secret.

No crees la clave production aún. No uses allowlist IP hasta conocer el egress real de Containers.

## 8. Configuración local completa

Desde `subtitula-gal-api`:

```powershell
Copy-Item .env.example .env
```

Edita el archivo ignorado y añade:

```dotenv
GOOGLE_CLIENT_ID=<client-id-local>
GOOGLE_CLIENT_SECRET=<client-secret-local>
ELEVENLABS_API_KEY=<key-local>
```

Desde `subtitula-gal`:

```powershell
Copy-Item .env.local.example .env.local
```

La plantilla ya contiene las dos URLs localhost. Inicia desde el frontend:

```powershell
.\start-up.ps1
```

Verifica `http://localhost:3000`, `/ping`, password auth, Google, email de verificación/reset en `http://localhost:8025`, una transcripción corta y logout. Detén con `.\start-up.ps1 stop`.

## 9. Configuración de Cloudflare development

Este paso se ejecutará cuando existan los manifests Wrangler y el Worker que controla el Container.

### Variables API versionadas

```text
SPRING_PROFILES_ACTIVE=dev
EMAIL_PROVIDER=disabled
EMAIL_DELIVERY_REQUIRED=false
DB_POOL_MAXIMUM_SIZE=5
DB_POOL_MINIMUM_IDLE=0
DB_POOL_CONNECTION_TIMEOUT_MS=10000
GOOGLE_CLIENT_ID=<id-dev-si-se-usa>
ELEVENLABS_MODEL_ID=scribe_v2
ELEVENLABS_LANGUAGE_HINT=glg
CORS_ORIGINS=https://subtitula-web-dev.<account-subdomain>.workers.dev
FRONTEND_URL=https://subtitula-web-dev.<account-subdomain>.workers.dev
```

### Worker Secrets API

Tras `npx wrangler login`, Wrangler pide cada valor sin incluirlo en el comando:

```powershell
npx wrangler secret put DB_URL --env development
npx wrangler secret put DB_USER --env development
npx wrangler secret put DB_PASSWORD --env development
npx wrangler secret put GOOGLE_CLIENT_SECRET --env development
npx wrangler secret put ELEVENLABS_API_KEY --env development
npx wrangler secret list --env development
```

Omite las dos variables Google si el callback remoto quedó pospuesto. No crees `SMTP_PASSWORD`.

### Variables de build frontend

```text
NEXT_PUBLIC_API_URL=https://subtitula-api-dev.<account-subdomain>.workers.dev
NEXT_PUBLIC_SITE_URL=https://subtitula-web-dev.<account-subdomain>.workers.dev
NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false
```

Con ese flag, la demo no muestra el banner de verificación ni ofrece recuperación de contraseña por correo. El backend sigue creando la cuenta y la sesión inmediatamente; cualquier intento directo de envío falla dentro del adaptador deshabilitado, el controlador lo registra y la respuesta funcional continúa. Ninguna función de proyectos/transcripción comprueba `emailVerified`, por lo que el usuario de prueba puede utilizar la aplicación completa.

## 10. GitHub Environment `development`

En cada repo: **Settings → Environments → New environment → development**.

1. Restringe deployments a `develop`.
2. Variables en ambos: `CLOUDFLARE_ACCOUNT_ID`, `DEPLOY_ENABLED=false`.
3. Secret en cada repo: su propio `CLOUDFLARE_API_TOKEN`.
4. En frontend añade las tres variables `NEXT_PUBLIC_*` exactas, incluida `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false`.
5. No habilites deploy: los workflows siguen siendo placeholders hasta crear manifests y comandos reales.

No adoptes secrets globales como fallback. Si el plan de GitHub del repo privado no ofrece Environment Secrets, habilita un plan compatible antes de desplegar.

## 11. Verificación hosted dev

- [ ] Ambas URLs HTTPS responden y corresponden al Worker correcto.
- [ ] `/ping` funciona y Spring registra perfiles `dev, hosted`.
- [ ] La API no tiene `SMTP_PASSWORD` y arranca con `EMAIL_PROVIDER=disabled`, `EMAIL_DELIVERY_REQUIRED=false`.
- [ ] Un intento de email deja un warning explícito; nunca afirma entrega.
- [ ] La UI no muestra verificación ni recuperación por email con `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false`.
- [ ] Registro/login por contraseña, sesión, CORS, CSRF y logout funcionan en navegador real.
- [ ] Google funciona o queda documentado como pospuesto por propiedad de dominio.
- [ ] Flyway, CRUD y sesiones usan únicamente PlanetScale dev.
- [ ] ElevenLabs usa la clave/cuota dev.
- [ ] Sitemap y robots contienen `NEXT_PUBLIC_SITE_URL`, no el dominio futuro.
- [ ] Ningún secreto aparece en Git, Actions logs o bundle frontend.

## 12. Producción y correo

No crees todavía DB HA, tokens production, zona DNS, remitentes SMTP ni secretos vacíos “para reservar”. Cuando exista un dominio razonable y una fecha de lanzamiento, sigue [Anexo de lanzamiento: dominio propio y correo](custom-domain-launch-annex.md). Cloudflare Email Service seguirá siendo el único correo hospedado; no se añadirá AWS/SES/Mailtrap como fallback.

## 13. Rotación

Revisar accesos trimestralmente y rotar ante exposición o cambio de personal:

1. crear credencial nueva con alcance mínimo;
2. actualizar el entorno correspondiente;
3. desplegar/reiniciar y verificar;
4. revocar la anterior;
5. registrar fecha, responsable y resultado.

Nunca «pruebes» una filtración manteniendo la clave: revócala.

## Referencias oficiales

- [Cloudflare: `workers.dev`](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/)
- [Cloudflare: API tokens](https://developers.cloudflare.com/fundamentals/api/get-started/create-token/)
- [Cloudflare: secrets por environment](https://developers.cloudflare.com/workers/configuration/secrets/)
- [Cloudflare Containers: variables y secretos](https://developers.cloudflare.com/containers/examples/env-vars-and-secrets/)
- [PlanetScale: conexión PostgreSQL](https://planetscale.com/docs/postgres/connecting)
- [pgJDBC: SSL](https://jdbc.postgresql.org/documentation/ssl/)
- [Google: OAuth clients y redirect URIs](https://support.google.com/cloud/answer/15549257)
- [Google: Authorized Domains](https://support.google.com/cloud/answer/15549049)
- [ElevenLabs: API keys](https://elevenlabs.io/docs/overview/administration/workspaces/api-keys)
- [GitHub: Environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)

## Anexo A — activar dominio y correo en dev/prod

Este anexo se ejecuta únicamente después de comprar el dominio. Hasta entonces, local usa Mailpit y hosted dev mantiene el flujo opcional descrito arriba.

1. Comprar el dominio e incorporarlo como zona DNS de Cloudflare.
2. Añadir Custom Domains a los Workers de frontend/API y actualizar juntos `NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_API_URL`, `CORS_ORIGINS`, `FRONTEND_URL` y los callbacks Google.
3. En Cloudflare Email Service, incorporar el dominio o subdominio remitente y verificar los registros de bounce, SPF, DKIM y DMARC.
4. Crear tokens separados dev/prod con permiso mínimo `Email Sending: Edit`; no reutilizar tokens de deployment.
5. En cada API hospedada establecer:

   ```text
   EMAIL_PROVIDER=smtp
   EMAIL_DELIVERY_REQUIRED=true
   APP_EMAIL_FROM=no-reply@<sending-domain>
   SMTP_HOST=smtp.mx.cloudflare.net
   SMTP_PORT=465
   SMTP_USERNAME=api_token
   SMTP_SSL_ENABLED=true
   SMTP_STARTTLS_ENABLED=false
   SMTP_PASSWORD=<Worker Secret del entorno>
   ```

6. En cada frontend establecer `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=true` y reconstruir el mismo código fuente.
7. Probar registro, reenvío, verificación, recuperación/reset, logs de entrega y rebotes primero en dev.
8. Antes de abrir el registro público, decidir qué acciones sensibles exigen `emailVerified=true` y aplicar esa regla en la API; hoy el campo es informativo y no bloquea uso.
9. Promocionar a production el mismo digest API probado en dev y un build frontend con las URLs production.
10. Confirmar Google OAuth, cookies/CORS y rollback antes de activar tráfico público.

El checklist ampliado y el criterio de salida están en [Anexo de lanzamiento: dominio propio y correo](custom-domain-launch-annex.md).
