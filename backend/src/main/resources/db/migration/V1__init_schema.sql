-- Esquema completo del modulo de Ordenes de Compra a Proveedores.

CREATE TABLE usuario (
    id                 SERIAL PRIMARY KEY,
    username           VARCHAR(60) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    rol                VARCHAR(20) NOT NULL,
    sucursal_id        INTEGER,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_usuario_rol CHECK (rol IN ('ADMIN', 'COMPRADOR', 'GERENTE_SUCURSAL'))
);

CREATE TABLE sucursal (
    id                 SERIAL PRIMARY KEY,
    nombre             VARCHAR(150) NOT NULL,
    formato            VARCHAR(30) NOT NULL,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por_id      INTEGER REFERENCES usuario (id),
    modificado_por_id  INTEGER REFERENCES usuario (id),
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_sucursal_formato CHECK (formato IN ('FERRETERIA', 'FERRETERIA_CONSTRUCCION', 'VENTA_DIRECTA'))
);

-- sucursal referencia a usuario (autoria) y usuario referencia a sucursal (asignacion de un
-- GERENTE_SUCURSAL): la FK de usuario hacia sucursal se agrega recien aqui, una vez que ambas
-- tablas ya existen.
ALTER TABLE usuario
    ADD CONSTRAINT fk_usuario_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal (id);

