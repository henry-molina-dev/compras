package com.ferrocompras.ordenescompra.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductoResponse(
    String object,
    Integer id,
    String codigo,
    String nombre,
    Integer categoriaId,
    BigDecimal precio,
    String unidadVenta,
    String unidadCompra,
    BigDecimal factorConversion,
    Boolean activo,
    Instant createdAt,
    Instant updatedAt
) {
}
