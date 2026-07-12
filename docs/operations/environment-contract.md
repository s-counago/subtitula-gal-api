# Contrato operativo de entornos

Este documento traduce la estrategia de ramas a un despliegue seguro. No contiene secretos y no habilita ningún proveedor.

## Flujo de Git

```text
feature/* ──PR──> develop ──push──> development
                    │
                    └──PR aprobada──> master (frontend) / main (API) ──push──> production
```

Una PR contra la rama de producción solo valida código. El despliegue de producción ocurre tras la fusión, con el `push` que crea GitHub al merge.

Los workflows incluidos hacen CI y anotan el Environment correspondiente, pero no despliegan. Mientras `DEPLOY_ENABLED` no sea exactamente `true`, terminan correctamente sin tocar servicios externos. Si se activa antes de implementar un proveedor, fallan explícitamente.

## Configuración futura de GitHub

En **cada** repositorio crear:

- Environment `development`, restringido a la rama `develop`.
- Environment `production`, restringido a `master` en el frontend y `main` en el API; requerir aprobación antes de despliegue cuando el plan de GitHub lo permita.
- Variable `DEPLOY_ENABLED=false` en ambos.

Los secretos se guardan por Environment, no como secretos globales de repositorio. Así una acción de desarrollo nunca puede leer credenciales de producción.

## Contrato de URLs

| Entorno | Frontend | API | Callback OAuth de Google |
|---|---|---|---|
| Local | `http://localhost:3000` | `http://localhost:8080` | `http://localhost:8080/login/oauth2/code/google` |
| Desarrollo remoto | `https://app.dev.subtitula.gal` | `https://api.dev.subtitula.gal` | `https://api.dev.subtitula.gal/login/oauth2/code/google` |
| Producción | `https://app.subtitula.gal` | `https://api.subtitula.gal` | `https://api.subtitula.gal/login/oauth2/code/google` |

Los nombres de desarrollo son la propuesta, no se han creado en DNS. Si se eligen otros, cambiar simultáneamente `NEXT_PUBLIC_API_URL`, `CORS_ORIGINS`, `FRONTEND_URL` y el redirect URI del cliente OAuth correspondiente.

## Antes de activar cualquier despliegue

1. El host de la API debe ejecutar con `SPRING_PROFILES_ACTIVE=prod` y secretos de su propio Environment.
2. El frontend debe construirse con la URL pública de la API de su entorno.
3. La API debe tener CORS limitado a la URL exacta del frontend y cookie Secure en producción.
4. Hay dos clientes OAuth de Google y dos bases de datos distintos.
5. La ruta de salida tras Google es `/projects`, consistente con el selector de flujos.
6. Existen límites de ElevenLabs y alertas/cortafuegos de gasto.
7. Se ha probado registro, Google Login, carga, transcripción y cierre de sesión desde una URL remota de dev.

## Decisiones pendientes que bloquean implementación de despliegue

- Proveedor de cómputo para Spring Boot y proveedor de PostgreSQL.
- Quién administra la zona `subtitula.gal` en Cloudflare.
- Retención y ubicación de archivos de vídeo/font/transcript para el piloto.
- Cuenta/proyecto de Google Cloud y cuentas de ElevenLabs/AWS que serán titulares de las credenciales de producción.
