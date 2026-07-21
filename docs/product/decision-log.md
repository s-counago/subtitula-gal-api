Exit code: 0
Wall time: 0.7 seconds
Output:
# Registro de decisiones e hipótesis

| Fecha | Estado | Decisión / hipótesis | Razón actual | Qué la validará o invalidará |
|---|---|---|---|---|
| 2026-07-12 | Adoptada | Un solo producto y editor para creador e institución. | Ambos necesitan el mismo objeto fundamental: medio, transcript, segmentos, subtítulos y salida. | Menos abandono y menos código duplicado; pruebas de tarea con ambos perfiles. |
| 2026-07-12 | Adoptada | La intención se elige al entrar, pero se puede cambiar en el proyecto. | El modo configura ayudas y metadatos; no debe encerrar al usuario. | Usuarios capaces de reutilizar un vídeo sin crear duplicados. |
| 2026-07-12 | Adoptada | Transparencia pública es una superficie abierta y separada del login. | La ciudadanía debe poder consultar sin convertirse en cliente de SaaS. | Tasa de éxito de búsqueda y enlaces compartidos. |
| 2026-07-12 | Adoptada | Búsqueda léxica con minuto y fuentes antes que chat semántico. | Es más auditable, barata y permite demostrar utilidad antes de añadir IA generativa. | Porcentaje de tareas resueltas; consultas sin resultado; coste por búsqueda. |
| 2026-07-12 | Adoptada | Subtitula complementa EidoLocal/Gestiona y las sedes, no las sustituye en el piloto. | Reduce riesgo comercial/técnico y respeta la fuente administrativa. | Tiempo de integración, disposición del piloto y uso desde sede. |
| 2026-07-12 | En validación | La revisión dirigida por señales reduce de forma material el tiempo humano. | Es la ventaja funcional que más puede importar a una institución. | Comparativa cronometrada contra editor lineal; cambios por hora. |
| 2026-07-12 | En validación | Gallego + transparencia audiovisual puede ser una cuña comercial suficiente. | Es específico y defendible localmente, pero el mercado es limitado. | Conversaciones con compradores, volumen procesable y tasa de conversión de pilotos. |
| 2026-07-12 | En validación | Una diputación puede adquirir para varios concellos. | Centralizaría compra/soporte para municipios con poca capacidad. | Entrevistas y licitaciones previas; necesidad de marca blanca y soporte. |
| 2026-07-12 | Rechazada por ahora | Construir un portal generalista de presupuestos, contratos y expedientes. | Duplica infraestructura existente y dispersa la tesis. | Solo reconsiderar si sesiones/documentos demuestran una necesidad clara y acceso fiable a datos. |
| 2026-07-12 | Rechazada por ahora | Un asistente que responda sin mostrar evidencia. | Riesgo de error, sesgo y falta de confianza pública. | Nunca lanzar sin evaluación, citas por fragmento y controles de seguridad. |
| 2026-07-13 | Adoptada | Desarrollo y producción son entornos aislados, no solo ramas. | Evita probar sobre datos, credenciales o presupuesto reales; permite medir costes por entorno. | Dos bases, dos clientes OAuth, dos claves de transcripción y dos GitHub Environments configurados. |
| 2026-07-18 | Adoptada | Arquitectura Cloudflare-first: frontend en Workers, Spring en Containers, Email Service y futuro R2. | Reduce proveedores y factura sin reescribir el API actual; Containers puede ejecutar su imagen OCI. | Despliegue remoto de dev, medidas de cold start/memoria y E2E con URLs estables. |
| 2026-07-18 | Revertida | D1 sería la única base hospedada y PostgreSQL quedaría local. | D1 exigiría sustituir JPA/JDBC/Flyway/Spring Session y mantener una persistencia distinta de local; el coste de desarrollo y depuración supera la simplificación de proveedor. | Solo reconsiderar si el backend abandona Spring/JDBC o una carga futura vive enteramente en Workers. |
| 2026-07-18 | Adoptada | PlanetScale PostgreSQL, provisionado y facturado mediante Cloudflare, será la base hospedada. | Mantiene PostgreSQL estándar y el mismo código JPA/JDBC/Flyway/Spring Session en los tres entornos; dev y prod quedan aislados. PlanetScale opera el servicio aunque aparezca en la factura Cloudflare. | Ramas/roles separados, Flyway, restauración, latencia regional y E2E remoto validados. |
| 2026-07-18 | Adoptada | La API se construye una vez y producción promueve el mismo digest probado en dev. | Evita diferencias de código o empaquetado entre entornos; solo cambian perfiles y secretos runtime. | CI registra digest, E2E dev lo aprueba y el despliegue prod referencia exactamente ese digest. |
| 2026-07-18 | Adoptada | Cloudflare Email Service será el único correo hospedado; Mailpit seguirá local. | Se prefiere una propuesta clara y fail-fast a mantener fallbacks prematuros. | SPF/DKIM/DMARC, entregabilidad, cuotas y Activity log validados al activar el anexo de lanzamiento. |
| 2026-07-18 | Adoptada | Hosted dev usará los hostnames gratuitos `workers.dev`; dominio propio, correo hospedado y producción pública forman un anexo de lanzamiento. | Evita pagar el dominio antes de validar el producto sin introducir otro proveedor ni código por entorno. | E2E dev estable; al lanzar, Custom Domains y OAuth/email funcionan cambiando configuración, no código. |

## North-star y métricas de salud

**North-star propuesta:** número de sesiones públicas en las que una persona puede llegar de una búsqueda a un minuto verificable y un documento relacionado.

| Área | Métricas de salud |
|---|---|
| Operación | tiempo de subida a borrador, tiempo de revisión por hora, porcentaje de publicación, coste por minuto publicado |
| Calidad | cambios por 1.000 palabras, errores de hablante/nombre en muestra, segmentos dudosos resueltos |
| Utilidad ciudadana | éxito de tarea, clic de resultado a minuto, búsquedas sin resultado, retorno y enlaces compartidos |
| Confianza | porcentaje de sesiones con fuente original y documentos, incidencias/correcciones, tiempo de rectificación |
| Negocio responsable | renovación de piloto, coste de soporte por entidad, margen por sesión sin recortar revisión necesaria |

No usar visitas al portal como única métrica de éxito: la transparencia se consulta a menudo por necesidad puntual. La medida relevante es si, cuando surge esa necesidad, la respuesta se encuentra y se puede comprobar.
