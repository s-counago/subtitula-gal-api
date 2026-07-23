# Guía paso a paso de configuración y secretos

**Estado:** runbook para local y hosted dev pre-dominio · **Actualizado:** 23 de julio de 2026.

Esta es la fuente de verdad para obtener y colocar cada valor. El dominio propio, SMTP hospedado y producción pública están pospuestos al [anexo de lanzamiento](custom-domain-launch-annex.md).

Nunca pegues secretos reales en Git, documentación, incidencias, logs, `NEXT_PUBLIC_*` ni chats. Usa un gestor de contraseñas con MFA/passkeys y guarda propietario, entorno, permisos, creación, rotación y revocación de cada entrada.

## 1. Qué existe ahora

| Destino | URL | Datos | Email | Coste fijo aproximado |
|---|---|---|---|---:|
| Local | `localhost` | PostgreSQL 17 Docker | Mailpit | USD 0 |
| Development | dos URLs `workers.dev` | PlanetScale PostgreSQL dev provisionado | deshabilitado; fallo visible | USD 10/mes |
| Production | no provisionado | no provisionado | no provisionado | USD 0 hasta el anexo |

Cloudflare da un hostname, no un dominio transferible. La forma es `<worker-name>.<account-subdomain>.workers.dev`. Usaremos:

```text
https://subtitula-web-dev.s-counago00.workers.dev
https://subtitula-api-dev.s-counago00.workers.dev
```

El subdominio real de esta cuenta es `s-counago00`.

## 2. Dónde vive cada tipo de dato

| Tipo | Custodia |
|---|---|
| Secretos locales | `subtitula-gal-api/.env` y `subtitula-gal/.env.local`, ambos ignorados |
| Secretos runtime dev | Cloudflare Worker Secrets/Secret Store del environment `development` |
| Variables runtime dev | bloque versionado `env.development.vars` de Wrangler |
| Token de despliegue | GitHub Environment Secret `CLOUDFLARE_API_TOKEN` |
| Variables de CI/build | GitHub Environment Variables |
| Copia de recuperación | gestor de contraseñas |

En operación normal GitHub no recibe secretos de DB, Google, ElevenLabs o correo. Para el primer despliegue, un Bitwarden Send de una sola lectura y las credenciales DB recién reseteadas pueden pasar por secretos `BOOTSTRAP_*` del GitHub Environment. El workflow los instala en Worker Secrets y se borran inmediatamente de GitHub; nunca entran en la imagen OCI.

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
| `GOOGLE_CLIENT_ID` | no | cliente Web dev, instalado pero UI pospuesta | Worker Secret |
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
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | `/backend` |
| `NEXT_PUBLIC_SITE_URL` | `http://localhost:3000` | `https://subtitula-web-dev.s-counago00.workers.dev` |
| `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED` | `true` | `false` |
| `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED` | `true` | `false` |

Son valores públicos incluidos en el bundle. Nunca pongas secretos en variables `NEXT_PUBLIC_*`.

### CI/CD development

| Nombre | Secreto | Ubicación |
|---|---|---|
| `CLOUDFLARE_ACCOUNT_ID` | no | GitHub Environment Variable |
| `CLOUDFLARE_API_TOKEN` | sí | GitHub Environment Secret, distinto por repo |
| `DEPLOY_ENABLED` | no | GitHub repository variable; el job-level `if` se evalúa antes del Environment |
| las cuatro `NEXT_PUBLIC_*` | no | GitHub Environment Variables del frontend |

## 4. Cloudflare: cuenta, hostname y tokens

### 4.1 Crear la cuenta y elegir hostname

