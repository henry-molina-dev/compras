-- Datos semilla minimos para desarrollo y demo. Los FK se resuelven por subconsulta (no por id
-- literal) para no depender de que las secuencias arranquen en un valor especifico.

INSERT INTO sucursal (nombre, formato) VALUES
    ('Ferreteria San Salvador', 'FERRETERIA'),
    ('Ferreteria de Construccion Santa Ana', 'FERRETERIA_CONSTRUCCION'),
    ('Venta Directa', 'VENTA_DIRECTA');

-- Password de todos los usuarios semilla: Compras2026!
INSERT INTO usuario (username, password_hash, rol, sucursal_id) VALUES
    ('admin', '$2b$10$CyPhh9yPZ5QrgWvWFfQ2JOkY2IBB4r0z0878HQJpymXgwoR1wq9cq', 'ADMIN', NULL),
    ('maria.gomez', '$2b$10$CyPhh9yPZ5QrgWvWFfQ2JOkY2IBB4r0z0878HQJpymXgwoR1wq9cq', 'COMPRADOR', NULL),
    ('gerente.sansalvador', '$2b$10$CyPhh9yPZ5QrgWvWFfQ2JOkY2IBB4r0z0878HQJpymXgwoR1wq9cq', 'GERENTE_SUCURSAL',
     (SELECT id FROM sucursal WHERE formato = 'FERRETERIA')),
    ('gerente.santaana', '$2b$10$CyPhh9yPZ5QrgWvWFfQ2JOkY2IBB4r0z0878HQJpymXgwoR1wq9cq', 'GERENTE_SUCURSAL',
     (SELECT id FROM sucursal WHERE formato = 'FERRETERIA_CONSTRUCCION')),
    ('gerente.ventadirecta', '$2b$10$CyPhh9yPZ5QrgWvWFfQ2JOkY2IBB4r0z0878HQJpymXgwoR1wq9cq', 'GERENTE_SUCURSAL',
     (SELECT id FROM sucursal WHERE formato = 'VENTA_DIRECTA'));

INSERT INTO categoria_producto (nombre) VALUES
    ('HERRAMIENTAS'), ('PINTURAS'), ('HOGAR'), ('JARDIN'), ('ACABADOS'),
    ('MATERIALES_PESADOS'), ('CONSTRUCCION');

INSERT INTO formato_categoria_permitida (formato, categoria_id)
SELECT 'FERRETERIA', id FROM categoria_producto
    WHERE nombre IN ('HERRAMIENTAS', 'PINTURAS', 'HOGAR', 'JARDIN', 'ACABADOS');

INSERT INTO formato_categoria_permitida (formato, categoria_id)
SELECT 'FERRETERIA_CONSTRUCCION', id FROM categoria_producto
    WHERE nombre IN ('MATERIALES_PESADOS', 'CONSTRUCCION');

INSERT INTO formato_categoria_permitida (formato, categoria_id)
SELECT 'VENTA_DIRECTA', id FROM categoria_producto;

INSERT INTO proveedor (nombre, email, telefono, direccion, webhook_api_key) VALUES
    ('Ferretera del Norte S.A.', 'contacto@ferreteradelnorte.example.com', '+503 2234-5566',
     'Colonia Escalon, San Salvador', 'whsk_fnorte_a1c9f3e7b2d84f01a6c5e9b7d3f2a810'),
    ('Aceros y Materiales S.A.', 'ventas@acerosymateriales.example.com', '+503 2345-6677',
     'Boulevard del Ejercito, Soyapango', 'whsk_acmat_7b2e9d4a1f836c05b9e2a7d4f1c803e6'),
    ('Suministros Industriales S.A.', 'pedidos@suministrosindustriales.example.com', '+503 2456-7788',
     'Km 14.5 Carretera Panamericana, Santa Ana', 'whsk_sumind_3f8a1d6b9e2c704f5a8b1d3e6c9f2075');

INSERT INTO producto (codigo, nombre, categoria_id, precio, unidad_venta, unidad_compra, factor_conversion) VALUES
    ('CEM-42', 'Cemento gris (saco 42.5kg)',
     (SELECT id FROM categoria_producto WHERE nombre = 'MATERIALES_PESADOS'), 45.00, 'UNIDAD', 'SACO', 1),
    ('VAR-38', 'Varilla 3/8" (6m)',
     (SELECT id FROM categoria_producto WHERE nombre = 'CONSTRUCCION'), 55.42, 'UNIDAD', 'ATADO', 6),
    ('TORN-05', 'Tornillo autorroscante 2" (caja)',
     (SELECT id FROM categoria_producto WHERE nombre = 'HERRAMIENTAS'), 3.20, 'UNIDAD', 'CAJA', 100),
    ('BROC-12', 'Juego de brocas (set)',
     (SELECT id FROM categoria_producto WHERE nombre = 'HERRAMIENTAS'), 18.50, 'UNIDAD', 'SET', 1),
    ('PINT-10', 'Pintura latex blanca (galon)',
     (SELECT id FROM categoria_producto WHERE nombre = 'PINTURAS'), 22.75, 'GALON', 'CUBETA', 5),
    ('PALA-07', 'Pala punta redonda',
     (SELECT id FROM categoria_producto WHERE nombre = 'JARDIN'), 12.90, 'UNIDAD', 'UNIDAD', 1);

INSERT INTO cliente (nombre, tipo, email) VALUES
    ('Constructora Pinares S.A.', 'CONTRATISTA', 'contacto@pinaresconstructora.example.com'),
    ('Industrias del Valle S.A.', 'INDUSTRIAL', 'compras@industriasdelvalle.example.com');