CREATE TABLE categoria_producto (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE formato_categoria_permitida (
    id           SERIAL PRIMARY KEY,
    formato      VARCHAR(30) NOT NULL,
    categoria_id INTEGER NOT NULL REFERENCES categoria_producto (id),
    CONSTRAINT chk_fcp_formato CHECK (formato IN ('FERRETERIA', 'FERRETERIA_CONSTRUCCION', 'VENTA_DIRECTA')),
    CONSTRAINT uq_fcp_formato_categoria UNIQUE (formato, categoria_id)
);

CREATE TABLE proveedor (
    id                 SERIAL PRIMARY KEY,
    nombre             VARCHAR(150) NOT NULL,
    email              VARCHAR(150) NOT NULL,
    telefono           VARCHAR(30),
    direccion          VARCHAR(250),
    webhook_api_key    VARCHAR(100) UNIQUE,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por_id      INTEGER REFERENCES usuario (id),
    modificado_por_id  INTEGER REFERENCES usuario (id),
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE producto (
    id                 SERIAL PRIMARY KEY,
    codigo             VARCHAR(30) NOT NULL UNIQUE,
    nombre             VARCHAR(150) NOT NULL,
    categoria_id       INTEGER NOT NULL REFERENCES categoria_producto (id),
    precio             NUMERIC(12, 2) NOT NULL,
    unidad_venta       VARCHAR(20) NOT NULL,
    unidad_compra      VARCHAR(20) NOT NULL,
    factor_conversion  NUMERIC(10, 4) NOT NULL DEFAULT 1,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por_id      INTEGER REFERENCES usuario (id),
    modificado_por_id  INTEGER REFERENCES usuario (id),
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_producto_precio_positivo CHECK (precio > 0),
    CONSTRAINT chk_producto_factor_positivo CHECK (factor_conversion > 0)
);

CREATE TABLE cliente (
    id                 SERIAL PRIMARY KEY,
    nombre             VARCHAR(150) NOT NULL,
    tipo               VARCHAR(30) NOT NULL,
    email              VARCHAR(150),
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por_id      INTEGER REFERENCES usuario (id),
    modificado_por_id  INTEGER REFERENCES usuario (id),
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_cliente_tipo CHECK (tipo IN ('INDUSTRIAL', 'CONTRATISTA', 'COMERCIO'))
);

CREATE TABLE orden_compra (
    id                  SERIAL PRIMARY KEY,
    numero_orden        VARCHAR(20) NOT NULL UNIQUE,
    proveedor_id        INTEGER NOT NULL REFERENCES proveedor (id),
    sucursal_destino_id INTEGER NOT NULL REFERENCES sucursal (id),
    cliente_id          INTEGER REFERENCES cliente (id),
    usuario_id          INTEGER NOT NULL REFERENCES usuario (id),
    estado              VARCHAR(20) NOT NULL,
    fecha_necesaria     DATE NOT NULL,
    total               NUMERIC(14, 2) NOT NULL DEFAULT 0,
    motivo_anulacion    VARCHAR(250),
    conforme            BOOLEAN,
    observacion_cierre  VARCHAR(250),
    modificado_por_id   INTEGER REFERENCES usuario (id),
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_orden_estado CHECK (estado IN ('CREADA', 'APROBADA', 'ANULADA', 'CERRADA')),
    CONSTRAINT chk_orden_motivo_anulacion CHECK (estado <> 'ANULADA' OR motivo_anulacion IS NOT NULL)
);

-- Cubren los filtros expuestos por GET /api/ordenes (estado, proveedor, sucursal) y la fecha
-- usada para ordenar/filtrar el listado.
CREATE INDEX idx_orden_estado ON orden_compra (estado);
CREATE INDEX idx_orden_proveedor ON orden_compra (proveedor_id);
CREATE INDEX idx_orden_sucursal_destino ON orden_compra (sucursal_destino_id);
CREATE INDEX idx_orden_fecha_necesaria ON orden_compra (fecha_necesaria);

CREATE TABLE orden_compra_detalle (
    id                 SERIAL PRIMARY KEY,
    orden_compra_id    INTEGER NOT NULL REFERENCES orden_compra (id) ON DELETE CASCADE,
    producto_id        INTEGER NOT NULL REFERENCES producto (id),
    cantidad           NUMERIC(10, 2) NOT NULL,
    precio_unitario    NUMERIC(12, 2) NOT NULL,
    subtotal           NUMERIC(14, 2) NOT NULL,
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_detalle_cantidad_positiva CHECK (cantidad > 0)
);

CREATE INDEX idx_detalle_orden ON orden_compra_detalle (orden_compra_id);

CREATE TABLE orden_compra_auditoria (
    id              SERIAL PRIMARY KEY,
    orden_compra_id INTEGER NOT NULL REFERENCES orden_compra (id),
    estado_anterior VARCHAR(20),
    estado_nuevo    VARCHAR(20) NOT NULL,
    usuario_id      INTEGER NOT NULL REFERENCES usuario (id),
    observacion     VARCHAR(250),
    fecha           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_auditoria_orden ON orden_compra_auditoria (orden_compra_id);

CREATE TABLE orden_evento_proveedor (
    id              SERIAL PRIMARY KEY,
    orden_compra_id INTEGER NOT NULL REFERENCES orden_compra (id),
    tipo_evento     VARCHAR(20) NOT NULL,
    observacion     VARCHAR(250),
    fecha_evento    TIMESTAMP NOT NULL,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_evento_tipo CHECK (tipo_evento IN ('ACEPTADA', 'RECHAZADA', 'PREPARADA', 'DESPACHADA', 'ENTREGADA'))
);

CREATE INDEX idx_evento_orden ON orden_evento_proveedor (orden_compra_id);

CREATE TABLE idempotency_key (
    id             SERIAL PRIMARY KEY,
    clave          VARCHAR(100) NOT NULL,
    endpoint       VARCHAR(150) NOT NULL,
    response_body  TEXT,
    status_code    INTEGER,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_clave_endpoint UNIQUE (clave, endpoint)
);

-- Contador atomico del consecutivo anual de numero_orden: una fila por anio, incrementada dentro
-- de la misma transaccion de creacion de la orden via SELECT ... FOR UPDATE. Nunca se calcula a
-- partir de un COUNT(*), que no es seguro ante creaciones concurrentes.
CREATE TABLE numero_orden_contador (
    anio         INTEGER PRIMARY KEY,
    ultimo_valor INTEGER NOT NULL DEFAULT 0
);