1. Entra en [Cloudflare Dashboard](https://dash.cloudflare.com) y activa MFA/passkey.
2. Abre **Workers & Pages**. El subdominio configurado es `s-counago00.workers.dev`.
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

Estado confirmado el 23 de julio de 2026:

- base `subtitula`, facturada por Cloudflare;
- rama predeterminada `development`;
- PostgreSQL PS-5 single-node, cero réplicas;
- `gcp-europe-west1` (St. Ghislain, Bélgica);
- rol de rama `subtitula_app_dev`;
- producción no provisionada.

PlanetScale exige que la rama predeterminada sea técnicamente production-capable. Aquí eso no significa entorno `prod`: la rama está reservada por contrato para `dev`, datos sintéticos y pruebas. Crear una segunda rama aislada ahora añadiría otro cluster facturable.

El password de creación de `subtitula_app_dev` se descartó deliberadamente porque Bitwarden no estaba disponible. Para terminar las credenciales dev:

1. En PlanetScale abre `subtitula` → rama `development` → **Roles**.
2. Resetea el password de `subtitula_app_dev`.
3. Copia host, database, username y el nuevo password directamente a Bitwarden; no uses archivo temporal, chat o logs.
4. Instala `DB_URL`, `DB_USER` y `DB_PASSWORD` como Worker Secrets cuando exista el manifest del API Worker.
5. Construye `DB_URL` con los valores exactos de Connect:

```text
jdbc:postgresql://<HOST>:5432/<DATABASE>?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory
```

Usamos conexión directa 5432 inicialmente. Flyway corre dentro de la misma imagen, así que el rol dev hereda temporalmente privilegios suficientes para las migraciones actuales. Tras separar un migrador, reducir el rol de runtime. Probar el certificado y hostname desde la imagen Linux/Java real antes de activar CI.

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

### 6.2 Hosted dev: pospuesto de forma explícita

1. En el mismo proyecto nonprod crea otro Web client, `subtitula-development`.
2. Mantén Audience en Testing y añade únicamente cuentas de prueba.
3. Instala ID y secret como Worker Secrets para no repetir la entrega.
4. Construye hosted dev con `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=false`.
5. No habilites el botón hasta que el inicio y callback OAuth atraviesen el gateway same-origin `/backend` y el E2E confirme la cookie de sesión. Password auth es el flujo hosted dev soportado.

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

Los manifests Wrangler/OpenNext y los workflows ya existen.

### Variables API versionadas

```text
SPRING_PROFILES_ACTIVE=dev
EMAIL_PROVIDER=disabled
EMAIL_DELIVERY_REQUIRED=false
DB_POOL_MAXIMUM_SIZE=5
DB_POOL_MINIMUM_IDLE=0
DB_POOL_CONNECTION_TIMEOUT_MS=10000
ELEVENLABS_MODEL_ID=scribe_v2
ELEVENLABS_LANGUAGE_HINT=glg
CORS_ORIGINS=https://subtitula-web-dev.s-counago00.workers.dev
FRONTEND_URL=https://subtitula-web-dev.s-counago00.workers.dev
```

### Worker Secrets API

Tras `npx wrangler login`, Wrangler pide cada valor sin incluirlo en el comando:

```powershell
npx wrangler secret put DB_URL --env development
npx wrangler secret put DB_USER --env development
npx wrangler secret put DB_PASSWORD --env development
npx wrangler secret put GOOGLE_CLIENT_ID --env development
npx wrangler secret put GOOGLE_CLIENT_SECRET --env development
npx wrangler secret put ELEVENLABS_API_KEY --env development
npx wrangler secret list --env development
```

Aunque Google esté oculto, sus credenciales dev pueden quedar instaladas para la futura activación. No crees `SMTP_PASSWORD`.

### Variables de build frontend

```text
NEXT_PUBLIC_API_URL=/backend
NEXT_PUBLIC_SITE_URL=https://subtitula-web-dev.s-counago00.workers.dev
NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false
NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=false
```

Con ese flag, la demo no muestra el banner de verificación ni ofrece recuperación de contraseña por correo. El backend sigue creando la cuenta y la sesión inmediatamente; cualquier intento directo de envío falla dentro del adaptador deshabilitado, el controlador lo registra y la respuesta funcional continúa. Ninguna función de proyectos/transcripción comprueba `emailVerified`, por lo que el usuario de prueba puede utilizar la aplicación completa.

## 10. GitHub Environment `development`

En cada repo: **Settings → Environments → New environment → development**.

1. Restringe deployments a `develop`.
2. Variable de Environment en ambos: `CLOUDFLARE_ACCOUNT_ID`. Variable de repositorio: `DEPLOY_ENABLED=false` durante bootstrap y `true` al desplegar. GitHub no carga variables del Environment antes de evaluar el `if:` del job.
3. Secret en cada repo: su propio `CLOUDFLARE_API_TOKEN`.
4. En frontend añade las cuatro variables `NEXT_PUBLIC_*` exactas.
5. En el primer API deploy añade temporalmente `BOOTSTRAP_DB_URL`, `BOOTSTRAP_DB_USER`, `BOOTSTRAP_DB_PASSWORD` y `BOOTSTRAP_SERVICE_SECRETS_JSON` como Environment Secrets. El JSON contiene únicamente `GOOGLE_CLIENT_ID_DEV`, `GOOGLE_CLIENT_SECRET_DEV` y `ELEVENLABS_API_KEY_DEV`. Tras una ejecución correcta, bórralos; los valores runtime quedan en Cloudflare.
6. Si excepcionalmente se entrega el Send directamente a CI como `BOOTSTRAP_BITWARDEN_SEND_URL`, el workflow configura primero `https://vault.bitwarden.eu`. Descarga una versión nativa y checksum-pinned de Bitwarden CLI; no instales `@bitwarden/cli` desde npm.

No adoptes secrets globales como fallback. Si el plan de GitHub del repo privado no ofrece Environment Secrets, habilita un plan compatible antes de desplegar.

**Estado actual (23-07-2026):** el bootstrap de development terminó correctamente. Los seis valores runtime están en Cloudflare Worker Secrets; `BOOTSTRAP_DB_URL`, `BOOTSTRAP_DB_USER`, `BOOTSTRAP_DB_PASSWORD` y `BOOTSTRAP_SERVICE_SECRETS_JSON` se borraron de GitHub. En el Environment solo debe quedar `CLOUDFLARE_API_TOKEN`. No vuelvas a crear secretos `BOOTSTRAP_*` salvo una rotación deliberada.

## 11. Verificación hosted dev

- [ ] Ambas URLs HTTPS responden y corresponden al Worker correcto.
- [ ] `/ping` funciona y Spring registra perfiles `dev, hosted`.
- [ ] La API no tiene `SMTP_PASSWORD` y arranca con `EMAIL_PROVIDER=disabled`, `EMAIL_DELIVERY_REQUIRED=false`.
- [ ] Un intento de email deja un warning explícito; nunca afirma entrega.
- [ ] La UI no muestra verificación ni recuperación por email con `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false`.
- [ ] Registro/login por contraseña, sesión, CORS, CSRF y logout funcionan en navegador real.
- [ ] Google está oculto con `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=false`; local sigue funcionando.
- [ ] Flyway, CRUD y sesiones usan únicamente PlanetScale dev.
- [ ] ElevenLabs usa la clave/cuota dev.
- [ ] Sitemap y robots contienen `NEXT_PUBLIC_SITE_URL`, no el dominio futuro.
- [ ] Ningún secreto aparece en Git, Actions logs o bundle frontend.

## 12. Producción y correo

No crees todavía DB HA, tokens production, zona DNS, remitentes SMTP ni secretos vacíos “para reservar”. Cuando exista un dominio razonable y una fecha de lanzamiento, sigue [Anexo de lanzamiento: dominio propio y correo](custom-domain-launch-annex.md). Cloudflare Email Service seguirá siendo el único correo hospedado; no se añadirá AWS/SES/Mailtrap como fallback.

La tarea de lanzamiento debe crear una rama/base HA aislada `production`, su rol `subtitula_app_prod`, credenciales distintas y Worker Secrets `prod`. No se promocionan datos ni passwords de `development`; solo el mismo digest de aplicación y las migraciones Flyway versionadas.

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
