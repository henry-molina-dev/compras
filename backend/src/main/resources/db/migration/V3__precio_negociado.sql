-- Precio negociado: cada proveedor define cuanto se puede negociar sobre el precio de catalogo de
-- cualquiera de sus productos (descuento maximo y aumento maximo, en porcentaje). 0/0 = sin
-- negociacion: la linea usa el precio de catalogo.
ALTER TABLE proveedor
    ADD COLUMN descuento_maximo_pct NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN aumento_maximo_pct   NUMERIC(5, 2) NOT NULL DEFAULT 0,
    -- El descuento es estrictamente menor a 100 para que el precio negociado nunca llegue a cero.
    ADD CONSTRAINT chk_proveedor_descuento_pct CHECK (descuento_maximo_pct >= 0 AND descuento_maximo_pct < 100),
    ADD CONSTRAINT chk_proveedor_aumento_pct CHECK (aumento_maximo_pct >= 0 AND aumento_maximo_pct <= 100);

-- Cada linea recuerda el precio de catalogo vigente al crearla: es la referencia contra la que se
-- validan los limites (tambien al editar la orden) y con la que se muestra la variacion, aunque el
-- catalogo cambie despues. Las lineas existentes no tuvieron negociacion: catalogo = precio unitario.
ALTER TABLE orden_compra_detalle ADD COLUMN precio_catalogo NUMERIC(12, 2);
UPDATE orden_compra_detalle SET precio_catalogo = precio_unitario;
ALTER TABLE orden_compra_detalle
    ALTER COLUMN precio_catalogo SET NOT NULL,
    ADD CONSTRAINT chk_detalle_precio_catalogo CHECK (precio_catalogo > 0);

-- Datos semilla de la demo: dos proveedores con margen de negociacion y uno sin (0/0), para mostrar
-- ambos comportamientos.
UPDATE proveedor SET descuento_maximo_pct = 10, aumento_maximo_pct = 5 WHERE nombre = 'Ferretera del Norte S.A.';
UPDATE proveedor SET descuento_maximo_pct = 5,  aumento_maximo_pct = 0 WHERE nombre = 'Aceros y Materiales S.A.';
