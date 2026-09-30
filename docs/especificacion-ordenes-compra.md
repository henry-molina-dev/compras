# Especificación técnica — Módulo de Órdenes de Compra a Proveedores

> **Autor:** [Henry Molina](https://henry-molina.dev/). Documento de diseño de un proyecto de demostración.

## 1. Contexto de negocio

La cadena cuenta con 26 ferreterías bajo dos formatos comerciales, más una unidad de Venta Directa:

- **Ferretería (16 tiendas):** formato tradicional enfocado en herramientas, pinturas, hogar, jardín y acabados.
- **Ferretería de Construcción (10 tiendas):** inventario robusto enfocado en materiales pesados y soluciones para la construcción.
- **Venta Directa (1 unidad):** atiende exclusivamente a clientes industriales, contratistas y grandes comercios.

El sistema debe permitir crear, modificar y anular (nunca borrar físicamente) órdenes de compra a proveedores, respetando las particularidades de cada formato de tienda.

## 2. Alcance funcional

1. Gestión de sucursales/puntos de venta, cada una con un **formato** (`FERRETERIA`, `FERRETERIA_CONSTRUCCION`, `VENTA_DIRECTA`).
2. Gestión de productos, con **categoría** y **precio**, y con unidad de compra distinta a la unidad de venta cuando aplique (factor de conversión).
3. Gestión de proveedores, con correo electrónico de contacto.
4. Gestión de clientes (solo relevantes para órdenes destinadas a Venta Directa).
5. Creación, modificación y anulación (lógica, vía cambio de estado) de órdenes de compra, con:
   - Totalización automática del monto.
   - Fecha de disponibilidad requerida (fecha en que se necesita que los productos ingresen a la sucursal).
   - Validación cruzada entre el formato de la sucursal destino y la categoría de los productos incluidos.
6. Máquina de estados de la orden, con reglas explícitas de transición.
7. Notificación por correo electrónico al proveedor cuando la orden es **aprobada** o **anulada**.
8. Webhook para que el proveedor reporte actualizaciones logísticas de la orden (aceptada, rechazada, preparada, despachada, entregada), visibles como línea de tiempo en la UI.
9. Autenticación y autorización basada en JWT propio (sin OAuth2) para usuarios internos, con roles `ADMIN` y `COMPRADOR`; autenticación separada por API key para el webhook de proveedores.
10. Módulo de administración de catálogos (Productos, Proveedores, Clientes, Usuarios) y consulta de bitácora de auditoría.
11. Tour guiado (onboarding) en el frontend para el flujo de creación de órdenes.
12. Registro masivo de órdenes de compra vía carga de archivo Excel (.xlsx), reutilizando las mismas reglas de negocio y el mismo servicio de creación que el flujo individual.

### Fuera de alcance (explícitamente)

- Reposición automática por punto de reorden.
- Multi-moneda y condiciones de pago complejas.
- Compra centralizada con distribución a múltiples sucursales desde una orden maestra.
- Módulo de recepción de mercadería con conteo físico (el estado `CERRADA` se maneja como transición manual simple).
- **Canales alternativos de actualización del proveedor** (respuesta de correo electrónico, hojas de cálculo, seguimiento telefónico/manual). En la vida real, no todos los proveedores tendrán capacidad técnica para integrarse vía webhook — algunos responderán por correo, enviarán una hoja de cálculo, o simplemente requerirán que alguien de compras los contacte para pedir el estatus. El modelo de esta aplicación asume que **el webhook es el único canal soportado por el sistema**; los demás casos seguirían resolviéndose de forma manual, fuera de la aplicación (ej. el comprador consulta al proveedor y, si corresponde, no hay forma de dejar ese avance registrado en la línea de tiempo salvo que alguien lo capture manualmente). Se documenta este vacío deliberadamente para dejar explícito que existe, no porque se considere trivial: una evolución natural del sistema sería un endpoint de "registro manual de evento de proveedor" para que el comprador capture a mano lo que un proveedor le reporte por otro medio, pero se deja fuera de esta demo para no diluir el foco en el flujo automatizado.

## 3. Modelo de dominio y diccionario de datos

Todas las tablas incluyen `fecha_creacion` (timestamp, default now) y `fecha_modificacion` (timestamp, actualizado en cada UPDATE) para cumplir el requisito de bitácora básica. Adicionalmente, `sucursal`, `proveedor`, `producto`, `cliente` y `orden_compra` incluyen `creado_por_id`/`modificado_por_id` (FK → `usuario.id`, nullable) para registrar qué usuario interno realizó cada cambio — ver regla de negocio en la sección 5. El detalle de auditoría de estados de la orden se registra además en una tabla dedicada.

**Moneda:** todos los campos monetarios (`producto.precio`, `orden_compra.total`, `orden_compra_detalle.precio_unitario`/`subtotal`) se manejan en **dólares estadounidenses (USD)**, sin columna de moneda — no hay soporte multi-moneda en el alcance de esta demo (sección 2, fuera de alcance). El frontend formatea estos valores como `$X,XXX.XX`.

### 3.1 `sucursal`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| nombre | varchar(150) | not null |
| formato | varchar(30) | not null — enum: `FERRETERIA`, `FERRETERIA_CONSTRUCCION`, `VENTA_DIRECTA` (CHECK, ver 3.13) |
| activo | boolean | default true |
| creado_por_id | integer | FK → `usuario.id`, nullable — `null` en registros de seed |
| modificado_por_id | integer | FK → `usuario.id`, nullable |
| fecha_creacion | timestamp | not null |
| fecha_modificacion | timestamp | not null |

**Reglas de administración:** solo `ADMIN` crea y edita sucursales (la lectura sigue abierta a cualquier usuario autenticado). Una sucursal desactivada no puede elegirse como destino de órdenes nuevas (`sucursal_inactiva`), pero sus órdenes existentes siguen siendo editables. El `formato` no se puede cambiar una vez que la sucursal tiene órdenes (409 `formato_bloqueado`), porque el formato gobierna las categorías permitidas y la obligatoriedad de cliente: para cambiarlo se desactiva la sucursal y se crea otra.

### 3.2 `proveedor`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| nombre | varchar(150) | not null |
| email | varchar(150) | not null, formato email validado |
| telefono | varchar(30) | nullable |
| direccion | varchar(250) | nullable |
| webhook_api_key | varchar(100) | unique, nullable — credencial para autenticar el webhook de este proveedor |
| descuento_maximo_pct | numeric(5,2) | not null, default 0, `>= 0 y < 100` (CHECK) — descuento máximo (en %) que se puede negociar sobre el precio de catálogo de cualquier producto de este proveedor |
| aumento_maximo_pct | numeric(5,2) | not null, default 0, `>= 0 y <= 100` (CHECK) — aumento máximo (en %) que se acepta sobre el precio de catálogo; con ambos límites en 0 el proveedor no admite negociar |
| activo | boolean | default true |
| creado_por_id | integer | FK → `usuario.id`, nullable — `null` en registros de seed |
| modificado_por_id | integer | FK → `usuario.id`, nullable |
| fecha_creacion | timestamp | not null |
| fecha_modificacion | timestamp | not null |

### 3.3 `categoria_producto`
Catálogo simple (tabla, no enum hardcodeado, para permitir mantenimiento vía administración).

| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| nombre | varchar(80) | not null, unique — ej. `HERRAMIENTAS`, `PINTURAS`, `HOGAR`, `JARDIN`, `ACABADOS`, `MATERIALES_PESADOS`, `CONSTRUCCION` |

### 3.4 `formato_categoria_permitida`
Tabla de mapeo N:N que define qué categorías de producto son válidas para cada formato de sucursal. Es la base de la validación cruzada.

| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| formato | varchar(30) | not null (CHECK, ver 3.13) |
| categoria_id | integer | FK → `categoria_producto.id`, not null |

> Unique constraint (`formato`, `categoria_id`).

### 3.5 `producto`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| codigo | varchar(30) | unique, not null |
| nombre | varchar(150) | not null |
| categoria_id | integer | FK → `categoria_producto.id`, not null |
| precio | numeric(12,2) | not null, > 0 (CHECK, ver 3.13) |
| unidad_venta | varchar(20) | not null — ej. `UNIDAD` |
| unidad_compra | varchar(20) | not null — ej. `CAJA`, `PALLET`, `TONELADA` |
| factor_conversion | numeric(10,4) | not null, default 1, > 0 (CHECK, ver 3.13) — cuántas unidades de venta equivalen a 1 unidad de compra |
| activo | boolean | default true |
| creado_por_id | integer | FK → `usuario.id`, nullable — `null` en registros de seed |
| modificado_por_id | integer | FK → `usuario.id`, nullable |
| fecha_creacion | timestamp | not null |
| fecha_modificacion | timestamp | not null |

### 3.6 `cliente`
Solo se asocia a órdenes cuya sucursal destino tiene formato `VENTA_DIRECTA`.

| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| nombre | varchar(150) | not null |
| tipo | varchar(30) | not null — enum: `INDUSTRIAL`, `CONTRATISTA`, `COMERCIO` (CHECK, ver 3.13) |
| email | varchar(150) | nullable, formato email validado |
| activo | boolean | default true |
| creado_por_id | integer | FK → `usuario.id`, nullable — `null` en registros de seed |
| modificado_por_id | integer | FK → `usuario.id`, nullable |
| fecha_creacion | timestamp | not null |
| fecha_modificacion | timestamp | not null |

### 3.7 `usuario`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| username | varchar(60) | unique, not null |
| password_hash | varchar(255) | not null (BCrypt) |
| rol | varchar(20) | not null — enum: `ADMIN`, `COMPRADOR`, `GERENTE_SUCURSAL` (CHECK, ver 3.13) |
| sucursal_id | integer | FK → `sucursal.id`, nullable — `null` para `ADMIN`/`COMPRADOR` (acceso a todas las sucursales); obligatorio para `GERENTE_SUCURSAL` (acceso restringido a la suya) |
| activo | boolean | default true |
| fecha_creacion | timestamp | not null |
| fecha_modificacion | timestamp | not null |

### 3.8 `orden_compra`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| numero_orden | varchar(20) | unique, not null — ej. `OC-2026-000123` |
| proveedor_id | integer | FK → `proveedor.id`, not null |
| sucursal_destino_id | integer | FK → `sucursal.id`, not null |
| cliente_id | integer | FK → `cliente.id`, nullable — obligatorio solo si `sucursal.formato = VENTA_DIRECTA` |
| usuario_id | integer | FK → `usuario.id`, not null — quien crea la orden |
| estado | varchar(20) | not null — `CREADA`, `APROBADA`, `ANULADA`, `CERRADA` (CHECK, ver 3.13) |
| fecha_necesaria | date | not null — fecha en que se necesita el ingreso de productos |
| total | numeric(14,2) | not null, default 0 — calculado a partir del detalle |
| motivo_anulacion | varchar(250) | nullable — obligatorio si `estado = ANULADA` (CHECK, ver 3.13) |
| conforme | boolean | nullable — se informa al cerrar la orden (confirmación de recepción en sucursal) |
| observacion_cierre | varchar(250) | nullable — observación del gerente de sucursal al confirmar recepción |
| modificado_por_id | integer | FK → `usuario.id`, nullable — última persona que editó el detalle mientras la orden estaba en `CREADA`. Distinto de `usuario_id` (quien la creó) y de `orden_compra_auditoria.usuario_id` (quien ejecutó cada transición de estado) |
| fecha_creacion | timestamp | not null |
| fecha_modificacion | timestamp | not null |

> **Generación de `numero_orden`:** consecutivo anual con el formato `OC-{año}-{consecutivo de 6 dígitos}` (ej. `OC-2026-000123`), reiniciando en 1 cada año calendario. Se implementa con una secuencia de PostgreSQL por año (`CREATE SEQUENCE orden_compra_seq_2026`) o una tabla de contador simple (`numero_orden_contador(anio, ultimo_valor)`) incrementada de forma atómica dentro de la misma transacción de creación de la orden — nunca calculado en el backend a partir de un `COUNT(*)`, que no es seguro ante creaciones concurrentes.

### 3.9 `orden_compra_detalle`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| orden_compra_id | integer | FK → `orden_compra.id`, not null, `ON DELETE CASCADE` (solo a nivel de integridad; la app nunca borra la orden) |
| producto_id | integer | FK → `producto.id`, not null |
| cantidad | numeric(10,2) | not null, > 0 (CHECK, ver 3.13) — expresada en unidad de compra |
| precio_unitario | numeric(12,2) | not null — precio de la línea: el del catálogo al crearla, o el negociado con el proveedor dentro de sus límites (ver regla de negocio 2) |
| precio_catalogo | numeric(12,2) | not null, > 0 (CHECK) — precio de catálogo vigente al crear la línea; referencia de los límites de negociación y de la variación mostrada |
| subtotal | numeric(14,2) | not null — `cantidad * precio_unitario` |
| fecha_creacion | timestamp | not null |
| fecha_modificacion | timestamp | not null |

### 3.10 `orden_compra_auditoria`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| orden_compra_id | integer | FK → `orden_compra.id`, not null |
| estado_anterior | varchar(20) | nullable (null en la creación) |
| estado_nuevo | varchar(20) | not null |
| usuario_id | integer | FK → `usuario.id`, not null |
| observacion | varchar(250) | nullable |
| fecha | timestamp | not null |

### 3.11 `orden_evento_proveedor`
Tabla append-only (no se edita ni se borra) que registra las actualizaciones logísticas que el proveedor reporta vía webhook. Es puramente informativa: no dispara transiciones de estado internas.

| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| orden_compra_id | integer | FK → `orden_compra.id`, not null |
| tipo_evento | varchar(20) | not null — `ACEPTADA`, `RECHAZADA`, `PREPARADA`, `DESPACHADA`, `ENTREGADA` (CHECK, ver 3.13) |
| observacion | varchar(250) | nullable — ej. motivo si `tipo_evento = RECHAZADA` |
| fecha_evento | timestamp | not null — fecha que informa el proveedor en el payload |
| fecha_creacion | timestamp | not null — fecha en que el webhook fue recibido por el sistema |

### 3.12 `idempotency_key`
Soporta el mecanismo de idempotencia descrito en la sección 12 (convenciones inspiradas en Stripe).

| Campo | Tipo | Restricciones |
|---|---|---|
| id | serial | PK |
| clave | varchar(100) | not null |
| endpoint | varchar(150) | not null |
| response_body | text | nullable |
| status_code | integer | nullable |
| fecha_creacion | timestamp | not null |

> Unique constraint (`clave`, `endpoint`).

### 3.13 Validaciones a nivel de base de datos (defensa en profundidad)

Las reglas de negocio descritas en la sección 5 se implementan en el backend (Bean Validation + lógica de servicio) y se reflejan en el frontend (Zod) para dar feedback inmediato. Ambas capas se saltan por completo ante una intervención directa sobre la base de datos — un `UPDATE` manual de "break-glass" durante un incidente, un script de migración de datos, o una futura herramienta de administración de DB — porque la base de datos por sí sola no rechaza hoy un `estado` inválido, un precio negativo, o una orden `ANULADA` sin motivo.

**Implementado en esta demo — restricciones `CHECK` de una sola fila.** Postgres las evalúa directamente sobre la fila que se inserta o actualiza, sin necesitar funciones ni triggers:

- Whitelists de enums que hoy solo están documentadas como comentario en la tabla anterior: `sucursal.formato`, `formato_categoria_permitida.formato`, `orden_compra.estado`, `usuario.rol`, `cliente.tipo`, `orden_evento_proveedor.tipo_evento`.
- Montos y cantidades positivos: `producto.precio > 0`, `producto.factor_conversion > 0`, `orden_compra_detalle.cantidad > 0`.
- `orden_compra`: `CHECK (estado <> 'ANULADA' OR motivo_anulacion IS NOT NULL)` — refleja la regla de negocio 6 (sección 5) también a nivel de fila.

Estas restricciones se agregan directamente en los scripts Flyway de la Fase 1 (`V1__init_schema.sql`), junto a la definición de cada columna marcada arriba con "(CHECK, ver 3.13)".

**Documentado como evolución futura, fuera del alcance de esta demo — reglas que requieren un trigger.** Postgres no permite que un `CHECK` consulte otra tabla u otra fila, así que estas exigen una función `PL/pgSQL` disparada `BEFORE INSERT/UPDATE` (o `AFTER` sobre una tabla relacionada):

- **Máquina de estados a nivel de base de datos.** Un trigger sobre `orden_compra` que rechace cualquier transición de `estado` fuera de las permitidas por la tabla de la sección 4 (ej. `CERRADA → ANULADA`), como red de seguridad independiente del State Pattern del backend.
- **Cliente obligatorio en Venta Directa, a nivel de fila.** Un trigger que verifique, contra `sucursal.formato`, que `cliente_id` no sea nulo cuando la sucursal destino es `VENTA_DIRECTA` — hoy esta regla (número 4, sección 5) solo vive en el backend.
- **Consistencia de `orden_compra.total`.** Un trigger sobre `orden_compra_detalle` (`AFTER INSERT/UPDATE/DELETE`) que recalcule y sincronice `orden_compra.total` a partir de sus líneas, en vez de confiar exclusivamente en que el servicio de backend lo haga correctamente.

Se documenta esta capa deliberadamente, no porque se considere trivial, sino para dejar explícito qué protección existe hoy (`CHECK`) y cuál se sacrificó por simplicidad (triggers) — mismo criterio que otros puntos ya documentados como fuera de alcance en la sección 2 (ej. canales alternativos del proveedor, exportación masiva de PDFs): son extensiones naturales del sistema, no vacíos accidentales.

## 4. Máquina de estados de la orden de compra

```
CREADA ──aprobar──> APROBADA ──cerrar──> CERRADA
  │                     │
  └──────anular─────────┴──────anular───> ANULADA
```

| Estado actual | Anular | Aprobar | Cerrar | Modificar detalle |
|---|---|---|---|---|
| CREADA | Sí (`COMPRADOR`/`ADMIN`) | Sí → APROBADA (`COMPRADOR`/`ADMIN`) | No | Sí (`COMPRADOR`/`ADMIN`) |
| APROBADA | Sí, con `motivo_anulacion` obligatorio (`COMPRADOR`/`ADMIN`) | No | Sí → CERRADA (`GERENTE_SUCURSAL` de la sucursal destino, o `ADMIN`) | No |
| CERRADA | **No** (mercadería ya ingresó; anular generaría inconsistencia con inventario) | No | No | No |
| ANULADA | No (estado final) | No | No | No |

`aprobar` y `anular` son exclusivas de `COMPRADOR`/`ADMIN` (decisiones de compra); `cerrar` es exclusiva de `GERENTE_SUCURSAL` de la sucursal destino de la orden (o `ADMIN`) — refleja la separación entre quien negocia/aprueba el gasto y quien certifica la recepción física de la mercadería.

Cualquier transición inválida debe responder **HTTP 409 Conflict** con un mensaje de negocio explícito (no un error genérico de validación). Cada transición exitosa debe insertar un registro en `orden_compra_auditoria`.

### Patrón de implementación sugerido

State Pattern: una interfaz `EstadoOrden` con métodos `aprobar`, `anular`, `cerrar`; una implementación por estado (`CreadaState`, `AprobadaState`, `CerradaState`, `AnuladaState`); cada una implementa solo las transiciones que le son válidas, y lanza `EstadoInvalidoException` en las demás.

## 5. Reglas de negocio explícitas

1. **No borrado físico.** Ninguna operación elimina filas de `orden_compra`; "anular" es exclusivamente un cambio de estado.
2. **Precio congelado y negociable.** `orden_compra_detalle.precio_unitario` es el precio de la línea: por defecto el del catálogo al momento de agregarla, o uno negociado con el proveedor. Cada línea guarda además el `precio_catalogo` vigente al crearla, que es la referencia de la negociación y de la variación que se muestra (cambios posteriores al precio del producto no afectan órdenes ya creadas). El precio negociado debe estar entre `precio_catalogo × (1 − descuento_maximo_pct)` y `precio_catalogo × (1 + aumento_maximo_pct)` del proveedor de la orden, ambos extremos incluidos, con máximo dos decimales; los límites son por proveedor y aplican a todos sus productos y a todos los roles (sin flujo de aprobación). Con 0% y 0% el proveedor no admite negociar. Fuera de rango: 400 `precio_fuera_de_rango`. Al editar una orden, una línea sin precio explícito conserva el precio y la referencia que ya tenía (no toma el catálogo de hoy); un producto nuevo usa el catálogo vigente; y si cambia el proveedor, los precios conservados se validan contra los límites del nuevo.
3. **Validación cruzada formato ↔ categoría.** Al agregar o modificar una línea de detalle, el sistema valida que la `categoria_id` del producto esté permitida para el `formato` de la `sucursal_destino` de la orden, según `formato_categoria_permitida`. Un producto de categoría no permitida debe rechazarse con un mensaje claro (ej. "El producto X (categoría Materiales Pesados) no está permitido para sucursales de formato Ferretería").
4. **Cliente obligatorio en Venta Directa.** Si `sucursal_destino.formato = VENTA_DIRECTA`, el campo `cliente_id` es obligatorio en la orden. Para los otros dos formatos, debe omitirse (permanecer `null`).
5. **Totalización automática.** `orden_compra.total` se recalcula en el backend cada vez que cambia el detalle; nunca se recibe como input directo del cliente.
6. **Anulación con motivo.** La transición a `ANULADA` requiere `motivo_anulacion` no vacío.
7. **Bloqueo de edición fuera de CREADA.** Solo se permite modificar el detalle de una orden mientras está en estado `CREADA`.
8. **Los eventos del proveedor son puramente informativos.** Ningún evento recibido vía webhook (incluyendo `ENTREGADA` o `RECHAZADA`) dispara automáticamente una transición de estado interna. Toda transición (`aprobar`, `anular`, `cerrar`) sigue siendo una acción explícita de un usuario interno (`COMPRADOR`/`ADMIN`/`GERENTE_SUCURSAL` según corresponda).
9. **Segregación de funciones en el cierre.** Confirmar la recepción de una orden (`cerrar`) es exclusivo de un `GERENTE_SUCURSAL` perteneciente a la sucursal destino de esa orden (o de `ADMIN`); requiere informar `conforme` (booleano) y opcionalmente `observacion_cierre`. Un `COMPRADOR` no puede ejecutar esta transición, aun cuando pueda ver la orden.
10. **Visibilidad restringida por sucursal.** Un usuario con rol `GERENTE_SUCURSAL` solo puede listar y ver el detalle de órdenes cuya `sucursal_destino_id` coincide con su `usuario.sucursal_id`; intentar acceder a una orden de otra sucursal responde `403 Forbidden`. `COMPRADOR` y `ADMIN` ven todas las órdenes sin restricción de sucursal.
11. **Trazabilidad de autoría, completada por el servidor.** Los campos `creado_por_id`/`modificado_por_id` (en `sucursal`, `proveedor`, `producto`, `cliente`) y `modificado_por_id` (en `orden_compra`) **nunca se reciben como input del cliente**: el backend los completa automáticamente a partir del usuario autenticado en el `SecurityContext` (extraído del JWT) en cada creación/actualización. Esto evita que un cliente falsifique la autoría enviándola como parte del payload.

## 6. Notificaciones por correo

| Evento disparador | Destinatario | Contenido mínimo |
|---|---|---|
| Orden pasa a `APROBADA` | `proveedor.email` | Número de orden, fecha de necesidad, detalle de productos y cantidades, total |
| Orden **`APROBADA` pasa a** `ANULADA` | `proveedor.email` | Número de orden, motivo de anulación, fecha de anulación |

**Anular una orden que seguía en `CREADA` no envía correo.** El proveedor solo se entera de una orden cuando esta pasa a `APROBADA` (única transición que dispara el correo de aprobación); si se anula antes de llegar a ese punto, el proveedor nunca supo de la orden, así que no hay nada que cancelarle — notificarlo igual sería confuso y, en la práctica, el primer correo que recibiría de una orden sería el de su propia anulación. `OrdenCompraService.anular()` solo publica `OrdenAnuladaEvent` cuando el estado anterior a la transición era `APROBADA`.

**Ambos correos llevan el PDF de la orden adjunto** (el mismo documento que genera la exportación individual, sección 9.1) — así el proveedor tiene el documento formal a la mano sin tener que pedirlo por separado. El PDF se renderiza en el momento de construir el evento, todavía dentro de la transacción que aprobó/anuló la orden (mientras sus relaciones de Hibernate siguen cargadas); el adjunto es "mejor esfuerzo": si el renderizado falla, se registra el error pero el correo se envía igual sin el archivo, y la transición de estado ya confirmada nunca se revierte por esto.

**Patrón de implementación:** eventos de dominio (`OrdenAprobadaEvent`, `OrdenAnuladaEvent`, cada uno con el PDF ya renderizado como `byte[]`) publicados vía `ApplicationEventPublisher` de Spring, consumidos por un `EmailListener` que invoca a un `EmailService`. El envío de correo es asíncrono/no transaccional respecto al cambio de estado: si el correo falla, se registra el error en logs pero la transición de estado ya confirmada no se revierte.

**Infraestructura para la demo:** servidor SMTP simulado (Mailpit) como contenedor adicional en `docker-compose.yml`, con interfaz web en `localhost:8025` para verificar visualmente la llegada de los correos durante la presentación.

## 7. Webhook de actualizaciones del proveedor

Permite que el proveedor (sistema externo) reporte el avance logístico de una orden ya aprobada. Es un flujo de integración máquina a máquina, independiente de la máquina de estados interna.

> **Supuesto explícito:** este webhook asume un proveedor con capacidad de integración vía API. En la práctica, no todos los proveedores tendrán esa capacidad (algunos reportarán por correo, hoja de cálculo, o requerirán seguimiento manual) — ver "Fuera de alcance" en la sección 2. La aplicación no cubre esos canales alternativos en esta demo.

**Tipos de evento:** `ACEPTADA`, `RECHAZADA` (rechazo total, con `observacion` recomendable), `PREPARADA`, `DESPACHADA`, `ENTREGADA`.

**Endpoint:** `POST /api/webhooks/eventos`

**Payload esperado:**
```json
{
  "numero_orden": "OC-2026-000123",
  "tipo_evento": "DESPACHADA",
  "observacion": "Despachado en 2 camiones, ETA 2 días",
  "fecha_evento": "2026-09-24T10:30:00-06:00"
}
```

**Identificación de la orden:** por `numero_orden` (el mismo que el proveedor recibe en el correo de aprobación y en el PDF), no por el `id` interno de la base de datos. La orden debe pertenecer al proveedor autenticado; una orden de otro proveedor responde `404`, igual que una inexistente, para no revelar que existe.

**Autenticación:** header `X-Api-Key`, validado contra `proveedor.webhook_api_key`. La clave identifica al proveedor: la URL no lleva su id. No usa JWT — el proveedor no es un usuario interno con sesión, sino un sistema externo autenticado por credencial propia. Se implementa como un filtro de seguridad independiente del `JwtAuthFilter` (otra cadena de filtros o un matcher específico sobre `/api/webhooks/**`).

**Comportamiento:**
- El evento se inserta en `orden_evento_proveedor` (append-only).
- No se valida ni se fuerza ninguna secuencia entre tipos de evento (se confía en el orden que reporte el proveedor); simplemente se registra con su `fecha_evento`.
- **No dispara transiciones de estado internas** (ver regla de negocio 8 en la sección 5), ni siquiera `ENTREGADA`.
- Un evento `ENTREGADA` puede usarse en el frontend como señal visual (ej. resaltar el botón "Cerrar orden") para sugerir al comprador que confirme el cierre manualmente, pero el cierre sigue siendo una acción explícita vía `PATCH /api/ordenes/{id}/cerrar`.

**Línea de tiempo en la UI:** en el detalle de la orden, se muestra una vista cronológica combinada que mezcla los registros de `orden_compra_auditoria` (cambios de estado internos) con los de `orden_evento_proveedor` (eventos del proveedor), ordenados por fecha — dando al comprador una vista única del ciclo de vida completo de la orden.

## 8. Registro masivo de órdenes de compra

Permite crear varias órdenes en una sola operación, vía carga de un archivo Excel, para casos de reposición periódica, carga de un pedido recurrente, o migración de datos.

**Principio de diseño clave: no es un camino alterno con sus propias reglas.** El proceso de carga masiva agrupa las filas del archivo por orden, arma el mismo `OrdenCompraRequest` que usa el formulario individual (sección 15), y llama al mismo servicio de creación (`OrdenCompraService.crearOrden()`). Todas las reglas de negocio ya definidas — validación cruzada formato↔categoría, cliente obligatorio en Venta Directa, cálculo de totales — se aplican automáticamente, sin duplicar ni reescribir esa lógica en un flujo aparte.

**Desviación respecto al diseño original: `.xlsx` en vez de CSV.** La primera versión de esta sección definía un CSV de texto plano con `proveedor_id`/`sucursal_destino_id`/`cliente_id` como IDs numéricos, y dejaba `.xlsx` fuera de alcance como extensión opcional. Se descartó esa versión tras encontrar dos problemas de uso real:

1. **Los usuarios no conocen los IDs numéricos** de proveedor/sucursal/cliente — la UI de administración no los muestra en ningún lado, así que la única forma de obtener uno era consultar la API o la base de datos directamente.
2. **Un CSV de texto plano depende de la configuración regional del sistema operativo** de quien lo edita en Excel/LibreOffice: en locales donde la coma es el separador decimal (como el usado en El Salvador), esas herramientas exportan `;` en vez de `,` como separador de columna por defecto, y además escriben número y fecha en el formato regional (coma decimal, `dd/MM/yyyy`) en vez de un formato neutral — cualquiera de los dos rompe un parser de CSV ingenuo, o peor, hace que interprete mal un valor sin ningún aviso.

Un archivo `.xlsx` real no tiene ninguno de los dos problemas: cada celda es un valor tipado (número o fecha reales que Excel guarda internamente, no texto que haya que adivinar cómo interpretar) y admite varias hojas — lo que permite ofrecer catálogos de referencia con listas desplegables en vez de pedirle al usuario que escriba un ID o un nombre exacto de memoria.

**Formato de archivo:** `.xlsx`, con una hoja **"Ordenes"** (una fila por línea de producto, agrupadas por una columna `referencia_lote` que identifica a qué orden pertenece cada fila) y cuatro hojas de referencia de solo lectura — **"Proveedores"**, **"Sucursales"**, **"Clientes"**, **"Productos"** — con los nombres/códigos activos del catálogo. Las columnas `proveedor`, `sucursal_destino`, `cliente` y `producto_codigo` de la hoja "Ordenes" tienen una lista desplegable (validación de datos de Excel) que apunta a la hoja de referencia correspondiente, para que el usuario elija en vez de escribir a mano.

| Columna | Contenido | Obligatoria |
|---|---|---|
| `referencia_lote` | Identifica a qué orden pertenece la fila | Sí |
| `proveedor` | Nombre exacto del proveedor (lista desplegable) | Sí |
| `sucursal_destino` | Nombre exacto de la sucursal destino (lista desplegable) | Sí |
| `cliente` | Nombre exacto del cliente (lista desplegable); obligatorio solo si la sucursal es Venta Directa | No |
| `fecha_necesaria` | Celda de fecha (o texto en formato ISO `YYYY-MM-DD` si se escribe a mano) | Sí |
| `producto_codigo` | Código del producto (lista desplegable) | Sí |
| `cantidad` | Celda numérica | Sí |
| `precio_unitario` | Precio unitario negociado (celda numérica, máximo dos decimales). Vacía = precio de catálogo; con valor se valida contra los límites del proveedor, igual que en el flujo individual. La columna es opcional: un archivo con la plantilla anterior, sin ella, sigue funcionando | No |

`proveedor`/`sucursal_destino`/`cliente` se resuelven por nombre exacto (sin distinguir mayúsculas/minúsculas). Como `nombre` no tiene restricción `UNIQUE` en esas tablas, un nombre duplicado en el catálogo se reporta como lote rechazado (`nombre_ambiguo`) en vez de adivinar cuál de los dos coincidentes usar.

**Atomicidad: por orden sí, por archivo no.** Si una fila de un lote falla validación, se rechaza esa orden completa (todas las filas de ese `referencia_lote`), pero no afecta a los demás lotes del mismo archivo — un error en una orden no debe invalidar las demás que sí son válidas.

**Procesamiento síncrono, con reporte de resultado.** Para el volumen esperado en esta demo (decenas o pocos cientos de filas), se procesa dentro del mismo request HTTP y se devuelve un resumen; no se justifica una cola de trabajo en background para este alcance. Se documenta como posible evolución futura si el volumen de uso creciera significativamente.

**Endpoints:**

| Método | Ruta | Rol requerido | Descripción |
|---|---|---|---|
| GET | `/api/ordenes/importaciones/plantilla` | COMPRADOR, ADMIN | Descarga un `.xlsx` de ejemplo con la hoja "Ordenes" y los catálogos de referencia |
| POST | `/api/ordenes/importaciones` | COMPRADOR, ADMIN | Sube el archivo (`multipart/form-data`), procesa cada lote y devuelve el reporte de resultado |

**Formato de respuesta** (mismo objeto de error tipado de la sección 12 para cada orden rechazada):
```json
{
  "object": "importacion_lote",
  "ordenes_creadas": [
    { "referencia_lote": "LOTE-1", "orden_id": 145, "numero_orden": "OC-2026-000145" }
  ],
  "ordenes_rechazadas": [
    {
      "referencia_lote": "LOTE-2",
      "error": {
        "type": "invalid_request_error",
        "code": "categoria_no_permitida",
        "message": "El producto TORN-05 no está permitido para esta sucursal.",
        "param": "producto_codigo"
      }
    }
  ]
}
```

**Frontend:** una pantalla "Importar órdenes" dentro de la sección Compras, con botón de descarga de plantilla, control de subida de archivo, y una tabla de resultado que distingue las órdenes creadas de las rechazadas (mostrando el motivo de cada rechazo con el mismo objeto de error que se usa en el resto de la aplicación).

## 9. Exportación de órdenes de compra

Cubre dos necesidades distintas, con formatos y granularidad propios de cada una — no una "exportación genérica".

### 9.1 Exportación individual — documento PDF

Genera el documento formal de una orden (cabecera + detalle), para enviar, imprimir o archivar.

```
GET /api/ordenes/{id}/exportar?formato=pdf
```
Mismo control de acceso que ya existe para ver la orden (`COMPRADOR`/`ADMIN`/`GERENTE_SUCURSAL`, este último restringido a su propia sucursal, sección 5).

**Implementación:** **openhtmltopdf** (licencia Apache 2.0, evita las restricciones de licenciamiento de iText) renderiza una plantilla HTML/CSS a PDF, reutilizando la paleta y tipografía definidas en la sección 14 (Sistema de diseño y UI) — el documento se ve consistente con el resto de la aplicación en vez de una plantilla genérica aparte.

### 9.2 Exportación masiva — reporte Excel

Reporte tabular para análisis o conciliación, con los mismos filtros que ya existen en el listado de órdenes. Se usa `.xlsx` en vez de CSV por la misma razón que la carga masiva (sección 8): un archivo de texto plano depende del separador de lista y el formato numérico/de fecha de la configuración regional del sistema operativo de quien lo abra en Excel/LibreOffice.

```
GET /api/ordenes/exportar?formato=xlsx&estado=APROBADA&sucursal_id=2
```

**Granularidad: una fila por línea de producto** (no una fila por orden), con los datos de cabecera repetidos en cada fila — de lo contrario se pierde el detalle de qué se compró, que es justamente lo que hace útil un reporte de compras. Esto deja el formato **simétrico** con la hoja "Ordenes" de la carga masiva de la sección 8: mismas columnas, en sentido inverso.

| numero_orden | proveedor | sucursal_destino | estado | fecha_necesaria | producto_codigo | producto_nombre | cantidad | precio_unitario | subtotal | total_orden |
|---|---|---|---|---|---|---|---|---|---|---|
| OC-2026-000145 | Ferretera del Norte S.A. | Ferretería de Construcción Santa Ana | APROBADA | 2026-10-15 | CEM-42 | Cemento gris (saco 42.5kg) | 40 | 45.00 | 1800.00 | 8450.00 |
| OC-2026-000145 | Ferretera del Norte S.A. | Ferretería de Construcción Santa Ana | APROBADA | 2026-10-15 | VAR-38 | Varilla 3/8" (6m) | 120 | 55.42 | 6650.00 | 8450.00 |

`total_orden` se repite deliberadamente en cada fila — permite construir una tabla dinámica por orden en Excel sin tener que buscar el total en otro lado.

**Fuera de alcance:** exportación masiva de PDFs (ej. un ZIP con un PDF por orden). Se documenta como extensión futura — no aporta una necesidad de negocio tan clara como el reporte Excel para justificar la complejidad adicional (generación en lote, compresión, archivos grandes).

**Frontend:** botón "Exportar PDF" en el detalle de la orden; botón "Exportar Excel" en el listado de órdenes, aplicando los mismos filtros activos en pantalla.

## 10. Seguridad

- Autenticación propia basada en JWT, **sin OAuth2** ni proveedor de identidad externo, para usuarios internos (`ADMIN`, `COMPRADOR`).
- Endpoint `POST /api/auth/login` recibe `username`/`password`, valida contra `usuario` (password con BCrypt) y devuelve un JWT firmado con clave secreta propia, con claims `sub` (username), `rol`, `exp`.
- Todos los endpoints protegidos (excepto el webhook) requieren header `Authorization: Bearer <token>`.
- `SecurityFilterChain` configurado en modo `STATELESS` (sin sesión de servidor).
- Autorización por rol:
  - `COMPRADOR`: acceso a creación/gestión de órdenes de compra (crear, editar, aprobar, anular) de todas las sucursales.
  - `GERENTE_SUCURSAL`: acceso de solo lectura a las órdenes de **su** sucursal (`usuario.sucursal_id`), y a la transición `cerrar` (confirmar recepción) sobre esas órdenes. Sin acceso a crear/aprobar/anular ni a otras sucursales.
  - `ADMIN`: acceso adicional a todo el módulo de administración (catálogos, usuarios, auditoría), y a todas las órdenes de todas las sucursales.
- La restricción de `GERENTE_SUCURSAL` a su propia sucursal es autorización a nivel de fila (row-level): se aplica como filtro obligatorio en el servicio, no solo como chequeo de rol.
- El endpoint de webhook (`/api/webhooks/eventos`) usa un mecanismo de autenticación independiente (API key por proveedor), no JWT — es una integración sistema-a-sistema, no una sesión de usuario.
- **CORS.** Frontend y backend corren en orígenes distintos (puertos distintos como mínimo). Se configura un `CorsConfigurationSource` explícito en Spring Security, permitiendo el origen del frontend (ej. `http://localhost:5173` en desarrollo, el dominio real en despliegue), los métodos `GET/POST/PUT/PATCH`, y el header `Authorization`. Sin esto, el navegador bloquea las llamadas del frontend a la API y el síntoma es engañoso — parece que "la API no responde" cuando en realidad es una petición bloqueada antes de llegar al backend.

## 11. Arquitectura y patrones de diseño

**Estilo arquitectónico:** monolito modular (no microservicios). Justificación: el dominio de Administración y Compras comparte entidades base y base de datos; separar en microservicios añadiría complejidad de comunicación/autenticación distribuida sin beneficio para el alcance de esta demo. Los módulos se separan a nivel de paquete para facilitar una eventual extracción futura a microservicios.

```
backend/
├── modulo-compras/
│   ├── controller/
│   ├── service/
│   │   └── state/         (State Pattern para transiciones de orden)
│   └── repository/
├── modulo-administracion/
│   ├── controller/
│   ├── service/
│   └── repository/
├── shared/                 (entidades comunes: Producto, Proveedor, Sucursal, Cliente, Usuario)
├── notificaciones/         (EmailService, EmailListener, eventos de dominio)
├── webhooks/                (controller y service del webhook de proveedores, ApiKeyAuthFilter)
├── security/                (JwtService, JwtAuthFilter, SecurityConfig)
├── dto/
└── exception/               (manejo centralizado vía @ControllerAdvice)
```

**Patrones de diseño a aplicar:**
- **Layered architecture** (Controller → Service → Repository).
- **State Pattern** para las transiciones de estado de la orden.
- **Observer / Event-driven** (Spring `ApplicationEventPublisher`) para notificaciones por correo, desacoplando el envío del cambio de estado.
- **DTO** para separar entidades de persistencia de los contratos de API.
- **Repository Pattern** vía Spring Data JPA.
- Manejo centralizado de errores vía `@ControllerAdvice` / `@ExceptionHandler`.

**Versiones de plataforma:**
- **Java 25 LTS** (liberado septiembre 2025, soporte hasta 2032) — última LTS disponible.
- **Spring Boot 4.1.x** (última versión estable con soporte activo; construida sobre Spring Framework 7). Nota: a diferencia de Java, Spring Boot no maneja el concepto de "LTS" — cada minor recibe ~12 meses de soporte gratuito, así que "última LTS" no aplica literalmente; lo correcto es fijar la última versión estable soportada al momento de iniciar el proyecto.
- **Jakarta EE 11** como baseline (incluida con Spring Boot 4.x): Bean Validation 3.1, Servlet 6.1, Persistence (JPA) 3.2.

**Lombok:** usado de forma selectiva sobre las entidades JPA (`@Getter`, `@Setter`, `@Builder`), nunca `@Data`. `@Data` genera `equals()`/`hashCode()`/`toString()` sobre todos los campos, incluyendo relaciones `@ManyToOne`/`@OneToMany` — con relaciones bidireccionales esto puede producir recursión infinita o inconsistencias con proxies de carga perezosa de Hibernate.

**DTOs como Records de Java**, no como clases con Lombok: son inmutables, de sintaxis compacta, y las anotaciones de Jakarta Bean Validation (`@NotNull`, `@Email`, `@Future`, etc.) se aplican directamente sobre los componentes del record. Esto hace innecesario Lombok en la capa de DTO — su uso se reserva exclusivamente a las entidades.

```java
public record OrdenCompraRequest(
    @NotNull Integer proveedorId,
    @NotNull Integer sucursalDestinoId,
    Integer clienteId,
    @NotNull @Future LocalDate fechaNecesaria,
    @NotEmpty List<@Valid DetalleRequest> detalle
) {}
```

**Mapeo DTO ↔ Entity: MapStruct**, en vez de mapeo manual o librerías basadas en reflection (ej. ModelMapper). Genera el código de mapeo en tiempo de compilación (más rápido, y los errores de mapeo se detectan al compilar, no en producción), y soporta Records como origen/destino desde la versión 1.5+.

> **Nota de configuración:** al combinar Lombok con MapStruct, el `maven-compiler-plugin` requiere la dependencia puente `lombok-mapstruct-binding` en sus annotation processor paths, o MapStruct no reconoce los getters/builders generados por Lombok sobre las entidades.
> ```xml
> <path>
>   <groupId>org.projectlombok</groupId>
>   <artifactId>lombok-mapstruct-binding</artifactId>
>   <version>0.2.0</version>
> </path>
> ```

## 12. Convenciones de diseño de API (inspiradas en Stripe)

Stripe es probablemente la referencia más citada de diseño de API REST bien pensado: no es solo "usar sustantivos en las rutas", sino un conjunto de convenciones que hacen la API predecible, segura ante reintentos, y fácil de evolucionar. Aplicamos las que tienen sentido para el alcance de esta demo, sin sobredimensionar (no necesitamos, por ejemplo, todo lo que Stripe hace para mantener compatibilidad entre miles de integradores externos):

**1. Idempotencia en operaciones de creación/mutación.** Los endpoints `POST /api/ordenes`, `PATCH /api/ordenes/{id}/aprobar`, `/anular`, `/cerrar`, y `POST /api/webhooks/eventos` aceptan un header opcional `Idempotency-Key` (string, generado por el cliente). Si se recibe una key ya usada para ese mismo endpoint en un plazo razonable (ej. 24 horas), el backend devuelve la respuesta original guardada en vez de reprocesar la operación. Esto es crítico para el webhook: un proveedor con reintentos automáticos (timeouts, reintentos de red) no debe generar eventos duplicados en la línea de tiempo. Se implementa con una tabla simple `idempotency_key` (`key`, `endpoint`, `response_body`, `status_code`, `fecha_creacion`).

**2. Formato de respuesta exitosa, consistente en toda la API.** Un recurso individual se devuelve directamente (sin envolver en `data`), con un campo `object` que identifica su tipo — útil sobre todo cuando un recurso viene embebido dentro de otro vía `expand` (punto 5):
```json
{
  "object": "orden_compra",
  "id": 123,
  "numero_orden": "OC-2026-000123",
  "estado": "APROBADA",
  "proveedor_id": 4,
  "sucursal_destino_id": 2,
  "cliente_id": null,
  "fecha_necesaria": "2026-10-15",
  "total": 8450.00,
  "created_at": "2026-09-24T14:32:10Z",
  "updated_at": "2026-09-24T15:01:00Z"
}
```
Reglas asociadas:
- Los endpoints de transición de estado (`aprobar`, `anular`, `cerrar`) devuelven el **recurso actualizado completo**, no un genérico `{"success": true}` — el frontend actualiza su estado local con la respuesta, sin necesitar un segundo `GET`.
- Fechas y horas en **ISO-8601**: timestamps completos (`created_at`, `updated_at`) como `2026-09-24T14:32:10Z`; fechas sin hora (`fecha_necesaria`) como `2026-10-15`. Es el default natural de Jackson con `Instant`/`LocalDate`, y más legible en la demo que un epoch numérico.
- Códigos HTTP: `200` para GET/PUT/PATCH, `201` para POST de creación (con header `Location` apuntando al recurso creado).

**3. Objeto de error consistente y tipado**, en vez de mensajes sueltos por endpoint:
```json
{
  "error": {
    "type": "invalid_request_error",
    "code": "categoria_no_permitida",
    "message": "El producto TORN-05 (categoría Materiales Pesados) no está permitido para sucursales de formato Ferretería.",
    "param": "detalle[2].producto_id"
  }
}
```
`type` distingue clases de error (`invalid_request_error`, `authentication_error`, `state_conflict_error`, `api_error`); `code` es un identificador estable que el frontend puede usar para lógica condicional sin parsear el mensaje; `param` señala el campo específico cuando aplica.

**4. Recursos anidados para relaciones de pertenencia clara.** Ya lo aplicamos de forma natural: `orden_compra_detalle` pertenece a una orden, así que su gestión ocurre a través de `orden_compra` (no como colección de primer nivel), y el webhook identifica al proveedor por su API key, no por la URL: `/api/webhooks/eventos`.

**5. Expansión de objetos relacionados (`expand`).** `GET /api/ordenes/{id}?expand=proveedor&expand=linea_tiempo` devuelve el proveedor y la línea de tiempo embebidos en la respuesta (cada uno con su propio `object`), en vez de que el frontend haga llamadas adicionales para cada relación. Por defecto (sin `expand`), las relaciones se devuelven solo como referencia (`proveedor_id`), manteniendo la respuesta liviana.

**6. Paginación basada en cursor**, no en offset/página, para `GET /api/ordenes`, usando el mismo envelope de listado que ya se usa para cualquier colección:
```
GET /api/ordenes?limit=20&starting_after=OC-2026-000123
```
Respuesta:
```json
{
  "object": "list",
  "data": [ /* array de objetos orden_compra */ ],
  "has_more": true
}
```
Es más estable que la paginación por página cuando se insertan nuevas órdenes mientras alguien navega el listado.

**7. Versionado de API explícito.** Header `Stripe-Version`-style: `X-Api-Version: 2026-09-24` (fecha de la versión del contrato). Si no se envía, se asume la última versión disponible. Para esta demo no habrá múltiples versiones activas simultáneamente, pero se deja el mecanismo listo — es la forma correcta de evolucionar contratos de API sin romper integraciones existentes (como el webhook de proveedores, que es justo el tipo de integración externa que no se puede romper sin aviso).

**8. Nombres de campos y estados sin ambigüedad, en snake_case en el JSON de la API** (consistente con la base de datos) aunque el código Java use camelCase internamente — evita que el frontend tenga que adivinar la convención.

**Documentación de la API.** El contrato completo está definido en `openapi.yaml` (adjunto a esta especificación) — es el contrato preciso contra el cual se implementa, cubriendo todos los endpoints de la sección 13 con sus esquemas, roles, y las convenciones de este apartado. Se mantiene a mano; no hay generación en runtime a partir del código.

Esto se documenta como convención transversal a todos los contratos de la sección 13; no se repite en cada fila de la tabla.

## 13. Contratos de API (resumen)

| Método | Ruta | Rol requerido | Descripción |
|---|---|---|---|
| POST | `/api/auth/login` | — | Autenticación, devuelve JWT |
| GET | `/api/sucursales` | COMPRADOR, ADMIN, GERENTE_SUCURSAL | Listar sucursales |
| POST/PUT | `/api/sucursales`, `/api/sucursales/{id}` | ADMIN | Crear y editar sucursales (no se borran: se desactivan) |
| GET/POST/PUT | `/api/proveedores` | ADMIN | CRUD proveedores |
| PATCH | `/api/proveedores/{id}/regenerar-clave-webhook` | ADMIN | Regenerar la clave de webhook (invalida la anterior) |
| GET/POST/PUT | `/api/productos` | ADMIN | CRUD productos |
| GET/POST/PUT | `/api/clientes` | ADMIN | CRUD clientes |
| GET/POST/PUT | `/api/usuarios` | ADMIN | CRUD usuarios |
| GET | `/api/ordenes` | COMPRADOR, ADMIN, GERENTE_SUCURSAL | Listar órdenes (con filtros: estado, proveedor, sucursal, fecha). `GERENTE_SUCURSAL` ve solo las de su sucursal |
| POST | `/api/ordenes` | COMPRADOR, ADMIN | Crear orden (estado inicial `CREADA`) |
| GET | `/api/ordenes/{id}` | COMPRADOR, ADMIN, GERENTE_SUCURSAL | Detalle de orden. `GERENTE_SUCURSAL` solo si es de su sucursal (403 si no) |
| PUT | `/api/ordenes/{id}` | COMPRADOR, ADMIN | Modificar orden (solo si `CREADA`) |
| PATCH | `/api/ordenes/{id}/aprobar` | COMPRADOR, ADMIN | Transición a `APROBADA` + disparo de correo |
| PATCH | `/api/ordenes/{id}/anular` | COMPRADOR, ADMIN | Transición a `ANULADA` (requiere `motivo`) + disparo de correo |
| PATCH | `/api/ordenes/{id}/cerrar` | GERENTE_SUCURSAL (de la sucursal destino), ADMIN | Transición a `CERRADA`, requiere `conforme` (bool) y `observacion` opcional |
| GET | `/api/ordenes/{id}/linea-tiempo` | COMPRADOR, ADMIN, GERENTE_SUCURSAL | Historial combinado (auditoría interna + eventos de proveedor), ordenado por fecha. `GERENTE_SUCURSAL` solo si la orden es de su sucursal |
| GET | `/api/auditoria/ordenes/{numeroOrden}` | ADMIN | Historial de estados de una orden por su `numero_orden` (solo auditoría interna) |
| POST | `/api/webhooks/eventos` | API key (`X-Api-Key`) | Registrar un evento logístico reportado por el proveedor |
| GET | `/api/ordenes/importaciones/plantilla` | COMPRADOR, ADMIN | Descargar plantilla Excel para carga masiva de órdenes |
| POST | `/api/ordenes/importaciones` | COMPRADOR, ADMIN | Carga masiva de órdenes vía Excel (ver sección 8) |
| GET | `/api/ordenes/{id}/exportar` | COMPRADOR, ADMIN, GERENTE_SUCURSAL | Exportar una orden como PDF (ver sección 9) |
| GET | `/api/ordenes/exportar` | COMPRADOR, ADMIN, GERENTE_SUCURSAL | Exportar un reporte Excel de órdenes con filtros (ver sección 9) |

Todos los endpoints de escritura deben validar entrada (campos obligatorios, formatos) tanto en frontend como en backend, y devolver errores estructurados según el objeto de error de la sección 12.

## 14. Sistema de diseño y UI

Se define explícitamente para evitar que la UI resulte inconsistente entre pantallas o "responsive" solo de nombre. Aplica a toda la aplicación (Compras, Sucursal, Administración).

**Librería base:** shadcn/ui sobre Tailwind CSS. Se eligió sobre Tailwind "a mano" porque genera el código de cada componente directamente en el proyecto (no es una caja negra), lo que permite mantener consistencia entre pantallas sin reinventar inputs, selects, diálogos y tablas en cada formulario, y da accesibilidad y responsividad razonables por defecto.

**Modo de color:** modo claro como predeterminado y único requerido para esta demo. Modo oscuro queda como extra opcional (shadcn/ui ya expone variables CSS semánticas — `background`, `foreground`, `primary`, etc. — pensadas para alternarse con la clase `dark:` de Tailwind, por lo que agregar un toggle de tema más adelante tiene bajo costo si el tiempo alcanza).

**Paleta (modo claro):**

| Uso | Color | Hex |
|---|---|---|
| Acento primario (marca, botones principales) | Azul marino | `#194F90` |
| Fondo suave sobre acento (hover, ítem activo del menú) | Azul muy claro | `#EFF6FF` |
| Anillo de foco | Azul claro | `#93C5FD` |
| Texto sobre fondo suave | Azul marino | `#194F90` |
| Texto primario | Casi negro cálido | `#292524` |
| Texto secundario | Gris cálido | `#78716C` |
| Bordes / separadores | Gris muy claro | `#E7E5E4` |
| Fondo de página | Gris muy claro | `#F5F5F4` |
| Superficie de tarjeta | Blanco | `#FFFFFF` |
| Éxito | Verde | `#16A34A` |
| Error / peligro (anular, rechazado) | Rojo | `#DC2626` |
| Informativo (avisos, toasts) | Celeste | `#0369A1` (fondo `#F0F9FF`, borde `#BAE6FD`) |
| Advertencia | Ámbar | `#92400E` (fondo `#FFFBEB`, borde `#FCD34D`) |

El acento es un azul marino (`#194F90`), sobrio y de buen contraste (texto blanco sobre él: 8.2:1). El naranja que tuvo la primera versión se abandonó porque en los mensajes se leía como alarma o error. Regla de diseño: el color de la marca no se usa para mensajes; los avisos y toasts usan el color de su significado (celeste informa, verde confirma, ámbar advierte, rojo es solo para errores). Los grises son cálidos (base "stone" de Tailwind), un fondo neutro que combina con el azul marino sin volverlo frío.

**Tipografía:** Inter como familia principal, con `system-ui` como fallback. Escala: 12px (etiquetas/metadatos) / 13px (texto de tabla y botones secundarios) / 15px (texto de cuerpo/encabezados de tarjeta) / 20px (totales y cifras destacadas). Dos pesos únicamente: 400 (regular) y 500 (medium) — evitar negritas fuertes (600/700), que se ven pesadas en una UI de este tipo.

**Espaciado y bordes:** escala de espaciado por defecto de Tailwind (4px base). Radio de borde de 6-8px en controles e inputs, 12px en tarjetas. Bordes de 1px, sin sombras decorativas (solo anillos de foco funcionales en inputs).

**Comportamiento responsive por pantalla (mínimo exigido):**

| Pantalla | Debajo de `md` (~768px) | En `md` y superior |
|---|---|---|
| Listado de órdenes | Tarjetas apiladas, una por orden | Tabla con columnas (número, proveedor, sucursal, estado, total, fecha) |
| Formulario de orden | Una sola columna, secciones apiladas verticalmente | Dos columnas: datos de la orden a la izquierda, detalle de productos + total a la derecha |
| Navegación principal | Menú hamburguesa colapsable | Barra lateral o superior fija, visible siempre |
| Línea de tiempo de la orden | Igual en ambos casos (ya es vertical por diseño) | Igual |
| Dashboard/listados de administración (catálogos) | Tarjetas apiladas con acciones en menú de tres puntos | Tabla con acciones inline (editar/activar-desactivar) |

**Iconografía:** un set de iconos outline consistente (ej. Lucide, que integra directamente con shadcn/ui) en vez de mezclar íconos de distintas librerías o emojis.

**Referencia visual (mockup ilustrativo del formulario de creación de orden, formato Ferretería de Construcción):**

- Encabezado con acento naranja e ícono de la sección.
- Selección de proveedor y sucursal destino.
- Aviso contextual cuando la validación cruzada formato↔categoría limita los productos disponibles.
- Detalle de líneas de producto y total calculado en vivo.
- Acciones de aprobar/anular, con el color de "anular" en tono neutro (no rojo agresivo) ya que sigue requiriendo motivo y no es una acción destructiva irreversible a nivel de datos.

## 15. Frontend

**Stack:** React + Vite + Tailwind CSS + shadcn/ui (sección 14).

### 15.1 Arquitectura: ruteo, estado y componentes

Se deja explícito para no dejarlo al criterio libre de la implementación — el riesgo real de una demo con buen backend y frontend improvisado está aquí, no en la apariencia visual.

**Ruteo — React Router v7.** Rutas protegidas por rol vía un componente `<ProtectedRoute rol={[...]}>` que envuelve las rutas privadas: redirige a `/login` sin sesión, o a una vista de "no autorizado" si el rol no coincide.

```
/login
/ordenes                      (listado — COMPRADOR, ADMIN, GERENTE_SUCURSAL)
/ordenes/nueva                 (COMPRADOR, ADMIN)
/ordenes/:id                   (detalle + línea de tiempo — todos los roles internos)
/ordenes/:id/editar             (COMPRADOR, ADMIN — solo si CREADA)
/ordenes/importar               (COMPRADOR, ADMIN)
/administracion/productos
/administracion/proveedores
/administracion/sucursales
/administracion/clientes
/administracion/usuarios
/administracion/auditoria
```

**Manejo de estado — dos categorías, sin mezclarlas:**
- **Estado de servidor** (órdenes, catálogos, línea de tiempo): **TanStack Query**. Resuelve cache, invalidación tras mutaciones (ej. al aprobar una orden se invalida automáticamente su detalle y el listado) y estados de carga/error sin `useEffect`/`useState` manual repetido en cada pantalla.
- **Estado de sesión** (usuario, rol, token): un **Context** simple (`AuthContext`) — no se justifica una librería de estado global (Redux/Zustand) para algo que cambia tan poco.
- **Estado de formulario:** **React Hook Form + Zod**. El schema Zod se mantiene alineado con las anotaciones Jakarta Bean Validation del backend (mismos campos obligatorios, mismos formatos), así ambas capas validan el mismo contrato con el mismo vocabulario.

**Jerarquía de componentes (carpetas):**
```
frontend/src/
├── app/               (routing, providers: QueryClient, AuthContext)
├── layouts/           (AppLayout con nav según rol, AuthLayout para /login)
├── pages/             (una carpeta por ruta: OrdenesListPage, OrdenDetallePage...)
├── features/          (lógica por dominio: ordenes/, catalogos/, auth/ — cada una con sus hooks de TanStack Query, ej. useOrdenes(), useAprobarOrden())
├── components/ui/     (componentes shadcn/ui generados)
└── components/shared/ (EstadoBadge, LineaTiempo, ErrorMessage — reutilizados entre pantallas)
```
La separación `features/` (lógica) vs. `pages/` (composición) vs. `components/` (presentación) evita que las llamadas a la API terminen dispersas dentro de componentes de presentación — un componente de botón no debe saber cómo se aprueba una orden, solo disparar el hook que lo hace.

**Nombres de componentes compartidos a mantener consistentes en toda la app** (no reinventar uno distinto por pantalla): `EstadoBadge` (pill de color por estado de orden o resultado de importación), `OrdenesTable`, `LineaTiempo`, `ErrorMessage` (renderiza el objeto de error tipado de la sección 12).

### 15.2 Mockups de referencia

Se diseñaron cuatro pantallas como referencia visual — no un catálogo exhaustivo de cada pantalla y breakpoint, sino las de mayor densidad de datos o de lógica condicional, donde un mockup aporta más que la descripción escrita:

1. **Listado de órdenes** — filtros, tabla con `EstadoBadge` por color, acciones de exportar/importar/nueva orden.
2. **Detalle de orden** — datos generales + detalle de productos, línea de tiempo combinada (auditoría interna + eventos de proveedor), botón "Cerrar orden" resaltado cuando hay un evento `ENTREGADA`.
3. **Nueva orden** — caso Venta Directa con cliente obligatorio, aviso de filtro de categoría por formato de sucursal, detalle de líneas con total en vivo.
4. **Importar órdenes** — carga de Excel, plantilla descargable, tabla de resultado distinguiendo lotes creados de rechazados con su motivo.

Canvas: https://claude.ai/artifact/FeYGtj9i6NFucgqvFYzSA5

El resto de pantallas (Administración, vistas específicas por rol) se construyen a partir de las reglas escritas en este documento y de los mismos componentes compartidos ya establecidos en estos cuatro mockups, sin necesitar un mockup propio — mockear cada pantalla y cada breakpoint tiene retorno decreciente frente a mantener las reglas y los componentes consistentes.

### 15.3 Estructura funcional por sección

**Estructura funcional:**
- **Login** (obtiene y almacena JWT).
- **Sección Compras** (rol `COMPRADOR`/`ADMIN`):
  - Listado de órdenes con filtro por estado/sucursal/proveedor.
  - Formulario de creación/edición de orden: selección de proveedor, sucursal destino, fecha necesaria, cliente (condicional a Venta Directa), detalle de productos con validación de categoría en tiempo real contra el formato de sucursal seleccionado, total calculado en vivo.
  - Acciones de cambio de estado (aprobar/anular con motivo/cerrar), deshabilitadas según el estado actual de la orden. El botón "Cerrar orden" se resalta visualmente cuando existe un evento `ENTREGADA` en la línea de tiempo, sin activarse automáticamente.
  - **Línea de tiempo de la orden:** componente vertical en el detalle de la orden que combina, ordenados por fecha, los cambios de estado internos (creada, aprobada, anulada, cerrada) y los eventos reportados por el proveedor (aceptada, rechazada, preparada, despachada, entregada), consumiendo `GET /api/ordenes/{id}/linea-tiempo`.
  - **Importar órdenes:** pantalla con descarga de plantilla Excel, carga de archivo, y tabla de resultado distinguiendo órdenes creadas de rechazadas con su motivo (ver sección 8).
  - **Exportar:** botón "Exportar PDF" en el detalle de la orden; botón "Exportar Excel" en el listado, aplicando los filtros activos (ver sección 9).
- **Sección Sucursal** (rol `GERENTE_SUCURSAL`):
  - Listado de solo las órdenes en estado `APROBADA` de su propia sucursal (filtrado automático por `usuario.sucursal_id`, sin selector de sucursal en la UI).
  - Detalle de orden en modo solo lectura salvo por la acción "Confirmar recepción", que abre un formulario simple: checkbox/toggle `conforme` + campo de observación opcional, y ejecuta `PATCH /api/ordenes/{id}/cerrar`.
  - Misma línea de tiempo combinada que en la sección Compras, para que el gerente vea el historial de eventos del proveedor antes de confirmar.
- **Sección Administración** (rol `ADMIN` únicamente, rutas protegidas):
  - CRUD de Productos, Proveedores, Sucursales, Clientes, Usuarios.
  - Consulta de bitácora de auditoría por orden.
- **Validaciones de entrada:** formato de fecha, formato de email, campos obligatorios — tanto a nivel de formulario (feedback inmediato) como respaldadas por el backend.
- **Tour guiado:** implementado con `react-joyride`, disponible a demanda (botón con el ícono «?» a la derecha del título, siempre en ese lugar y separado de los botones de acción, que van en su propia fila debajo del título, a la derecha en pantallas anchas y a la izquierda en móvil; nunca arranca solo, porque las pantallas son intuitivas y la ayuda se ofrece, no se impone) en cinco pantallas: nueva orden (proveedor y sucursal, productos permitidos, líneas, total, acciones), editar orden (datos, líneas con sus precios conservados, agregar o quitar, total, guardar), lista de órdenes (filtros, tabla, exportar, nueva orden/importar), importar órdenes (plantilla, archivo, procesar por lotes) y detalle de una orden (estado, acciones, datos, productos, línea de tiempo). Los pasos se adaptan al rol: el gerente de sucursal, que no ve filtros ni crea órdenes, recorre solo lo que le corresponde. Una prueba por pantalla y rol verifica que cada paso apunte a un elemento existente.

## 16. Stack tecnológico y despliegue

- **Backend:** Java 25 LTS + Spring Boot 4.1.x, Spring Security (JWT propio, sin OAuth2), Spring Data JPA, Spring Mail, Flyway, Lombok (solo en entidades), MapStruct (mapeo DTO↔entity, ver sección 11).
- **Frontend:** React + Vite + Tailwind CSS + shadcn/ui (ver sección 14, Sistema de diseño y UI).
- **Base de datos:** PostgreSQL.
- **Migraciones:** Flyway (`src/main/resources/db/migration`, scripts `V1__init_schema.sql`, `V2__seed_data.sql`, etc.).
- **Orquestación:** Docker Compose (sin Kubernetes para esta demo).
- **Correo (demo):** Mailpit.

### `docker-compose.yml` (estructura esperada)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ordenes_compra
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d ordenes_compra"]
      interval: 5s
      timeout: 5s
      retries: 10

  mailpit:
    image: axllent/mailpit
    ports:
      - "1025:1025"
      - "8025:8025"

  backend:
    build: ./backend
    depends_on:
      postgres:
        condition: service_healthy
      mailpit:
        condition: service_started
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ordenes_compra
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: app
      SPRING_MAIL_HOST: mailpit
      SPRING_MAIL_PORT: 1025
      JWT_SECRET: <secreto>
    ports:
      - "8080:8080"

  frontend:
    build: ./frontend
    depends_on:
      - backend
    ports:
      - "5173:80"

volumes:
  pgdata:
```

> **Nota:** `depends_on` sin condición solo espera a que el contenedor arranque, no a que Postgres esté listo para aceptar conexiones — el `healthcheck` + `condition: service_healthy` evita que el backend intente correr las migraciones de Flyway contra una base de datos que todavía no está lista, que es la causa más común de que `docker compose up` falle en el primer intento.

### Estructura del repositorio

```
proyecto/
├── backend/          (Spring Boot, Dockerfile propio)
├── frontend/          (React + Vite + Tailwind, Dockerfile propio)
├── docker-compose.yml
└── README.md
```

## 17. Datos semilla mínimos sugeridos (Flyway seed)

- 3 sucursales de ejemplo, una por formato.
- Categorías: `HERRAMIENTAS`, `PINTURAS`, `HOGAR`, `JARDIN`, `ACABADOS`, `MATERIALES_PESADOS`, `CONSTRUCCION`.
- Mapeo `formato_categoria_permitida`: Ferretería → Herramientas/Pinturas/Hogar/Jardín/Acabados; Ferretería de Construcción → Materiales Pesados/Construcción; Venta Directa → todas.
- 2-3 proveedores, cada uno con `webhook_api_key` de ejemplo generada, 4-6 productos distribuidos entre categorías, 1-2 clientes, 1 usuario `ADMIN`, 1 `COMPRADOR`, y 1 `GERENTE_SUCURSAL` por cada sucursal semilla (con su `sucursal_id` correspondiente).

## 18. Criterios de aceptación

- Se puede crear una orden, agregar líneas de detalle, y el total se calcula correctamente.
- Agregar un producto de categoría no permitida para el formato de la sucursal destino es rechazado con mensaje claro.
- Seleccionar una sucursal de formato Venta Directa hace obligatorio el campo Cliente; en los otros formatos el campo no aplica.
- El flujo de estados respeta la tabla de la sección 4; intentar una transición inválida devuelve 409.
- Aprobar una orden, o anular una orden ya `APROBADA`, genera un correo visible en Mailpit dirigido al proveedor, con el PDF de la orden adjunto. Anular una orden que seguía en `CREADA` no genera ningún correo.
- Anular una orden en estado `CERRADA` es rechazado.
- Los endpoints de administración son inaccesibles para un usuario con rol `COMPRADOR`.
- Un usuario `GERENTE_SUCURSAL` solo ve en el listado y puede abrir el detalle de órdenes de su propia sucursal; intentar acceder a una orden de otra sucursal responde `403`.
- Un usuario `GERENTE_SUCURSAL` puede ejecutar `cerrar` (con `conforme`/`observacion`) sobre una orden `APROBADA` de su sucursal, pero recibe `403` si intenta `aprobar` o `anular`.
- Un `COMPRADOR` recibe `403` si intenta ejecutar `cerrar` directamente (la acción es exclusiva de `GERENTE_SUCURSAL`/`ADMIN`).
- Un `POST` al webhook con un `X-Api-Key` inválido o inexistente es rechazado (401/403); con una key válida, el evento queda registrado y visible en la línea de tiempo de la orden correspondiente.
- Un evento `ENTREGADA` o `RECHAZADA` recibido vía webhook **no** cambia el estado interno de la orden; el estado solo cambia por acción explícita de un usuario interno.
- La línea de tiempo de la orden muestra, ordenados cronológicamente, tanto los cambios de estado internos como los eventos reportados por el proveedor.
- El tour guiado se activa en el flujo de creación de orden y puede saltarse.
- Enviar dos veces el mismo `POST /api/webhooks/eventos` con el mismo `Idempotency-Key` no genera un evento duplicado en la línea de tiempo.
- `GET /api/ordenes` soporta paginación por cursor (`limit`/`starting_after`) y responde con `data`/`has_more`.
- Un error de negocio (ej. categoría no permitida, transición de estado inválida) responde con el objeto de error tipado (`type`/`code`/`message`/`param`) descrito en la sección 12.
- El listado de órdenes y el formulario de creación se adaptan según la tabla de la sección 14 (tarjetas apiladas/una columna debajo de `md`, tabla/dos columnas en `md` y superior), verificable achicando la ventana del navegador.
- Los componentes de formulario (inputs, selects, botones) usan consistentemente shadcn/ui, sin mezclar estilos ad hoc entre pantallas.
- Las rutas privadas redirigen a `/login` sin sesión, y a una vista de no autorizado si el rol del usuario no tiene acceso a esa ruta (sección 15.1).
- Un mismo componente (`EstadoBadge`, `LineaTiempo`) se reutiliza entre las pantallas que lo necesitan, en vez de reimplementarse por separado en cada una.
- `docker-compose up` levanta los 4 servicios y la aplicación es funcional de punta a punta sin pasos manuales adicionales.
- Cargar un Excel con dos lotes, uno válido y otro con una regla de negocio incumplida (ej. categoría no permitida), crea la orden válida y rechaza solo la otra, reportando ambos resultados en la respuesta.
- Las órdenes creadas vía carga masiva quedan sujetas exactamente a las mismas reglas de negocio (sección 5) que las creadas por el formulario individual — verificable comparando el resultado de crear la misma orden por ambos caminos.
- Exportar una orden como PDF genera un documento con la cabecera y el detalle completo de la orden, consistente con la paleta de la sección 14.
- Exportar el reporte Excel incluye una fila por línea de producto (no una por orden), con los datos de cabecera repetidos en cada fila.

## 19. Estrategias de pruebas: backend y frontend

### 19.1 Backend

**Principio rector: probar reglas de negocio, no el framework.** Cada prueba debe trazarse a una regla de negocio (sección 5) o a un criterio de aceptación (sección 18) concreto — si una prueba no protege contra una regresión real, no se escribe. Se exige explícitamente **evitar sobre-cobertura**: no probar getters/setters, no probar el wiring de Spring, no probar repositorios Spring Data JPA sin lógica custom, no probar controladores de catálogos CRUD simples sin reglas de negocio asociadas, y no duplicar la misma regla en dos capas distintas (ej. si ya se prueba en el unit test del servicio, no repetirla en el integration test salvo que se quiera confirmar la propagación del código de estado HTTP).

**Dos niveles, no más:**

**1. Unit tests puros** (JUnit 5 + Mockito, sin contexto de Spring) — para lógica de negocio aislable sin tocar base de datos: el State Pattern y las validaciones de servicio.

**2. Integration tests con Testcontainers** — para todo lo que involucra persistencia real o la cadena completa HTTP → seguridad → servicio → base de datos. **No se usan stubs ni bases de datos en memoria (H2) como sustituto de Postgres**: se levanta un contenedor de PostgreSQL real, Flyway corre las migraciones reales contra él, y se valida el comportamiento end-to-end contra el motor real (evita divergencias de dialecto SQL entre el motor de prueba y el de producción).

```java
@Testcontainers
@SpringBootTest
class OrdenCompraIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // Spring Boot 4.1.x conecta el datasource automáticamente
    // vía @ServiceConnection, sin @DynamicPropertySource manual.
}
```

### Conjunto mínimo suficiente (alcance cerrado — no ampliar sin justificación)

**Unit tests (State Pattern y validaciones de servicio):**
- Cada transición válida de la máquina de estados: `CREADA → APROBADA`, `APROBADA → CERRADA`, `CREADA → ANULADA`, `APROBADA → ANULADA`.
- Cada transición inválida rechazada: `CERRADA → ANULADA`, `ANULADA → *`, `CREADA → CERRADA`.
- Cálculo del total de la orden a partir de las líneas de detalle.
- Rechazo de un producto cuya categoría no está permitida para el formato de la sucursal destino.
- Exigencia de `cliente_id` cuando la sucursal destino es `VENTA_DIRECTA`.
- Publicación del evento de dominio correcto al aprobar/anular, verificada con Mockito sobre `ApplicationEventPublisher` — sin enviar correo real ni depender de Mailpit en este nivel.

**Integration tests con Testcontainers (flujo completo HTTP + Postgres real):**
- Crear una orden completa y verificar persistencia + total calculado.
- Aprobar una orden y verificar el cambio de estado + registro correspondiente en `orden_compra_auditoria`.
- Intentar anular una orden en estado `CERRADA` → `409 Conflict`.
- `POST` al webhook con `X-Api-Key` inválida o inexistente → `401`/`403`; con key válida → evento registrado en `orden_evento_proveedor`.
- Enviar el mismo `Idempotency-Key` dos veces al webhook → el evento no se duplica.
- Un `GERENTE_SUCURSAL` intentando ver o cerrar una orden de otra sucursal → `403 Forbidden`.
- Un `COMPRADOR` intentando ejecutar `cerrar` → `403 Forbidden`.
- Un `COMPRADOR` accediendo a un endpoint de administración → `403 Forbidden`.
- Cargar un Excel con un lote válido y otro con una regla de negocio incumplida → crea solo el lote válido y reporta el rechazo del otro (atomicidad por orden, no por archivo).

Esta lista es la referencia de alcance de pruebas para la implementación: cualquier prueba adicional debe justificarse contra una regla de negocio o criterio de aceptación no cubierto por la lista anterior, no agregarse por defecto.

### 19.2 Frontend

Mismo principio rector que en el backend: probar lógica de negocio expresada en la UI, no la librería de componentes ni detalles internos de implementación.

**Herramientas:** **Vitest** (motor de pruebas nativo de Vite, API compatible con Jest, arranque rápido) + **React Testing Library** (prueba componentes desde la perspectiva del usuario — qué ve y qué puede hacer, no su implementación interna).

**Conjunto mínimo suficiente:**
- El campo **Cliente** se vuelve obligatorio (aparece y es requerido) cuando se selecciona una sucursal de formato `VENTA_DIRECTA`, y desaparece/no se exige en los otros formatos.
- El selector de productos **filtra** por la categoría permitida según el formato de la sucursal seleccionada (reflejo en frontend de la validación cruzada del backend).
- El **total se recalcula** correctamente en la UI al agregar/quitar/modificar una línea de detalle.
- Las **validaciones de formulario** (fecha, email, campos vacíos) muestran el mensaje de error esperado y no dejan enviar el formulario mientras estén incumplidas.
- Los **botones de acción** (aprobar/anular/cerrar) están habilitados/deshabilitados según el estado de la orden y el rol del usuario autenticado (ej. un `GERENTE_SUCURSAL` no ve el botón de "aprobar").

**Qué NO probar:**
- Snapshots genéricos de componentes (se rompen con cualquier cambio de estilo y no protegen ninguna regla real).
- Componentes de shadcn/ui en sí mismos — ya están probados por sus propios mantenedores.
- El tour guiado (`react-joyride`) — es una capa de UX, no de negocio; suficiente con verificarlo manualmente.

**Fuera de alcance para esta demo:** pruebas end-to-end con Playwright/Cypress (navegador real + backend real). Aportan valor en un producto real, pero para el tiempo disponible, Vitest + Testing Library sobre los puntos anteriores ya demuestra el criterio sin inflar el esfuerzo.

## 20. Convenciones de documentación en el código

Este documento es un insumo para generar la implementación, no una referencia que deba sobrevivir dentro del código. El código debe ser autocontenido y explicarse a sí mismo, sin asumir que quien lo lea después tiene este documento a la mano.

**No hacer:**
```java
// Según la sección 5.3 de la especificación, no se puede anular una orden CERRADA
if (estado == Estado.CERRADA) throw new EstadoInvalidoException(...);
```
```java
/**
 * Implementa el punto 7 del documento de especificación (webhooks).
 */
