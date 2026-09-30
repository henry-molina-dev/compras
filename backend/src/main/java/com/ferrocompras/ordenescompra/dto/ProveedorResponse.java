package com.ferrocompras.ordenescompra.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProveedorResponse(
    String object,
    Integer id,
    String nombre,
    String email,
    String telefono,
    String direccion,
    // Se expone porque el alta de proveedor genera esta clave automaticamente (ver ProveedorService)
    // y el ADMIN necesita poder copiarla para entregarsela al proveedor real; no hay otro mecanismo
    // para consultarla despues del alta. Quien no es ADMIN la recibe en null (ver ProveedorController).
    String webhookApiKey,
    // Limites de negociacion (porcentaje sobre el precio de catalogo); 0/0 = no se negocia.
    BigDecimal descuentoMaximoPct,
    BigDecimal aumentoMaximoPct,
    Boolean activo,
    Instant createdAt,
    Instant updatedAt
) {
}
