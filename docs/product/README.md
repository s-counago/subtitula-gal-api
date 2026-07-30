# Subtitula — centro de producto y mercado

**Estado:** borrador operativo · **Actualizado:** 30 de julio de 2026

Este directorio convierte la conversación de estrategia en un artefacto que se puede mantener. No sustituye a Linear o GitHub Issues cuando haya equipo: por ahora sirve de fuente de verdad legible y de briefing para diseño y desarrollo.

## La decisión de producto, en una frase

Subtitula no debe convertirse en un portal de cumplimiento documental. Debe ser la infraestructura para **publicar, revisar y encontrar lo que se dijo en el audiovisual público**, conservando el vídeo y el documento oficial como evidencia.

La entrada comercial es la accesibilidad y el ahorro operativo; el valor público es poder ir de una pregunta al minuto exacto, a las personas que hablaron y a los documentos asociados.

## Contenido

- [Plan canónico de evidencia, enriquecimiento y búsqueda](transparency-evidence-search-implementation-plan.md): implementación completa y por fases de vídeo durable, revisión humana mínima, guía estructurada, publicación y búsqueda híbrida. Toda sesión futura que trabaje en este flujo debe empezar aquí.
- [Contrato API de transparencia v1](transparency-api-contract-v1.md): formas HTTP, precondiciones y DTOs públicos/internos.
- [Contrato UX de transparencia](transparency-ux-acceptance.md): escenarios de tarea, textos gallegos y accesibilidad.
- [Línea base Scribe v2](scribe-v2-provider-baseline.md): fixture público sanitizado, métricas y comportamiento observado.
- [Refactor de interfaz](product-refactor.md): arquitectura, pantallas y modelo de datos para los tres flujos.
- [Backlog priorizado](backlog.md): trabajo de producto, tecnología, investigación y validación.
- [Mercado y competidores](market-watch.md): proveedores, productos análogos, precios públicos y huecos por investigar.
- [Registro de decisiones](decision-log.md): hipótesis que ya orientan el producto y las que aún necesitan prueba.
- [Infraestructura y entornos](infrastructure.md): servicios, secretos, costes y orden de activación para dev/prod.
- [Contrato operativo de entornos](../operations/environment-contract.md): ramas, GitHub Environments, URLs y requisitos para desplegar.
- [Arquitectura Cloudflare-first](../operations/cloudflare-architecture.md): cómputo, base de datos, correo, almacenamiento y orden de migración.
- [Configuración de secretos](../operations/secrets-setup.md): runbook paso a paso para obtener, custodiar, instalar y rotar cada valor de local, dev y producción.
- [Runbook del piloto de transparencia](../operations/transparency-pilot-runbook.md): prerrequisitos de pgvector/R2, rollout por capacidades, métricas, retención, restore y rollback.
- [Anexo de lanzamiento](../operations/custom-domain-launch-annex.md): dominio propio, correo hospedado, Google público y producción cuando el piloto lo justifique.
- [Tablero operativo](dashboard.html): vista navegable de tareas, investigación, mercado, decisiones y documentos.
- [Instantánea de datos del tablero](dashboard-data.json): fichero intercambiable para conservar cambios entre ordenadores.
- [Fuente editable del tablero](dashboard.fragment.html): fragmento del que se genera el tablero estático.

## Cadencia mínima

1. Al empezar una semana, mover una sola iniciativa a En curso en el backlog.
2. Al descubrir un dato comercial, añadir fuente, fecha y nivel de certeza al mapa de mercado.
3. Cuando una entrevista, piloto o métrica cambie una hipótesis, actualizar el registro de decisiones; no reescribirla sin dejar rastro.
4. Cada lanzamiento debe anotar minutos procesados, coste por minuto, porcentaje de revisión, tiempo hasta publicar y éxito de búsqueda.

## Tablero y sincronización

El tablero permite añadir tareas, preguntas de investigación, referencias de mercado e ideas; también permite cerrar tareas y preguntas. Sus cambios se guardan en el navegador mientras se trabaja.

Para llevar esos cambios a otro ordenador, exportar el JSON desde el tablero, sustituir [dashboard-data.json](dashboard-data.json) por ese fichero y hacer commit/push. En el otro ordenador, abrir el tablero e importar el JSON versionado. Un HTML estático no puede modificar el repositorio por sí mismo sin credenciales o un servicio de backend.

## Contexto de implementación

El diseño se elaboró antes de conectar este repositorio. Al iniciar el refactor real, el primer trabajo es mapear sus rutas, componentes y modelo de datos al contrato de [product-refactor.md](product-refactor.md), no reconstruir tres productos independientes.