```
Este tipo de comentario no envejece bien: el documento no viaja con el código, puede quedar desactualizado sin que nadie lo note, y no le sirve de nada a quien mantenga el proyecto sin ese contexto externo.

**Sí hacer — documentar el código en sus propios términos, explicando el *por qué* de la regla, no citando su origen:**
```java
// Una orden CERRADA implica que la mercadería ya ingresó físicamente a la sucursal;
// anularla dejaría el inventario en un estado inconsistente con la orden.
if (estado == Estado.CERRADA) throw new EstadoInvalidoException(...);
```
```java
/**
 * Registra eventos logísticos que el proveedor reporta de forma asíncrona
 * (aceptación, despacho, entrega, etc.). Los eventos son puramente informativos:
 * ninguno dispara una transición de estado interna de la orden por sí mismo.
 */
```

**Guía general para Claude Code:**
- Javadoc en clases y métodos públicos que documenten comportamiento no trivial (qué hace, qué excepciones lanza y por qué, qué invariantes asume) — no Javadoc genérico que solo repite el nombre del método.
- Comentarios inline solo donde la razón de una decisión no sea obvia leyendo el código (ej. por qué un campo se congela en vez de referenciar el catálogo en vivo), nunca para narrar lo que el código evidentemente ya dice.
- Nombres de clases, métodos y variables que reflejen el lenguaje del dominio (`EstadoOrden`, `validarCategoriaPermitida`, `precioCongelado`) para que la intención de negocio quede en el código mismo, no solo en comentarios.
- Cero menciones a "la especificación", "el documento", números de sección, o a Claude Code mismo, en comentarios, Javadoc, nombres de commit o README generado.

## 21. Orden de implementación sugerido

Pensado para minimizar retrabajo: cada fase se apoya en piezas ya construidas en la anterior, y las pruebas (sección 19) se escriben junto con cada módulo, no como una fase separada al final.

**Fase 1 — Infraestructura base.** `docker-compose.yml` (Postgres, Mailpit), scripts Flyway con el esquema completo (sección 3, incluyendo las restricciones `CHECK` de la sección 3.13) y datos semilla (sección 17). Al final de esta fase, la base de datos existe, es consultable, y rechaza por sí misma los datos inválidos que cubre 3.13 — aunque no haya una sola línea de backend.

**Fase 2 — Capa de dominio.** Entidades JPA (Lombok selectivo), DTOs como Records, mappers MapStruct (sección 11). Sin lógica de negocio todavía, solo el modelo persistente y su representación de API.

**Fase 3 — Seguridad.** Login, emisión/validación de JWT, filtro de autenticación, roles (`ADMIN`, `COMPRADOR`, `GERENTE_SUCURSAL`). Se hace temprano porque casi todo lo demás depende de tener un usuario autenticado en contexto (incluyendo `creado_por_id`/`modificado_por_id`, sección 5).

**Fase 4 — Núcleo de Compras: máquina de estados y reglas de negocio.** State Pattern (sección 4), validación cruzada formato↔categoría, cliente obligatorio en Venta Directa, cálculo de totales, CRUD de orden y sus transiciones (`aprobar`/`anular`/`cerrar` con autorización por rol y por sucursal). Es el corazón funcional del sistema — se construye antes que cualquier catálogo secundario.

**Fase 5 — Convenciones de API transversales.** Formato de respuesta y error consistentes, paginación por cursor, idempotencia, expansión de objetos (sección 12) — se aplican sobre los endpoints ya existentes de la Fase 4 antes de seguir agregando endpoints nuevos, para no tener que retrofit-earlos después en todos lados.

**Fase 6 — Notificaciones por correo.** Eventos de dominio (`OrdenAprobadaEvent`/`OrdenAnuladaEvent`), `EmailService`, integración con Mailpit (sección 6). Depende de que la máquina de estados de la Fase 4 ya emita las transiciones correctas.

**Fase 7 — Webhook de proveedores.** Autenticación por API key, endpoint de eventos, idempotencia aplicada a este endpoint, línea de tiempo combinada (sección 7). Depende de que `orden_compra` y su auditoría (Fase 4) ya existan.

**Fase 8 — Módulo de Administración.** CRUD de catálogos (producto, proveedor, cliente, usuario) y consulta de auditoría (sección 2, punto 10). Se deja para después del núcleo de Compras porque es funcionalmente más simple (CRUD sin máquina de estados) y no bloquea nada de lo anterior.

**Fase 9 — Registro masivo e importación de órdenes.** Endpoints de plantilla y carga de Excel (sección 8), reutilizando el servicio de creación de orden de la Fase 4. Se deja para después de que el núcleo de Compras esté estable, ya que depende directamente de él y no aporta valor si la validación individual todavía está cambiando.

**Fase 10 — Exportación de órdenes.** PDF individual (`openhtmltopdf`) y reporte Excel masivo (sección 9). Depende de tener ya definida la paleta/tipografía (sección 14) para el PDF, y los filtros del listado (Fase 4) para el Excel.

**Fase 11 — Frontend.** Setup de ruteo, providers (TanStack Query, AuthContext) y jerarquía de carpetas (sección 15.1) primero, antes de cualquier pantalla — evita retrofit de la arquitectura una vez que ya hay componentes escritos. Luego login, listado y formulario de órdenes con validaciones (guiándose por los mockups de la sección 15.2 para las pantallas que cubren), línea de tiempo, importación masiva, exportación, sección Sucursal, sección Administración — en ese orden, siguiendo la misma prioridad que el backend. El tour guiado (`react-joyride`) se implementa al final de esta fase, ya que depende de que las pantallas que resalta ya existan y sean estables.

**Puerta de verificación de la Fase 11:** una pantalla no se da por terminada con que el componente renderice o pase su test de Vitest — se da por terminada cuando se ejercitó contra el backend real corriendo (login real, rol real, datos reales), no contra supuestos sobre lo que el contrato debería permitir. Cualquier discrepancia entre lo que la pantalla necesita y lo que `openapi.yaml`/el backend realmente ofrecen (un campo que falta, un endpoint con otro rol del esperado, una forma de respuesta distinta) se corrige de inmediato al detectarla, no se anota como pendiente para después. Esto es exactamente lo que faltó en las Fases 1-10: al ser todas de backend, ningún consumidor real ejercitó la API hasta ahora, y varias brechas (campo `activo` ausente, falta de forma de generar `webhook_api_key`, uso inválido de `scopes` en `openapi.yaml`) quedaron sin detectar durante varias fases seguidas.

**Fase 12 — Revisión final.** Verificación de los criterios de aceptación (sección 18) de punta a punta y revisión de que el código no contenga las referencias que describe la sección 20.

**Puerta de verificación de la Fase 12:** además de los criterios de aceptación, se confirma con herramientas automáticas — no solo con revisión manual o con que la suite de pruebas pase — que cada artefacto cumple lo que declara: `openapi.yaml` pasa un linter de OpenAPI (ej. Spectral) sin advertencias; las migraciones Flyway aplican limpio contra una base de datos completamente vacía, no solo contra la base de datos de desarrollo ya migrada una vez; y el build de frontend no tiene errores de tipos contra los contratos de datos reales del backend. Este paso faltó entre las Fases 1 y 10 y permitió que defectos como una autoconfiguración de Flyway nunca activa, o el uso inválido del campo `scopes` en `openapi.yaml`, quedaran sin detectar durante varias fases seguidas — en ambos casos, la suite de pruebas de negocio (sección 19) pasaba igual, porque nunca estaba diseñada para detectar ese tipo de defecto.
