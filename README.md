# Órdenes de Compra a Proveedores

Aplicación web para que una cadena de ferreterías gestione sus **órdenes de compra a proveedores**: desde crearlas y aprobarlas hasta confirmar la recepción de la mercadería en cada sucursal, con notificaciones por correo, seguimiento del proveedor vía webhook, importación masiva desde Excel y exportación a PDF.

**Autor:** [Henry Molina](https://henry-molina.dev/) (portafolio). 

Proyecto de demostración completo: API, base de datos, interfaz, pruebas y despliegue en producción.

**Demo en vivo:** https://compras.henry-molina.dev/login (los usuarios de prueba están en [Usuarios de la demostración](#usuarios-de-la-demostración)).

## Contenido

- [Qué resuelve](#qué-resuelve)
- [Funcionalidades](#funcionalidades)
- [Ciclo de vida de una orden](#ciclo-de-vida-de-una-orden)
- [Reglas de negocio](#reglas-de-negocio)
- [Roles y permisos](#roles-y-permisos)
- [Integración del proveedor (webhook)](#integración-del-proveedor-webhook)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Levantar el proyecto en local](#levantar-el-proyecto-en-local)
- [Publicar las imágenes en Docker Hub](#publicar-las-imágenes-en-docker-hub)
- [Desplegar en producción](#desplegar-en-producción)
- [Respaldos de la base de datos](#respaldos-de-la-base-de-datos)
- [Documentación](#documentación)

## Qué resuelve

La cadena tiene 26 ferreterías bajo dos formatos comerciales, más una unidad de Venta Directa:

| Formato | Enfoque |
|---|---|
| **Ferretería** | Herramientas, pinturas, hogar, jardín y acabados |
| **Ferretería de Construcción** | Materiales pesados y soluciones para la construcción |
| **Venta Directa** | Clientes industriales, contratistas y grandes comercios |

Cada formato solo puede pedir ciertas categorías de producto, y la separación de funciones es explícita: **quien compra y aprueba el gasto no es quien certifica que la mercadería llegó**. La aplicación modela ese flujo completo y deja trazabilidad de cada paso.

## Funcionalidades

- **Órdenes de compra:** crear, editar (mientras están en estado *Creada*), aprobar, anular y cerrar, con listado filtrable y paginado, detalle y **línea de tiempo** que mezcla los cambios internos con los eventos del proveedor.
- **Precio negociado por proveedor:** cada proveedor define cuánto descuento se puede negociar y cuánto aumento se acepta sobre el precio de catálogo; en cada línea, un lápiz permite ajustar el precio dentro de esos límites.
- **Notificaciones por correo:** al aprobar una orden, el proveedor recibe un correo con el PDF adjunto; si se anula una orden ya aprobada, recibe el aviso de anulación.
- **Webhook de proveedores:** el sistema del proveedor reporta el avance logístico (aceptada, despachada, entregada…) con su propia API key.
- **Importación masiva desde Excel:** plantilla descargable con listas desplegables; cada lote se valida por separado y se informa qué se creó y qué se rechazó, con el motivo.
- **Exportación:** PDF de una orden y reporte en Excel con los filtros aplicados.
- **Administración** (solo ADMIN): proveedores (con sus límites de negociación y su clave de webhook, que se puede regenerar), productos, clientes, sucursales, usuarios y bitácora de auditoría por orden.
- **Tours guiados** a demanda en las pantallas principales, adaptados al rol del usuario.

## Ciclo de vida de una orden

```
CREADA ──aprobar──> APROBADA ──cerrar──> CERRADA
  │                     │
  └──────anular─────────┴──────anular───> ANULADA
```

| Estado | Qué se puede hacer | Quién |
|---|---|---|
| **Creada** | Editar el detalle, aprobar, anular | COMPRADOR, ADMIN |
| **Aprobada** | Anular (con motivo), cerrar. Se notifica al proveedor | COMPRADOR/ADMIN anulan; GERENTE_SUCURSAL de la sucursal destino (o ADMIN) cierra |
| **Cerrada** | Nada: la mercadería ya ingresó | — |
| **Anulada** | Nada: estado final | — |

Una transición inválida responde **409** con un mensaje de negocio claro, y cada transición queda registrada en la auditoría.

## Reglas de negocio

1. **Nunca se borra una orden.** Anular es solo un cambio de estado.
2. **Validación cruzada formato ↔ categoría.** Una sucursal solo puede pedir productos de las categorías permitidas para su formato (p. ej. materiales pesados no se piden a una Ferretería).
3. **Cliente obligatorio en Venta Directa,** y no aplicable en los otros dos formatos.
4. **El total lo calcula el servidor** a partir de las líneas; nunca se recibe del cliente.
5. **Precio congelado y negociable.** Cada línea guarda el precio de catálogo vigente al crearla (`precio_catalogo`) y su precio efectivo. Un precio negociado debe estar entre `catálogo × (1 − descuento máx.)` y `catálogo × (1 + aumento máx.)` **del proveedor de la orden**, extremos incluidos. Con 0 % y 0 % el proveedor no admite negociar. Los límites aplican a todos sus productos y a todos los roles, sin flujo de aprobación. Al editar una orden, una línea conserva su precio y su referencia de catálogo aunque el catálogo haya cambiado.
6. **Anular requiere motivo.** Y no se puede anular una orden cerrada.
7. **Solo se edita el detalle en estado Creada.**
8. **Segregación de funciones en el cierre.** Confirmar la recepción lo hace el gerente de la sucursal destino de esa orden (o un ADMIN); un COMPRADOR no puede, aunque vea la orden.
9. **Visibilidad por sucursal.** Un gerente solo ve las órdenes de su sucursal; intentar acceder a otra responde 403. Es una restricción a nivel de fila, no solo de rol.
10. **Una sucursal desactivada no recibe órdenes nuevas,** y su formato no se puede cambiar una vez que tiene órdenes (se desactiva y se crea otra).
11. **Los eventos del proveedor son informativos:** ninguno, ni siquiera *entregada*, cambia el estado de la orden. Toda transición es una acción explícita de un usuario interno.
12. **Autoría completada por el servidor:** quién creó o modificó cada registro sale del token del usuario autenticado, nunca del cuerpo de la petición.
13. **Idempotencia.** Crear, aprobar, anular y cerrar órdenes, y el webhook, aceptan el header `Idempotency-Key`: reintentar con la misma clave devuelve la respuesta original sin duplicar nada.

## Roles y permisos

| | ADMIN | COMPRADOR | GERENTE_SUCURSAL |
|---|:---:|:---:|:---:|
| Ver órdenes | Todas | Todas | Solo las de su sucursal (su lista es "Órdenes por recibir": las aprobadas) |
| Crear y editar órdenes (Creada) | ✅ | ✅ | — |
| Negociar el precio de una línea | ✅ | ✅ | — |
| Aprobar y anular | ✅ | ✅ | — |
| Confirmar recepción (cerrar) | ✅ | — | ✅ (su sucursal) |
| Importar órdenes desde Excel | ✅ | ✅ | — |
| Exportar PDF / Excel | ✅ | ✅ | ✅ (su sucursal) |
| Consultar catálogos (proveedores, productos, clientes) | ✅ | ✅ | — |
| Administrar catálogos, sucursales y usuarios | ✅ | — | — |
| Ver y regenerar la clave de webhook de un proveedor | ✅ | — | — |
| Bitácora de auditoría por orden | ✅ | — | — |

El **proveedor** no es un usuario: es un sistema externo que se autentica con su propia API key (ver la siguiente sección).

## Integración del proveedor (webhook)

Cada proveedor recibe una clave (la genera el sistema al crearlo; un ADMIN puede regenerarla, y la anterior deja de funcionar al instante). La clave identifica al proveedor, y la orden se indica por su **número de orden**, el mismo que el proveedor recibe en el correo de aprobación:

```bash
curl -X POST https://compras-api.<dominio>/api/webhooks/eventos \
  -H "X-Api-Key: <clave del proveedor>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: <opcional, evita duplicados en reintentos>" \
  -d '{
        "numero_orden": "OC-2026-000123",
        "tipo_evento": "DESPACHADA",
        "observacion": "Despachado en 2 camiones",
        "fecha_evento": "2026-09-24T10:30:00-06:00"
      }'
```

Tipos de evento: `ACEPTADA`, `RECHAZADA`, `PREPARADA`, `DESPACHADA`, `ENTREGADA`. Una orden de otro proveedor responde 404, igual que una inexistente. Los eventos aparecen en la línea de tiempo de la orden, y `ENTREGADA` resalta para el comprador la acción de cerrarla.

## Stack tecnológico

| Capa | Tecnologías |
|---|---|
| **Backend** | Java 25, Spring Boot 4.1, Spring Security con JWT propio (sin OAuth2), Spring Data JPA / Hibernate, Flyway, MapStruct, Lombok, Bean Validation |
| **Documentos y correo** | Apache POI (Excel), openhtmltopdf + PDFBox (PDF con la fuente Inter embebida), Spring Mail |
| **Base de datos** | PostgreSQL 16, esquema y datos semilla versionados con Flyway |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui (Radix), React Router 7, TanStack Query, React Hook Form + Zod, react-joyride (tours), Sonner |
| **Pruebas** | JUnit 5, Mockito, Testcontainers (las pruebas de integración corren contra un PostgreSQL real y efímero), Vitest + Testing Library |
| **Infraestructura** | Docker y Docker Compose, Caddy 2 (proxy inverso con HTTPS automático), Docker Hub, AWS Lightsail + Route 53 |
| **Contrato de la API** | OpenAPI 3 en `docs/openapi.yaml` (los tipos del frontend salen de ahí); la API usa `snake_case` |

## Estructura del repositorio

```
backend/    API Spring Boot (Maven) y migraciones Flyway
frontend/   Aplicación React (Vite)
compose/    Docker Compose (base + dev + prod), Caddyfile y scripts de publicación, despliegue y respaldo
docs/       Especificación técnica y contrato OpenAPI
```

## Levantar el proyecto en local

Requisitos: Docker Desktop. Para desarrollar sin contenedores: JDK 25 y Node 24.

### Con Docker (todo el sistema con HTTPS)

```bash
cd compose
cp .env.example .env    # definir POSTGRES_PASSWORD y JWT_SECRET; dejar DOMAIN=local.dev
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

Para no repetir los `-f`, agrega a `compose/.env`: `COMPOSE_FILE=docker-compose.yml;docker-compose.dev.yml` (en Linux/macOS el separador es `:`). Desde entonces basta `docker compose up -d --build`.

Antes de abrir el navegador, una sola vez:

1. **Hosts:** agrega `127.0.0.1 compras.local.dev compras-api.local.dev mail.local.dev` al archivo hosts (Windows: `C:\Windows\System32\drivers\etc\hosts`; Linux/macOS: `/etc/hosts`).
2. **Certificado:** Caddy emite certificados de su propia CA local. Expórtala con `docker compose -f docker-compose.yml -f docker-compose.dev.yml cp reverse-proxy:/data/caddy/pki/authorities/local/root.crt .` (sin los `-f`, si no configuraste `COMPOSE_FILE`, Compose falla porque el archivo base no corre solo) e instálala como raíz de confianza en tu sistema o navegador (si no, verás advertencias de certificado).

| Servicio | URL |
|---|---|
| Aplicación | https://compras.local.dev |
| API | https://compras-api.local.dev |
| Correos simulados (Mailpit) | https://mail.local.dev (también http://localhost:8025) |
| PostgreSQL | `127.0.0.1:5433` (usuario `app`, base `ordenes_compra`, clave = `POSTGRES_PASSWORD`; se cambia con `POSTGRES_HOST_PORT`) |

Las migraciones y los datos semilla se aplican solos al arrancar el backend. Para detener: `docker compose down` (con `-v` también borra la base de datos).

### Usuarios de la demostración

| Usuario | Rol | Sucursal |
|---|---|---|
| `admin` | ADMIN | — |
| `maria.gomez` | COMPRADOR | — |
| `gerente.sansalvador` | GERENTE_SUCURSAL | Ferretería San Salvador (formato Ferretería) |
| `gerente.santaana` | GERENTE_SUCURSAL | Ferretería de Construcción Santa Ana |
| `gerente.ventadirecta` | GERENTE_SUCURSAL | Venta Directa |

Los tres proveedores semilla traen límites de negociación distintos para mostrar el comportamiento: Ferretera del Norte (10 % de descuento / 5 % de aumento), Aceros y Materiales (5 % / 0 %) y Suministros Industriales (0 % / 0 %, no negocia).

### Desarrollo sin contenedores para el código

Deja solo la base de datos y el correo en Docker y corre backend y frontend desde el host:

```bash
cd compose && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres mailpit

cd ../backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ordenes_compra \
SPRING_DATASOURCE_PASSWORD=<POSTGRES_PASSWORD de compose/.env> \
mvn spring-boot:run                      # http://localhost:8080

cd ../frontend
npm install && npm run dev               # http://localhost:5173
```

El backend usa por defecto `localhost:1025` para el correo y permite el origen `http://localhost:5173`, que es justo lo que queda publicado con esa configuración. El puerto 5433 se usa para no chocar con un PostgreSQL nativo en el 5432.

### Pruebas

```bash
cd backend && mvn test        # integración con PostgreSQL real vía Testcontainers: requiere Docker activo
cd frontend && npm test       # Vitest + Testing Library
cd frontend && npm run lint && npm run build
```

Si cambias `docs/openapi.yaml`, regenera los tipos del frontend: `npx openapi-typescript ../docs/openapi.yaml -o src/api/schema.d.ts`.

## Publicar las imágenes en Docker Hub

No hay CI/CD: las imágenes se construyen y se suben con un script. Una sola vez, `docker login`. Luego, en `compose/.env` de tu máquina define:

```
DOCKERHUB_USER=<tu usuario de Docker Hub>
PROD_DOMAIN=<tu dominio de producción>     # el frontend lo incorpora al compilarse
```

```bash
cd compose
./publish.sh 1.0.1              # o sin versión: usa el SHA corto de git
./publish.sh 1.0.1 --deploy     # además ejecuta deploy.sh en el servidor por ssh
```

- Construye `<usuario>/ordenes-backend` y `<usuario>/ordenes-frontend` para `linux/amd64` y sube `:1.0.1` y `:latest`.
- Se niega a correr con cambios sin commitear, para que cada versión corresponda a un commit. Con `--allow-dirty` la permite, pero la versión queda como `1.0.1-dirty` y no actualiza `:latest`.
- **El frontend incorpora `https://compras-api.<PROD_DOMAIN>` al compilarse**, así que una imagen sirve solo para ese dominio (por eso existe `PROD_DOMAIN`, distinto del `DOMAIN=local.dev` de desarrollo).
- `--deploy` necesita `DEPLOY_HOST` y `DEPLOY_DIR` en el mismo `.env` (ver `compose/.env.example`).

## Desplegar en producción

Probado en una instancia **Amazon Lightsail** (Ubuntu 24.04, al menos 2 GB de RAM) con DNS en **Route 53**.

1. **Instancia:** créala y asígnale una **IP estática** (si no, cambia al reiniciar).
2. **Firewall** de Lightsail: TCP 22 (mejor restringido a tu IP), TCP 80, TCP 443 y **UDP 443** (HTTP/3). No abras el 5432: PostgreSQL queda enlazado a localhost y se consulta por túnel SSH.
3. **DNS:** registros A para `compras.<dominio>` y `compras-api.<dominio>` (o un comodín `*.<dominio>`) hacia la IP. Deben resolver antes del primer arranque: Caddy los necesita para obtener los certificados de Let's Encrypt.
4. **Docker** en el servidor: `curl -fsSL https://get.docker.com | sh` y `sudo usermod -aG docker ubuntu` (vuelve a iniciar sesión).
5. **Copia la carpeta `compose/`** al servidor (`scp -r compose ubuntu@<ip>:~/compose`).
6. **Configura `compose/.env` en el servidor** a partir de `.env.example`: `POSTGRES_PASSWORD` y `JWT_SECRET` fuertes (`openssl rand -base64 48`), `DOMAIN` con el dominio real, `DOCKERHUB_USER` y las credenciales `SMTP_*` de un proveedor real (puerto 587). **No** agregues la línea `COMPOSE_FILE` de desarrollo. Si tus repositorios de Docker Hub son privados, haz `docker login` también en el servidor.
7. **Despliega:**

```bash
cd ~/compose
./deploy.sh 1.0.1
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f reverse-proxy   # verifica los certificados
```

`deploy.sh` fija `IMAGE_TAG` en el `.env`, descarga las imágenes y levanta los servicios con los archivos base y de producción; Flyway aplica las migraciones al arrancar el backend. **Rollback:** vuelve a correrlo con la versión anterior (el script la imprime al terminar). Despliega siempre una versión fija, no `latest`.

Verificación: `https://compras.<dominio>` carga la aplicación y `https://compras-api.<dominio>` responde 401 sin token. Para consultar la base desde tu máquina: `ssh -L 5433:127.0.0.1:5433 ubuntu@<ip>`.

> La clave SSH (`.pem`) de la instancia nunca debe estar dentro del repositorio; `.gitignore` ya ignora `*.pem`, pero lo mejor es guardarla fuera del proyecto.

## Respaldos de la base de datos

`compose/backup.sh` usa `pg_dump` sobre el contenedor de PostgreSQL en ejecución, igual en desarrollo y en producción:

```bash
cd compose
./backup.sh                    # backups/ordenes_compra-<fecha>.sql.gz; borra los de más de 14 días
./backup.sh --keep 30          # conservar 30 días
./backup.sh --restore backups/ordenes_compra-20260929-030000.sql.gz    # pide confirmación
```

Para restaurar: `docker stop compras-backend-1`, correr `--restore` y `docker start compras-backend-1`. Los archivos quedan en `compose/backups/` (ignorado por git), en la misma máquina: cópialos fuera del servidor (scp o S3) o activa los snapshots automáticos de Lightsail. Programación diaria en el servidor (`crontab -e`):

```
0 3 * * * cd /home/ubuntu/compose && ./backup.sh >> backups/backup.log 2>&1
```

Prueba un restore de vez en cuando: un respaldo que nunca se restauró no está verificado.

## Documentación

- [`docs/especificacion-ordenes-compra.md`](docs/especificacion-ordenes-compra.md): especificación técnica completa (modelo de datos, reglas, máquina de estados, seguridad, diseño de la interfaz).
- [`docs/openapi.yaml`](docs/openapi.yaml): contrato de la API, fuente de los tipos del frontend.

---

© 2026 Henry Molina
